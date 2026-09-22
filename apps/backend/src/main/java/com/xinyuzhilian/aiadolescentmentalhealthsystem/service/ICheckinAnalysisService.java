package com.xinyuzhilian.aiadolescentmentalhealthsystem.service;

/**
 * 签到 AI 分析流水线服务接口
 */
public interface ICheckinAnalysisService {

    /**
     * 在签到事务内调用：若正文非空且今日分析次数未超限，创建分析任务（status=0），
     * 并注册事务提交后（afterCommit）的投递回调。
     *
     * @param checkinId    签到记录ID
     * @param userId       用户ID
     * @param diaryContent 日记正文（可能为 null）
     */
    void scheduleIfNeeded(Long checkinId, Long userId, String diaryContent);

    /**
     * 投递分析任务到线程池（afterCommit 或扫尾任务调用）
     *
     * @param analysisId 分析任务ID
     */
    void submit(Long analysisId);
}
