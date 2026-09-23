package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.alibaba.fastjson2.JSON;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinAnalysis;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.RiskAlert;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.UserCheckin;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.CheckinAnalysisMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.RiskAlertMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserCheckinMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.utils.DashScopeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 签到分析流水线单元测试（Mockito，不依赖数据库）。
 *
 * 覆盖设计文档 11 节「关键词兜底规则」「等级1不写 risk_alert」
 * 「解析失败 risk_level 保持 NULL」「分析抢占式更新」「扫尾任务」，
 * 以及两条危机安全属性：响应信封解析、关键词兜底不依赖模型可用性。
 */
@ExtendWith(MockitoExtension.class)
class CheckinAnalysisServiceImplTest {

    @Mock private CheckinAnalysisMapper checkinAnalysisMapper;
    @Mock private UserCheckinMapper userCheckinMapper;
    @Mock private DashScopeClient dashScopeClient;
    @Mock private RiskAlertMapper riskAlertMapper;
    @Mock private ThreadPoolTaskExecutor executor;

    private CheckinAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CheckinAnalysisServiceImpl(
                checkinAnalysisMapper, userCheckinMapper, dashScopeClient,
                riskAlertMapper, executor);
    }

    private CheckinAnalysis pendingAnalysis() {
        CheckinAnalysis analysis = new CheckinAnalysis();
        analysis.setId(1L);
        analysis.setCheckinId(10L);
        analysis.setUserId(1L);
        return analysis;
    }

    private UserCheckin checkinWith(String diary) {
        UserCheckin checkin = new UserCheckin();
        checkin.setId(10L);
        checkin.setUserId(1L);
        checkin.setDiaryContent(diary);
        return checkin;
    }

    /**
     * 构造与真实 DashScope 响应同形状的 OpenAI 兼容信封：
     * 业务 JSON 位于 choices[0].message.content，token 用量位于根级 usage。
     * 早前测试直接返回裸业务 JSON，与真实响应形状不符，掩盖了解析层错位。
     */
    private String envelope(String businessJson, int promptTokens, int completionTokens) {
        return "{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion\",\"model\":\"qwen3-max\","
                + "\"choices\":[{\"index\":0,\"finish_reason\":\"stop\","
                + "\"message\":{\"role\":\"assistant\",\"content\":" + JSON.toJSONString(businessJson) + "}}],"
                + "\"usage\":{\"prompt_tokens\":" + promptTokens
                + ",\"completion_tokens\":" + completionTokens
                + ",\"total_tokens\":" + (promptTokens + completionTokens) + "}}";
    }

    private String businessJson(int level, String reason, String evidence, String feedback, String suggestion) {
        return "{\"risk_level\":" + level + ",\"risk_reason\":\"" + reason + "\",\"evidence\":\"" + evidence
                + "\",\"user_feedback\":\"" + feedback + "\",\"suggestion\":\"" + suggestion + "\"}";
    }

    // ========== 必修复 1：真实信封解析出等级与 token 用量 ==========

    @Test
    void process_真实信封_解析出等级与token用量() throws IOException {
        CheckinAnalysis analysis = pendingAnalysis();
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(1);
        when(checkinAnalysisMapper.selectById(1L)).thenReturn(analysis);
        when(userCheckinMapper.selectById(10L)).thenReturn(checkinWith("今天有点低落。"));

        when(dashScopeClient.chat(any(), any())).thenReturn(
                envelope(businessJson(1, "持续低落", "今天有点低落", "抱抱你，慢慢来", "多休息"), 120, 40));

        service.process(1L);

        ArgumentCaptor<CheckinAnalysis> captor = ArgumentCaptor.forClass(CheckinAnalysis.class);
        verify(checkinAnalysisMapper).updateById(captor.capture());
        CheckinAnalysis saved = captor.getValue();
        // 若解析仍对着信封根取字段，这里会是 null / 0
        assertEquals(1, saved.getRiskLevel());
        assertEquals("抱抱你，慢慢来", saved.getUserFeedback());
        assertEquals("持续低落", saved.getRiskReason());
        assertEquals(120, saved.getPromptTokens());
        assertEquals(40, saved.getCompletionTokens());
        assertEquals(CheckinAnalysis.STATUS_SUCCESS, saved.getStatus());
    }

    @Test
    void process_响应缺choices_解析失败_riskLevel保持NULL() throws IOException {
        CheckinAnalysis analysis = pendingAnalysis();
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(1);
        when(checkinAnalysisMapper.selectById(1L)).thenReturn(analysis);
        when(userCheckinMapper.selectById(10L)).thenReturn(checkinWith("今天还行。"));

        // 网关错误响应：没有 choices
        when(dashScopeClient.chat(any(), any()))
                .thenReturn("{\"error\":{\"code\":\"InvalidApiKey\",\"message\":\"401\"}}");

        service.process(1L);

        ArgumentCaptor<CheckinAnalysis> captor = ArgumentCaptor.forClass(CheckinAnalysis.class);
        verify(checkinAnalysisMapper).updateById(captor.capture());
        assertNull(captor.getValue().getRiskLevel(), "解析失败时 risk_level 必须保持 NULL");
        assertEquals(CheckinAnalysis.STATUS_FAILED, captor.getValue().getStatus());
    }

    // ========== 关键词兜底：命中 + 模型判 0 → 最终 2 ==========

    @Test
    void process_关键词命中_强制level2() throws IOException {
        ReflectionTestUtils.setField(service, "riskKeywords", "自杀,轻生");
        CheckinAnalysis analysis = pendingAnalysis();
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(1);
        when(checkinAnalysisMapper.selectById(1L)).thenReturn(analysis);
        when(userCheckinMapper.selectById(10L)).thenReturn(checkinWith("我今天很想自杀。"));

        // 模型判 0（平稳），但关键词命中 → 强制 2
        when(dashScopeClient.chat(any(), any())).thenReturn(
                envelope(businessJson(0, "无明显风险", "", "保持", ""), 100, 20));

        service.process(1L);

        ArgumentCaptor<CheckinAnalysis> captor = ArgumentCaptor.forClass(CheckinAnalysis.class);
        verify(checkinAnalysisMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getRiskLevel());

        ArgumentCaptor<RiskAlert> alertCaptor = ArgumentCaptor.forClass(RiskAlert.class);
        verify(riskAlertMapper, atLeastOnce()).insert(alertCaptor.capture());
        assertEquals(RiskAlert.TRIGGER_KEYWORD, alertCaptor.getValue().getTriggerType());
        assertEquals(2, alertCaptor.getValue().getLevel());
    }

    // ========== 必修复 2：关键词兜底不依赖模型可用性 ==========

    @Test
    void process_关键词命中_模型调用失败_兜底仍生效() throws IOException {
        ReflectionTestUtils.setField(service, "riskKeywords", "自杀,轻生");
        CheckinAnalysis analysis = pendingAnalysis();
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(1);
        when(checkinAnalysisMapper.selectById(1L)).thenReturn(analysis);
        when(userCheckinMapper.selectById(10L)).thenReturn(checkinWith("我觉得活着没意思，想轻生。"));
        when(dashScopeClient.chat(any(), any())).thenThrow(new IOException("DashScope API 返回 429"));

        service.process(1L);

        // 预警必须先于模型调用落库，模型失败不得让危机路径失效
        InOrder inOrder = inOrder(riskAlertMapper, dashScopeClient);
        inOrder.verify(riskAlertMapper).insert(any(RiskAlert.class));
        inOrder.verify(dashScopeClient).chat(any(), any());

        ArgumentCaptor<CheckinAnalysis> captor = ArgumentCaptor.forClass(CheckinAnalysis.class);
        verify(checkinAnalysisMapper).updateById(captor.capture());
        CheckinAnalysis saved = captor.getValue();
        assertEquals(2, saved.getRiskLevel(), "关键词兜底必须给出等级 2，而非失败态");
        assertEquals(CheckinAnalysis.STATUS_SUCCESS, saved.getStatus());
        assertTrue(saved.getUserFeedback().contains("12356"), "危机反馈必须含求助渠道");
    }

    // ========== 关键词兜底：未命中 + 模型判 2 → 最终 2 ==========

    @Test
    void process_模型判2_未命中关键词_落库level2() throws IOException {
        CheckinAnalysis analysis = pendingAnalysis();
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(1);
        when(checkinAnalysisMapper.selectById(1L)).thenReturn(analysis);
        when(userCheckinMapper.selectById(10L)).thenReturn(checkinWith("最近很难受。"));

        when(dashScopeClient.chat(any(), any())).thenReturn(
                envelope(businessJson(2, "明显痛苦", "最近很难受", "请拨打 12356", "寻求帮助"), 110, 30));

        service.process(1L);

        ArgumentCaptor<CheckinAnalysis> captor = ArgumentCaptor.forClass(CheckinAnalysis.class);
        verify(checkinAnalysisMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getRiskLevel());
        verify(riskAlertMapper).insert(any(RiskAlert.class));
    }

    // ========== 等级 1 不写 risk_alert ==========

    @Test
    void process_模型判1_不写riskAlert() throws IOException {
        CheckinAnalysis analysis = pendingAnalysis();
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(1);
        when(checkinAnalysisMapper.selectById(1L)).thenReturn(analysis);
        when(userCheckinMapper.selectById(10L)).thenReturn(checkinWith("今天有点低落。"));

        when(dashScopeClient.chat(any(), any())).thenReturn(
                envelope(businessJson(1, "持续低落", "今天有点低落", "抱抱你", "多休息"), 105, 25));

        service.process(1L);

        ArgumentCaptor<CheckinAnalysis> captor = ArgumentCaptor.forClass(CheckinAnalysis.class);
        verify(checkinAnalysisMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getRiskLevel());
        verify(riskAlertMapper, never()).insert(any(RiskAlert.class));
        verify(riskAlertMapper, never()).updateById(any(RiskAlert.class));
    }

    // ========== 无关键词命中且模型失败：risk_level 保持 NULL ==========

    @Test
    void process_无关键词_模型失败_riskLevel保持NULL() throws IOException {
        CheckinAnalysis analysis = pendingAnalysis();
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(1);
        when(checkinAnalysisMapper.selectById(1L)).thenReturn(analysis);
        when(userCheckinMapper.selectById(10L)).thenReturn(checkinWith("今天有点低落。"));
        when(dashScopeClient.chat(any(), any())).thenThrow(new IOException("网络错误"));

        service.process(1L);

        ArgumentCaptor<CheckinAnalysis> captor = ArgumentCaptor.forClass(CheckinAnalysis.class);
        verify(checkinAnalysisMapper).updateById(captor.capture());
        assertNull(captor.getValue().getRiskLevel(), "失败时 risk_level 必须保持 NULL");
        assertEquals(CheckinAnalysis.STATUS_FAILED, captor.getValue().getStatus());
        verify(riskAlertMapper, never()).insert(any(RiskAlert.class));
    }

    // ========== 分析抢占式更新 ==========

    @Test
    void process_抢占失败_不执行() throws IOException {
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(0);

        service.process(1L);

        verify(dashScopeClient, never()).chat(any(), any());
        verify(checkinAnalysisMapper, never()).updateById(any(CheckinAnalysis.class));
    }

    // ========== 扫尾任务（三种捞取条件） ==========

    @Test
    void sweep_三种捞取条件() {
        CheckinAnalysis pending = new CheckinAnalysis();
        pending.setId(1L);
        CheckinAnalysis failed = new CheckinAnalysis();
        failed.setId(2L);
        failed.setRetryCount(1);
        CheckinAnalysis stuck = new CheckinAnalysis();
        stuck.setId(3L);

        when(checkinAnalysisMapper.selectList(any()))
                .thenReturn(List.of(pending), List.of(failed), List.of(stuck));

        service.sweep();

        // pending → submit（1 次）；failed → 置 PENDING + submit（1 次）；stuck → 置 FAILED（0 次）
        verify(executor, times(2)).execute(any());
        // failed 与 stuck 各自 updateById 一次
        verify(checkinAnalysisMapper, times(2)).updateById(any(CheckinAnalysis.class));
    }
}
