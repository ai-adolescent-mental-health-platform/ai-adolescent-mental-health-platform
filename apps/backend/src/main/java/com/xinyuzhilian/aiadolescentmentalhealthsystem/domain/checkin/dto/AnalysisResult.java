package com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.dto;

import lombok.Data;

/**
 * AI 分析结果（内部解析用）
 * 对应模型输出的 JSON 字段
 */
@Data
public class AnalysisResult {

    /** 预警等级（0-平稳,1-关注,2-危机） */
    private Integer riskLevel;

    /** 判定理由（平台侧） */
    private String riskReason;

    /** 触发判定的原文摘录 */
    private String evidence;

    /** 面向用户的共情反馈全文 */
    private String userFeedback;

    /** 面向用户的建议 */
    private String suggestion;
}
