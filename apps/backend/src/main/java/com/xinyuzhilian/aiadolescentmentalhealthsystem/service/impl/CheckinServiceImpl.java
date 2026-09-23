package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.dto.CheckinSubmitDTO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinAnalysisVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinHistoryVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinStatsVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinTodayVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.PageResult;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.Result;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinAnalysis;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinMoodTag;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.UserCheckin;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.UserCheckinMoodTag;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.exception.ServiceException;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.CheckinAnalysisMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.CheckinMoodTagMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserCheckinMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserCheckinMoodTagMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.ICheckinAnalysisService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.ICheckinService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IDictDataService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.utils.CheckinLogic;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 每日签到服务实现类
 */
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements ICheckinService {

    /** 日记正文长度上限（信任边界：同时保护入库与 LLM 调用） */
    private static final int MAX_DIARY_LENGTH = 2000;

    private final UserCheckinMapper userCheckinMapper;
    private final UserCheckinMoodTagMapper userCheckinMoodTagMapper;
    private final CheckinMoodTagMapper checkinMoodTagMapper;
    private final CheckinAnalysisMapper checkinAnalysisMapper;
    private final IDictDataService dictDataService;
    private final ICheckinAnalysisService checkinAnalysisService;

    @Override
    @Transactional
    public Result<CheckinTodayVO> submit(Long userId, CheckinSubmitDTO dto) {
        LocalDate today = LocalDate.now();
        // 幂等：同日重复提交返回已有记录
        UserCheckin existing = userCheckinMapper.selectOne(
                new LambdaQueryWrapper<UserCheckin>()
                        .eq(UserCheckin::getUserId, userId)
                        .eq(UserCheckin::getCheckinDate, today)
        );
        if (existing != null) {
            return Result.success(buildTodayVO(existing));
        }

        List<Integer> tagIds = dto.getTagIds();
        if (tagIds == null || tagIds.isEmpty()) {
            throw new ServiceException("请至少选择一个情绪标签");
        }
        // 去重后校验标签有效性
        List<Integer> distinctTagIds = tagIds.stream().distinct().toList();
        List<CheckinMoodTag> tags = checkinMoodTagMapper.selectBatchIds(distinctTagIds);
        if (tags.size() != distinctTagIds.size()) {
            throw new ServiceException("包含无效的情绪标签");
        }

        // 情绪极性均值（写入时冗余）
        List<Integer> polarities = tags.stream().map(CheckinMoodTag::getPolarity).toList();
        int avgPolarity = CheckinLogic.avgPolarity(polarities);

        String diary = normalize(dto.getDiaryContent());
        // 信任边界：日记正文会入库并直发 LLM，此处限长（项目未引入 Bean Validation，按现有风格显式校验）
        if (diary != null && diary.length() > MAX_DIARY_LENGTH) {
            throw new ServiceException("日记内容不能超过 " + MAX_DIARY_LENGTH + " 字");
        }

        UserCheckin checkin = new UserCheckin();
        checkin.setUserId(userId);
        checkin.setCheckinDate(today);
        checkin.setMoodPolarity(avgPolarity);
        checkin.setDiaryContent(diary);
        checkin.setContentLength(diary == null ? 0 : diary.length());
        try {
            userCheckinMapper.insert(checkin);
        } catch (DuplicateKeyException e) {
            // 并发同日提交：唯一键 uk_user_checkin_date 拦下后回查，返回既有记录而非 500。
            // RR 快照可能读不到刚提交的并发行，此时降级为明确的业务提示。
            UserCheckin concurrent = userCheckinMapper.selectOne(
                    new LambdaQueryWrapper<UserCheckin>()
                            .eq(UserCheckin::getUserId, userId)
                            .eq(UserCheckin::getCheckinDate, today)
            );
            if (concurrent != null) {
                return Result.success(buildTodayVO(concurrent));
            }
            throw new ServiceException("今日已完成签到");
        }

        for (Integer tagId : distinctTagIds) {
            UserCheckinMoodTag rel = new UserCheckinMoodTag();
            rel.setCheckinId(checkin.getId());
            rel.setTagId(tagId);
            userCheckinMoodTagMapper.insert(rel);
        }

        // 分析任务创建与 afterCommit 投递
        checkinAnalysisService.scheduleIfNeeded(checkin.getId(), userId, diary);
        return Result.success(buildTodayVO(checkin));
    }

    @Override
    public Result<CheckinTodayVO> getToday(Long userId) {
        LocalDate today = LocalDate.now();
        UserCheckin checkin = userCheckinMapper.selectOne(
                new LambdaQueryWrapper<UserCheckin>()
                        .eq(UserCheckin::getUserId, userId)
                        .eq(UserCheckin::getCheckinDate, today)
        );
        return Result.success(buildTodayVO(checkin));
    }

    @Override
    @Transactional
    public Result<CheckinTodayVO> update(Long userId, Long checkinId, CheckinSubmitDTO dto) {
        UserCheckin checkin = userCheckinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new ServiceException("签到记录不存在");
        }
        // 归属校验
        if (!checkin.getUserId().equals(userId)) {
            throw new ServiceException(403, "无权操作该签到记录");
        }
        // 只允许修改今日内容
        if (!checkin.getCheckinDate().equals(LocalDate.now())) {
            throw new ServiceException("只能修改今日的签到内容");
        }

        List<Integer> tagIds = dto.getTagIds();
        if (tagIds == null || tagIds.isEmpty()) {
            throw new ServiceException("请至少选择一个情绪标签");
        }
        List<Integer> distinctTagIds = tagIds.stream().distinct().toList();
        List<CheckinMoodTag> tags = checkinMoodTagMapper.selectBatchIds(distinctTagIds);
        if (tags.size() != distinctTagIds.size()) {
            throw new ServiceException("包含无效的情绪标签");
        }

        List<Integer> polarities = tags.stream().map(CheckinMoodTag::getPolarity).toList();
        int avgPolarity = CheckinLogic.avgPolarity(polarities);
        String diary = normalize(dto.getDiaryContent());

        checkin.setMoodPolarity(avgPolarity);
        checkin.setDiaryContent(diary);
        checkin.setContentLength(diary == null ? 0 : diary.length());
        userCheckinMapper.updateById(checkin);

        // 重建标签关联
        userCheckinMoodTagMapper.delete(
                new LambdaQueryWrapper<UserCheckinMoodTag>().eq(UserCheckinMoodTag::getCheckinId, checkinId)
        );
        for (Integer tagId : distinctTagIds) {
            UserCheckinMoodTag rel = new UserCheckinMoodTag();
            rel.setCheckinId(checkinId);
            rel.setTagId(tagId);
            userCheckinMoodTagMapper.insert(rel);
        }

        // 触发重新分析（受每日次数上限约束）
        checkinAnalysisService.scheduleIfNeeded(checkinId, userId, diary);
        return Result.success(buildTodayVO(checkin));
    }

    @Override
    public Result<List<CheckinMoodTag>> getMoodTags() {
        return Result.success(dictDataService.getAllMoodTags());
    }

    @Override
    public Result<PageResult<CheckinHistoryVO>> getHistory(Long userId, Integer page, Integer size) {
        Page<UserCheckin> p = new Page<>(page, size);
        IPage<UserCheckin> result = userCheckinMapper.selectPage(p,
                new LambdaQueryWrapper<UserCheckin>()
                        .eq(UserCheckin::getUserId, userId)
                        .orderByDesc(UserCheckin::getCheckinDate)
        );

        List<CheckinHistoryVO> vos = result.getRecords().stream()
                .map(this::toHistoryVO)
                .toList();

        PageResult<CheckinHistoryVO> pr = new PageResult<>();
        pr.setTotal(result.getTotal());
        pr.setRecords(vos);
        pr.setCurrent(result.getCurrent());
        pr.setSize(result.getSize());
        pr.setPages(result.getPages());
        return Result.success(pr);
    }

    @Override
    public Result<CheckinStatsVO> getStats(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(365);
        List<UserCheckin> list = userCheckinMapper.selectList(
                new LambdaQueryWrapper<UserCheckin>()
                        .eq(UserCheckin::getUserId, userId)
                        .ge(UserCheckin::getCheckinDate, since)
        );

        Set<LocalDate> dates = list.stream().map(UserCheckin::getCheckinDate).collect(Collectors.toSet());

        // 连续天数：今天未签则从昨天起算，逐日回溯，遇断点停止
        int continuous = CheckinLogic.calcContinuousDays(dates, today);

        // 本月签到数
        int monthCount = (int) list.stream()
                .filter(c -> c.getCheckinDate().getYear() == today.getYear()
                        && c.getCheckinDate().getMonthValue() == today.getMonthValue())
                .count();

        // 近 30 天情绪极性趋势
        Map<LocalDate, Integer> polarityMap = list.stream()
                .collect(Collectors.toMap(UserCheckin::getCheckinDate, UserCheckin::getMoodPolarity, (a, b) -> a));
        LocalDate start = today.minusDays(29);
        List<CheckinStatsVO.TrendPoint> trend = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            LocalDate d = start.plusDays(i);
            CheckinStatsVO.TrendPoint point = new CheckinStatsVO.TrendPoint();
            point.setDate(d.toString());
            point.setPolarity(polarityMap.get(d));
            trend.add(point);
        }

        CheckinStatsVO vo = new CheckinStatsVO();
        vo.setContinuousDays(continuous);
        vo.setMonthCount(monthCount);
        vo.setTrend(trend);
        return Result.success(vo);
    }

    @Override
    public Result<CheckinAnalysisVO> getAnalysis(Long userId, Long checkinId) {
        UserCheckin checkin = userCheckinMapper.selectById(checkinId);
        if (checkin == null || !checkin.getUserId().equals(userId)) {
            throw new ServiceException(403, "无权访问该签到记录");
        }
        CheckinAnalysis analysis = checkinAnalysisMapper.selectOne(
                new LambdaQueryWrapper<CheckinAnalysis>().eq(CheckinAnalysis::getCheckinId, checkinId)
        );
        // 用户侧只下发脱敏视图，平台字段（riskLevel/riskReason/errorMsg）不出后端
        return Result.success(CheckinAnalysisVO.from(analysis));
    }

    private CheckinTodayVO buildTodayVO(UserCheckin checkin) {
        CheckinTodayVO vo = new CheckinTodayVO();
        if (checkin == null) {
            vo.setTags(List.of());
            return vo;
        }
        vo.setCheckin(checkin);
        List<UserCheckinMoodTag> rels = userCheckinMoodTagMapper.selectList(
                new LambdaQueryWrapper<UserCheckinMoodTag>().eq(UserCheckinMoodTag::getCheckinId, checkin.getId())
        );
        List<Integer> tagIds = rels.stream().map(UserCheckinMoodTag::getTagId).toList();
        List<CheckinMoodTag> tags = tagIds.isEmpty() ? List.of() : checkinMoodTagMapper.selectBatchIds(tagIds);
        vo.setTags(tags);

        CheckinAnalysis analysis = checkinAnalysisMapper.selectOne(
                new LambdaQueryWrapper<CheckinAnalysis>().eq(CheckinAnalysis::getCheckinId, checkin.getId())
        );
        vo.setAnalysis(CheckinAnalysisVO.from(analysis));
        return vo;
    }

    private CheckinHistoryVO toHistoryVO(UserCheckin checkin) {
        CheckinHistoryVO vo = new CheckinHistoryVO();
        vo.setId(checkin.getId());
        vo.setCheckinDate(checkin.getCheckinDate());
        vo.setMoodPolarity(checkin.getMoodPolarity());
        vo.setDiaryContent(checkin.getDiaryContent());
        vo.setCreateTime(checkin.getCreateTime());

        List<UserCheckinMoodTag> rels = userCheckinMoodTagMapper.selectList(
                new LambdaQueryWrapper<UserCheckinMoodTag>().eq(UserCheckinMoodTag::getCheckinId, checkin.getId())
        );
        List<Integer> tagIds = rels.stream().map(UserCheckinMoodTag::getTagId).toList();
        vo.setTags(tagIds.isEmpty() ? List.of() : checkinMoodTagMapper.selectBatchIds(tagIds));

        CheckinAnalysis analysis = checkinAnalysisMapper.selectOne(
                new LambdaQueryWrapper<CheckinAnalysis>().eq(CheckinAnalysis::getCheckinId, checkin.getId())
        );
        if (analysis != null) {
            vo.setAnalysisStatus(analysis.getStatus());
        }
        return vo;
    }

    private String normalize(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
