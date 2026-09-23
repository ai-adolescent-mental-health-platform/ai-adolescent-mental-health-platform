package com.xinyuzhilian.aiadolescentmentalhealthsystem.controller;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.annotation.CurrentUserId;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.dto.AlertHandleDTO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.AlertDetailVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.PageResult;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.Result;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinAnalysis;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.RiskAlert;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IAdminCheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端签到预警控制器（超级管理员）
 * 处理预警列表、详情、处置、复核队列、每日统计
 */
@RestController
@RequestMapping("/admin/checkin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('4')")
public class AdminCheckinController {

    private final IAdminCheckinService adminCheckinService;

    /**
     * 分页查询预警列表
     */
    @GetMapping("/alerts")
    public Result<PageResult<RiskAlert>> alerts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return adminCheckinService.getAlerts(page, size, status, sourceType, startTime, endTime);
    }

    /**
     * 预警详情（含关联签到正文）
     */
    @GetMapping("/alerts/{id}")
    public Result<AlertDetailVO> alertDetail(@PathVariable Long id) {
        return adminCheckinService.getAlertDetail(id);
    }

    /**
     * 处置预警
     */
    @PutMapping("/alerts/{id}/handle")
    public Result<String> handle(
            @PathVariable Long id,
            @CurrentUserId Long userId,
            @RequestBody AlertHandleDTO dto) {
        return adminCheckinService.handle(id, userId, dto);
    }

    /**
     * 等级2 文案复核队列
     */
    @GetMapping("/analysis/crisis-review")
    public Result<PageResult<CheckinAnalysis>> crisisReview(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return adminCheckinService.getCrisisReview(page, size);
    }

    /**
     * 每日统计
     */
    @GetMapping("/stats/daily")
    public Result<Map<String, Object>> dailyStats(@RequestParam(required = false) String date) {
        return adminCheckinService.getDailyStats(date);
    }
}
