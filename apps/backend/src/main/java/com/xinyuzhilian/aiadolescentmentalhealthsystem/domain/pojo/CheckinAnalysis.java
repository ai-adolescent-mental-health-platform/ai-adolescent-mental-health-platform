package com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 签到AI分析结果表
 * 任务状态机与分析结果合并，一个签到最多一条分析记录
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("checkin_analysis")
@ApiModel(value = "CheckinAnalysis对象", description = "签到AI分析结果表")
public class CheckinAnalysis implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：待处理 */
    public static final int STATUS_PENDING = 0;
    /** 状态：处理中 */
    public static final int STATUS_PROCESSING = 1;
    /** 状态：成功 */
    public static final int STATUS_SUCCESS = 2;
    /** 状态：失败 */
    public static final int STATUS_FAILED = 3;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "签到记录ID")
    @TableField("checkin_id")
    private Long checkinId;

    @ApiModelProperty(value = "用户ID(冗余,供家长端与运营按用户查询)")
    @TableField("user_id")
    private Long userId;

    @ApiModelProperty(value = "状态(0-待处理,1-处理中,2-成功,3-失败)")
    private Integer status;

    @ApiModelProperty(value = "预警等级(0-平稳,1-关注,2-危机);失败时为NULL,禁止默认0")
    @TableField("risk_level")
    private Integer riskLevel;

    @ApiModelProperty(value = "面向用户的共情反馈全文")
    @TableField("user_feedback")
    private String userFeedback;

    @ApiModelProperty(value = "面向用户的建议")
    private String suggestion;

    @ApiModelProperty(value = "判定理由(平台侧专用,不向用户展示)")
    @TableField("risk_reason")
    private String riskReason;

    @ApiModelProperty(value = "实际使用的模型名")
    private String model;

    @ApiModelProperty(value = "输入token数")
    @TableField("prompt_tokens")
    private Integer promptTokens;

    @ApiModelProperty(value = "输出token数")
    @TableField("completion_tokens")
    private Integer completionTokens;

    @ApiModelProperty(value = "已重试次数")
    @TableField("retry_count")
    private Integer retryCount;

    @ApiModelProperty(value = "失败原因(截断存储)")
    @TableField("error_msg")
    private String errorMsg;

    @ApiModelProperty(value = "开始处理时间")
    @TableField("started_at")
    private LocalDateTime startedAt;

    @ApiModelProperty(value = "处理完成时间")
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
