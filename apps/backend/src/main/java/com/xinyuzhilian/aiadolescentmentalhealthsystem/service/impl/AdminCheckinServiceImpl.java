package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.dto.AlertHandleDTO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.AlertDetailVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.PageResult;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.Result;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinAnalysis;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.RiskAlert;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.UserCheckin;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.exception.ServiceException;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.CheckinAnalysisMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.RiskAlertMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserCheckinMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IAdminCheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端签到预警服务实现
 */
@Service
@RequiredArgsConstructor
public class AdminCheckinServiceImpl implements IAdminCheckinService {

    private final RiskAlertMapper riskAlertMapper;
    private final CheckinAnalysisMapper checkinAnalysisMapper;
    private final UserCheckinMapper userCheckinMapper;

    @Override
    public Result<PageResult<RiskAlert>> getAlerts(Integer page, Integer size, Integer status,
                                                   String sourceType, String startTime, String endTime) {
        LambdaQueryWrapper<RiskAlert> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(RiskAlert::getStatus, status);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            qw.eq(RiskAlert::getSourceType, sourceType);
        }
        if (startTime != null && !startTime.isBlank()) {
            qw.ge(RiskAlert::getCreateTime, parseTime(startTime, false));
        }
        if (endTime != null && !endTime.isBlank()) {
            qw.le(RiskAlert::getCreateTime, parseTime(endTime, true));
        }
        // 待处置（status=0）置顶
        qw.orderByAsc(RiskAlert::getStatus).orderByDesc(RiskAlert::getCreateTime);

        Page<RiskAlert> p = new Page<>(page, size);
        IPage<RiskAlert> result = riskAlertMapper.selectPage(p, qw);
        return Result.success(PageResult.build(result));
    }

    @Override
    public Result<AlertDetailVO> getAlertDetail(Long id) {
        RiskAlert alert = riskAlertMapper.selectById(id);
        if (alert == null) {
            throw new ServiceException("预警记录不存在");
        }
        AlertDetailVO vo = new AlertDetailVO();
        vo.setAlert(alert);
        if (RiskAlert.SOURCE_CHECKIN.equals(alert.getSourceType())) {
            UserCheckin checkin = userCheckinMapper.selectById(alert.getSourceId());
            if (checkin != null) {
                vo.setDiaryContent(checkin.getDiaryContent());
            }
        }
        return Result.success(vo);
    }

    @Override
    public Result<String> handle(Long id, Long handlerId, AlertHandleDTO dto) {
        Integer status = dto.getStatus();
        // 处置状态白名单：只接受 1-处置中 / 2-已处置 / 3-已忽略，越界值不得落库
        if (status == null || (status != RiskAlert.STATUS_PROCESSING
                && status != RiskAlert.STATUS_RESOLVED
                && status != RiskAlert.STATUS_IGNORED)) {
            throw new ServiceException("无效的处置状态");
        }
        RiskAlert alert = riskAlertMapper.selectById(id);
        if (alert == null) {
            throw new ServiceException("预警记录不存在");
        }
        alert.setStatus(status);
        alert.setHandlerId(handlerId);
        alert.setHandleNote(dto.getHandleNote());
        alert.setHandledAt(LocalDateTime.now());
        alert.setUpdateTime(LocalDateTime.now());
        riskAlertMapper.updateById(alert);
        return Result.success("处置成功", null);
    }

    @Override
    public Result<PageResult<CheckinAnalysis>> getCrisisReview(Integer page, Integer size) {
        Page<CheckinAnalysis> p = new Page<>(page, size);
        IPage<CheckinAnalysis> result = checkinAnalysisMapper.selectPage(p,
                new LambdaQueryWrapper<CheckinAnalysis>()
                        .eq(CheckinAnalysis::getRiskLevel, 2)
                        .orderByDesc(CheckinAnalysis::getCreateTime)
        );
        return Result.success(PageResult.build(result));
    }

    @Override
    public Result<Map<String, Object>> getDailyStats(String date) {
        LocalDate d = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        LocalDateTime start = d.atStartOfDay();
        LocalDateTime end = d.plusDays(1).atStartOfDay();

        Long checkinCount = userCheckinMapper.selectCount(
                new LambdaQueryWrapper<UserCheckin>().eq(UserCheckin::getCheckinDate, d)
        );
        Long analysisCount = checkinAnalysisMapper.selectCount(
                new LambdaQueryWrapper<CheckinAnalysis>()
                        .ge(CheckinAnalysis::getCreateTime, start)
                        .lt(CheckinAnalysis::getCreateTime, end)
        );
        List<CheckinAnalysis> analyses = checkinAnalysisMapper.selectList(
                new LambdaQueryWrapper<CheckinAnalysis>()
                        .ge(CheckinAnalysis::getCreateTime, start)
                        .lt(CheckinAnalysis::getCreateTime, end)
        );
        long totalTokens = analyses.stream()
                .mapToLong(a -> (a.getPromptTokens() == null ? 0 : a.getPromptTokens())
                        + (a.getCompletionTokens() == null ? 0 : a.getCompletionTokens()))
                .sum();
        Map<Integer, Long> levelDist = analyses.stream()
                .filter(a -> a.getRiskLevel() != null)
                .collect(Collectors.groupingBy(CheckinAnalysis::getRiskLevel, Collectors.counting()));

        Map<String, Object> stats = new HashMap<>();
        stats.put("date", d.toString());
        stats.put("checkinCount", checkinCount);
        stats.put("analysisCount", analysisCount);
        stats.put("totalTokens", totalTokens);
        stats.put("levelDistribution", levelDist);
        return Result.success(stats);
    }

    private LocalDateTime parseTime(String s, boolean endOfDay) {
        try {
            if (s.contains("T")) {
                return LocalDateTime.parse(s);
            }
            LocalDate d = LocalDate.parse(s);
            return endOfDay ? d.atTime(23, 59, 59) : d.atStartOfDay();
        } catch (Exception e) {
            throw new ServiceException("时间格式不正确");
        }
    }
}
