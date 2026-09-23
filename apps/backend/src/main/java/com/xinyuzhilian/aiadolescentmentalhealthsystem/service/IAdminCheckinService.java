package com.xinyuzhilian.aiadolescentmentalhealthsystem.service;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.dto.AlertHandleDTO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.AlertDetailVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.PageResult;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.Result;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinAnalysis;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.RiskAlert;

import java.util.Map;

/**
 * 管理端签到预警服务接口
 */
public interface IAdminCheckinService {

    /**
     * 分页查询预警列表，支持按处置状态 / 来源类型 / 时间范围筛选。
     */
    Result<PageResult<RiskAlert>> getAlerts(Integer page, Integer size, Integer status,
                                            String sourceType, String startTime, String endTime);

    /**
     * 预警详情（含关联签到正文）
     */
    Result<AlertDetailVO> getAlertDetail(Long id);

    /**
     * 处置预警：置 status / handler_id / handle_note / handled_at
     */
    Result<String> handle(Long id, Long handlerId, AlertHandleDTO dto);

    /**
     * 等级2 文案复核队列（人工抽查）
     */
    Result<PageResult<CheckinAnalysis>> getCrisisReview(Integer page, Integer size);

    /**
     * 每日统计：签到量、分析量、token 消耗、各等级分布
     */
    Result<Map<String, Object>> getDailyStats(String date);
}
