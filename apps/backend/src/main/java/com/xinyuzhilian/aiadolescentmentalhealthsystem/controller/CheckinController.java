package com.xinyuzhilian.aiadolescentmentalhealthsystem.controller;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.annotation.CurrentUserId;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.dto.CheckinSubmitDTO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinAnalysisVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinHistoryVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinStatsVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinTodayVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.PageResult;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.Result;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinMoodTag;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.ICheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 每日签到控制器
 * 处理签到提交、今日状态、历史、统计等请求
 */
@RestController
@RequestMapping("/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final ICheckinService checkinService;

    /**
     * 获取今日签到状态
     */
    @GetMapping("/today")
    public Result<CheckinTodayVO> today(@CurrentUserId Long userId) {
        return checkinService.getToday(userId);
    }

    /**
     * 提交签到（幂等）
     */
    @PostMapping
    public Result<CheckinTodayVO> submit(
            @CurrentUserId Long userId,
            @RequestBody CheckinSubmitDTO dto) {
        return checkinService.submit(userId, dto);
    }

    /**
     * 修改今日签到内容
     */
    @PutMapping("/{id}")
    public Result<CheckinTodayVO> update(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @RequestBody CheckinSubmitDTO dto) {
        return checkinService.update(userId, id, dto);
    }

    /**
     * 获取情绪标签列表
     */
    @GetMapping("/mood-tags")
    public Result<List<CheckinMoodTag>> moodTags() {
        return checkinService.getMoodTags();
    }

    /**
     * 分页获取签到历史
     */
    @GetMapping("/history")
    public Result<PageResult<CheckinHistoryVO>> history(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return checkinService.getHistory(userId, page, size);
    }

    /**
     * 获取签到统计
     */
    @GetMapping("/stats")
    public Result<CheckinStatsVO> stats(@CurrentUserId Long userId) {
        return checkinService.getStats(userId);
    }

    /**
     * 获取分析结果（前端轮询用）。返回用户侧脱敏视图，不含平台判定字段。
     */
    @GetMapping("/analysis/{checkinId}")
    public Result<CheckinAnalysisVO> analysis(
            @CurrentUserId Long userId,
            @PathVariable Long checkinId) {
        return checkinService.getAnalysis(userId, checkinId);
    }
}
