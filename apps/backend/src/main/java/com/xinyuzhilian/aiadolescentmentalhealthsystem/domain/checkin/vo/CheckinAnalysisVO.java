package com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinAnalysis;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户侧分析结果视图（脱敏）
 *
 * 只暴露用户可感知的字段。riskLevel / riskReason / errorMsg / model / tokens
 * 属平台侧信息，不下发用户端（见设计 3.2「用户看到反馈，平台看到等级」）。
 */
@Data
@ApiModel(value = "CheckinAnalysisVO", description = "用户侧分析结果视图（脱敏）")
public class CheckinAnalysisVO {

    @ApiModelProperty(value = "分析记录ID")
    private Long id;

    @ApiModelProperty(value = "分析状态(0-待处理,1-处理中,2-成功,3-失败)")
    private Integer status;

    @ApiModelProperty(value = "面向用户的共情反馈")
    private String userFeedback;

    @ApiModelProperty(value = "面向用户的建议")
    private String suggestion;

    @ApiModelProperty(value = "完成时间")
    private LocalDateTime finishedAt;

    /**
     * 由实体转换，丢弃平台侧字段。
     */
    public static CheckinAnalysisVO from(CheckinAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        CheckinAnalysisVO vo = new CheckinAnalysisVO();
        vo.setId(analysis.getId());
        vo.setStatus(analysis.getStatus());
        vo.setUserFeedback(analysis.getUserFeedback());
        vo.setSuggestion(analysis.getSuggestion());
        vo.setFinishedAt(analysis.getFinishedAt());
        return vo;
    }
}
