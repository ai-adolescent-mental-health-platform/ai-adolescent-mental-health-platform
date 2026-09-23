package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.dto.AnalysisResult;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinAnalysis;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.RiskAlert;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.UserCheckin;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.CheckinAnalysisMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.RiskAlertMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserCheckinMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.ICheckinAnalysisService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.utils.CheckinLogic;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.utils.DashScopeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * 签到 AI 分析流水线实现
 *
 * 覆盖：任务创建 + afterCommit 投递、worker 抢占执行、失败处理（risk_level 保持 NULL）、
 * 定时扫尾（PENDING 超时 / 失败重试 / 僵死）。
 */
@Slf4j
@Service
public class CheckinAnalysisServiceImpl implements ICheckinAnalysisService {

    private final CheckinAnalysisMapper checkinAnalysisMapper;
    private final UserCheckinMapper userCheckinMapper;
    private final DashScopeClient dashScopeClient;
    private final RiskAlertMapper riskAlertMapper;
    private final ThreadPoolTaskExecutor executor;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${checkin.analysis.max-per-day:3}")
    private int maxPerDay;

    @Value("${dashscope.api.chat-model:qwen3-max}")
    private String chatModel;

    @Value("${checkin.risk.keywords:}")
    private String riskKeywords;

    private static final String SYSTEM_PROMPT =
            "你是青少年心理健康风险研判助手。根据用户当天签到写下的内容，判定其情绪风险等级。\n"
            + "等级定义：\n"
            + "0-平稳：无明显风险信号；\n"
            + "1-关注：持续低落或焦虑，但未达危机；\n"
            + "2-危机：明显痛苦、建议求助，或出现自伤、轻生等信号。\n"
            + "你必须只输出一个 JSON 对象，不要输出任何其他文字或 markdown 代码围栏，格式如下：\n"
            + "{\"risk_level\":0或1或2,\"risk_reason\":\"平台侧判定理由\",\"evidence\":\"触发判定的原文摘录\","
            + "\"user_feedback\":\"面向用户的共情反馈，不得出现等级/预警字样\",\"suggestion\":\"面向用户的具体建议\"}\n"
            + "若判定为2（危机），user_feedback 必须包含求助渠道：全国心理援助热线12356、青少年服务热线12355、紧急时拨打110/120。";

    /** 等级2 固定求助渠道兜底文案（存在性校验失败时追加，见设计 5.4） */
    private static final String HELP_TEXT =
            "如果你现在很难受，或有伤害自己的念头，请马上打一个电话。对面有人，正听着：\n\n"
            + "· 全国心理援助热线 12356\n"
            + "· 青少年服务热线 12355\n"
            + "· 情况紧急时，请立刻拨打 110 / 120\n\n"
            + "你不用一个人扛着。";

    public CheckinAnalysisServiceImpl(CheckinAnalysisMapper checkinAnalysisMapper,
                                      UserCheckinMapper userCheckinMapper,
                                      DashScopeClient dashScopeClient,
                                      RiskAlertMapper riskAlertMapper,
                                      @Qualifier("checkinAnalysisExecutor") ThreadPoolTaskExecutor executor) {
        this.checkinAnalysisMapper = checkinAnalysisMapper;
        this.userCheckinMapper = userCheckinMapper;
        this.dashScopeClient = dashScopeClient;
        this.riskAlertMapper = riskAlertMapper;
        this.executor = executor;
    }

    @Override
    public void scheduleIfNeeded(Long checkinId, Long userId, String diaryContent) {
        if (diaryContent == null || diaryContent.isBlank()) {
            return;
        }
        // 成本控制：今日实际发起的分析次数（含重跑）受 max-per-day 限制
        if (!tryAcquireQuota(userId)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        // 一个签到只有一条分析，重跑是覆盖而非追加（uk_checkin_id）
        CheckinAnalysis existing = checkinAnalysisMapper.selectOne(
                new LambdaQueryWrapper<CheckinAnalysis>().eq(CheckinAnalysis::getCheckinId, checkinId)
        );
        final Long analysisId;
        if (existing != null) {
            // 重跑：状态与上一轮结论一并清空，避免新分析失败时用户读到旧反馈配新状态
            existing.setStatus(CheckinAnalysis.STATUS_PENDING);
            existing.setRetryCount(0);
            existing.setErrorMsg(null);
            existing.setRiskLevel(null);
            existing.setRiskReason(null);
            existing.setUserFeedback(null);
            existing.setSuggestion(null);
            existing.setModel(null);
            existing.setPromptTokens(null);
            existing.setCompletionTokens(null);
            existing.setStartedAt(null);
            existing.setFinishedAt(null);
            existing.setUpdateTime(now);
            checkinAnalysisMapper.updateById(existing);
            analysisId = existing.getId();
        } else {
            CheckinAnalysis analysis = new CheckinAnalysis();
            analysis.setCheckinId(checkinId);
            analysis.setUserId(userId);
            analysis.setStatus(CheckinAnalysis.STATUS_PENDING);
            analysis.setCreateTime(now);
            checkinAnalysisMapper.insert(analysis);
            analysisId = analysis.getId();
        }

        // 事务提交后才投递，否则 worker 读不到记录
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(analysisId);
            }
        });
    }

    /**
     * 占用一次当日分析额度。优先用 Redis 计数器（准确，含重跑），
     * Redis 不可用时退化为 DB 计数（按 create_time 近似）。
     */
    private boolean tryAcquireQuota(Long userId) {
        LocalDate today = LocalDate.now();
        String key = "checkin:analysis:quota:" + userId + ":" + today;
        if (stringRedisTemplate != null) {
            try {
                Long count = stringRedisTemplate.opsForValue().increment(key);
                if (count != null && count > maxPerDay) {
                    stringRedisTemplate.opsForValue().decrement(key);
                    return false;
                }
                stringRedisTemplate.expire(key, Duration.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay()));
                return true;
            } catch (Exception e) {
                log.warn("Redis 分析额度计数失败，退化为 DB 计数", e);
            }
        }
        Long count = checkinAnalysisMapper.selectCount(
                new LambdaQueryWrapper<CheckinAnalysis>()
                        .eq(CheckinAnalysis::getUserId, userId)
                        .ge(CheckinAnalysis::getCreateTime, today.atStartOfDay())
        );
        return count == null || count < maxPerDay;
    }

    @Override
    public void submit(Long analysisId) {
        try {
            executor.execute(() -> process(analysisId));
        } catch (RejectedExecutionException e) {
            // 队列满：任务在库中仍是 PENDING，由扫尾任务重新投递
            log.warn("签到分析线程池拒绝，任务 {} 待扫尾重投", analysisId);
        }
    }

    /**
     * worker 执行：抢占 → 调用 LLM → 解析 → 落库。
     */
    public void process(Long analysisId) {
        // 抢占式更新：仅当 status=0 才置为处理中，0 表示已被其他实例抢走
        int claimed = checkinAnalysisMapper.claimPending(analysisId);
        if (claimed == 0) {
            return;
        }

        CheckinAnalysis analysis = checkinAnalysisMapper.selectById(analysisId);
        if (analysis == null) {
            return;
        }
        UserCheckin checkin = userCheckinMapper.selectById(analysis.getCheckinId());
        if (checkin == null || checkin.getDiaryContent() == null || checkin.getDiaryContent().isBlank()) {
            markFailed(analysis, "签到正文为空");
            return;
        }

        String diary = checkin.getDiaryContent();

        // 关键词兜底前置：命中即落预警，不依赖模型可用性。
        // 危机场景下模型可能超时/限流/解析异常，兜底若跟在模型之后会随之一并失效。
        CheckinLogic.KeywordHit hit = CheckinLogic.scanKeyword(diary, getKeywords());
        boolean keywordCrisis = hit != null;
        if (keywordCrisis) {
            upsertAlert(checkin.getUserId(), checkin.getId(), RiskAlert.TRIGGER_KEYWORD,
                    "命中危机关键词「" + hit.keyword() + "」", hit.sentence());
        }

        try {
            String response = dashScopeClient.chat(SYSTEM_PROMPT, diary);
            ParsedAnalysis parsed = parse(response);

            Integer finalLevel = parsed.result.getRiskLevel();
            String riskReason = parsed.result.getRiskReason();
            String evidence = parsed.result.getEvidence();
            String userFeedback = parsed.result.getUserFeedback();
            String triggerType = RiskAlert.TRIGGER_MODEL;

            // 关键词命中优先级最高：无论模型判什么，等级强制 2
            if (keywordCrisis) {
                finalLevel = 2;
                triggerType = RiskAlert.TRIGGER_KEYWORD;
                evidence = hit.sentence();
                riskReason = "命中危机关键词「" + hit.keyword() + "」";
            }

            // 危机级：求助渠道存在性校验 + 预警落库（幂等覆盖，保留处置状态）
            if (finalLevel != null && finalLevel == 2) {
                userFeedback = ensureHelpChannel(userFeedback);
                upsertAlert(checkin.getUserId(), checkin.getId(), triggerType, riskReason, evidence);
            }

            analysis.setStatus(CheckinAnalysis.STATUS_SUCCESS);
            analysis.setRiskLevel(finalLevel);
            analysis.setRiskReason(riskReason);
            analysis.setUserFeedback(userFeedback);
            analysis.setSuggestion(parsed.result.getSuggestion());
            analysis.setModel(chatModel);
            analysis.setPromptTokens(parsed.promptTokens);
            analysis.setCompletionTokens(parsed.completionTokens);
            analysis.setFinishedAt(LocalDateTime.now());
            checkinAnalysisMapper.updateById(analysis);
        } catch (Exception e) {
            if (keywordCrisis) {
                // 预警已在模型调用前落库，这里只收尾分析记录：
                // 不得因模型失败把已兜底的危机降级成“未分析”
                analysis.setStatus(CheckinAnalysis.STATUS_SUCCESS);
                analysis.setRiskLevel(2);
                analysis.setRiskReason("命中危机关键词「" + hit.keyword() + "」（模型判定失败：" + truncate(e.getMessage()) + "）");
                analysis.setUserFeedback(HELP_TEXT);
                analysis.setModel("keyword-fallback");
                analysis.setFinishedAt(LocalDateTime.now());
                checkinAnalysisMapper.updateById(analysis);
                log.warn("模型调用失败但关键词兜底已生效, analysisId={}", analysisId, e);
            } else {
                log.error("签到分析失败, analysisId={}", analysisId, e);
                markFailed(analysis, e.getMessage());
            }
        }
    }

    /**
     * 定时扫尾（5 分钟）：覆盖三类丢单。
     */
    @Scheduled(fixedDelay = 300000)
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();

        // 1. PENDING 超时（进程重启 / 线程池拒绝丢单）
        List<CheckinAnalysis> pending = checkinAnalysisMapper.selectList(
                new LambdaQueryWrapper<CheckinAnalysis>()
                        .eq(CheckinAnalysis::getStatus, CheckinAnalysis.STATUS_PENDING)
                        .lt(CheckinAnalysis::getCreateTime, now.minusMinutes(2))
        );
        for (CheckinAnalysis a : pending) {
            submit(a.getId());
        }

        // 2. 失败重试（retry_count < 3）
        List<CheckinAnalysis> failed = checkinAnalysisMapper.selectList(
                new LambdaQueryWrapper<CheckinAnalysis>()
                        .eq(CheckinAnalysis::getStatus, CheckinAnalysis.STATUS_FAILED)
                        .lt(CheckinAnalysis::getRetryCount, 3)
                        .lt(CheckinAnalysis::getUpdateTime, now.minusMinutes(10))
        );
        for (CheckinAnalysis a : failed) {
            a.setRetryCount(a.getRetryCount() + 1);
            a.setStatus(CheckinAnalysis.STATUS_PENDING);
            a.setErrorMsg(null);
            a.setUpdateTime(now);
            checkinAnalysisMapper.updateById(a);
            submit(a.getId());
        }

        // 3. 僵死（处理中超过 10 分钟）→ 判失败交给重试
        List<CheckinAnalysis> stuck = checkinAnalysisMapper.selectList(
                new LambdaQueryWrapper<CheckinAnalysis>()
                        .eq(CheckinAnalysis::getStatus, CheckinAnalysis.STATUS_PROCESSING)
                        .lt(CheckinAnalysis::getStartedAt, now.minusMinutes(10))
        );
        for (CheckinAnalysis a : stuck) {
            a.setStatus(CheckinAnalysis.STATUS_FAILED);
            a.setErrorMsg("处理超时");
            a.setUpdateTime(now);
            checkinAnalysisMapper.updateById(a);
        }
    }

    private void markFailed(CheckinAnalysis analysis, String message) {
        analysis.setStatus(CheckinAnalysis.STATUS_FAILED);
        analysis.setErrorMsg(truncate(message));
        analysis.setFinishedAt(LocalDateTime.now());
        // risk_level 保持 NULL，绝不默认 0
        checkinAnalysisMapper.updateById(analysis);
    }

    /**
     * 解析 DashScope 响应。
     *
     * 响应是 OpenAI 兼容信封：{ id, choices: [{ message: { content } }], usage }
     * 业务 JSON 在 choices[0].message.content 内，token 用量在根级 usage，
     * 两者必须分别取——直接对信封根解析会拿到全空字段。
     */
    private ParsedAnalysis parse(String response) {
        JSONObject envelope = JSON.parseObject(response.trim());

        JSONArray choices = envelope.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("DashScope 响应缺少 choices");
        }
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice == null ? null : choice.getJSONObject("message");
        String content = message == null ? null : message.getString("content");
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("DashScope 响应缺少 choices[0].message.content");
        }

        // 模型有时会包 markdown 代码围栏，剥离后再解析业务 JSON
        String body = content.trim();
        if (body.startsWith("```")) {
            body = body.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        JSONObject obj = JSON.parseObject(body);

        AnalysisResult result = new AnalysisResult();
        result.setRiskLevel(obj.getInteger("risk_level"));
        result.setRiskReason(obj.getString("risk_reason"));
        result.setEvidence(obj.getString("evidence"));
        result.setUserFeedback(obj.getString("user_feedback"));
        result.setSuggestion(obj.getString("suggestion"));

        JSONObject usage = envelope.getJSONObject("usage");
        int promptTokens = usage == null ? 0 : usage.getIntValue("prompt_tokens");
        int completionTokens = usage == null ? 0 : usage.getIntValue("completion_tokens");
        return new ParsedAnalysis(result, promptTokens, completionTokens);
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    /**
     * 等级2 求助渠道存在性校验：不含渠道则追加固定文案。
     */
    private String ensureHelpChannel(String feedback) {
        if (feedback == null || feedback.isBlank()) {
            return HELP_TEXT;
        }
        if (feedback.contains("12356") || feedback.contains("12355")
                || feedback.contains("110") || feedback.contains("120")
                || feedback.contains("心理援助") || feedback.contains("求助")) {
            return feedback;
        }
        return feedback + "\n\n" + HELP_TEXT;
    }

    /**
     * 危机预警落库（幂等：uk_source 下重跑是覆盖而非追加，保留处置状态）。
     */
    private void upsertAlert(Long userId, Long checkinId, String triggerType, String riskReason, String evidence) {
        RiskAlert existing = riskAlertMapper.selectOne(
                new LambdaQueryWrapper<RiskAlert>()
                        .eq(RiskAlert::getSourceType, RiskAlert.SOURCE_CHECKIN)
                        .eq(RiskAlert::getSourceId, checkinId)
        );
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setLevel(2);
            existing.setTriggerType(triggerType);
            existing.setReason(truncate(riskReason));
            existing.setEvidence(truncate(evidence));
            existing.setUpdateTime(now);
            riskAlertMapper.updateById(existing);
        } else {
            RiskAlert alert = new RiskAlert();
            alert.setUserId(userId);
            alert.setSourceType(RiskAlert.SOURCE_CHECKIN);
            alert.setSourceId(checkinId);
            alert.setLevel(2);
            alert.setTriggerType(triggerType);
            alert.setReason(truncate(riskReason));
            alert.setEvidence(truncate(evidence));
            alert.setStatus(RiskAlert.STATUS_PENDING);
            alert.setCreateTime(now);
            riskAlertMapper.insert(alert);
        }
    }

    private List<String> getKeywords() {
        if (riskKeywords == null || riskKeywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(riskKeywords.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static class ParsedAnalysis {
        final AnalysisResult result;
        final int promptTokens;
        final int completionTokens;

        ParsedAnalysis(AnalysisResult result, int promptTokens, int completionTokens) {
            this.result = result;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }
    }
}
