package com.xinyuzhilian.aiadolescentmentalhealthsystem.service;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.dto.CheckinSubmitDTO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinAnalysisVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinHistoryVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinStatsVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinTodayVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.PageResult;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.Result;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinMoodTag;

import java.util.List;

/**
 * 每日签到服务接口
 */
public interface ICheckinService {

    /**
     * 提交签到（幂等：同日重复提交返回已有记录）
     */
    Result<CheckinTodayVO> submit(Long userId, CheckinSubmitDTO dto);

    /**
     * 获取今日签到状态
     */
    Result<CheckinTodayVO> getToday(Long userId);

    /**
     * 修改今日签到内容
     */
    Result<CheckinTodayVO> update(Long userId, Long checkinId, CheckinSubmitDTO dto);

    /**
     * 获取情绪标签列表
     */
    Result<List<CheckinMoodTag>> getMoodTags();

    /**
     * 分页获取签到历史
     */
    Result<PageResult<CheckinHistoryVO>> getHistory(Long userId, Integer page, Integer size);

    /**
     * 获取签到统计（连续天数、本月数、近30天趋势）
     */
    Result<CheckinStatsVO> getStats(Long userId);

    /**
     * 获取分析结果（前端轮询用），校验记录归属。
     * 返回用户侧脱敏视图，不含平台判定字段。
     */
    Result<CheckinAnalysisVO> getAnalysis(Long userId, Long checkinId);
}
