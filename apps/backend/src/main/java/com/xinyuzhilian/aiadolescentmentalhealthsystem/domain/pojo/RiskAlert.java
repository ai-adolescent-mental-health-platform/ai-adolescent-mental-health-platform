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
 * 风险预警记录表（通用，多源共用）
 * 当前仅危机级入表，保留 level 列以兼容后续多源刻度
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("risk_alert")
@ApiModel(value = "RiskAlert对象", description = "风险预警记录表")
public class RiskAlert implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源：签到 */
    public static final String SOURCE_CHECKIN = "CHECKIN";
    /** 来源：量表 */
    public static final String SOURCE_ASSESSMENT = "ASSESSMENT";
    /** 来源：小爱倾听 */
    public static final String SOURCE_XIAOAI = "XIAOAI";

    /** 触发方式：模型判定 */
    public static final String TRIGGER_MODEL = "MODEL";
    /** 触发方式：关键词兜底 */
    public static final String TRIGGER_KEYWORD = "KEYWORD";
    /** 触发方式：人工 */
    public static final String TRIGGER_MANUAL = "MANUAL";

    /** 处置状态：待处置 */
    public static final int STATUS_PENDING = 0;
    /** 处置状态：处置中 */
    public static final int STATUS_PROCESSING = 1;
    /** 处置状态：已处置 */
    public static final int STATUS_RESOLVED = 2;
    /** 处置状态：已忽略 */
    public static final int STATUS_IGNORED = 3;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "用户ID")
    @TableField("user_id")
    private Long userId;

    @ApiModelProperty(value = "来源类型(CHECKIN-签到,ASSESSMENT-量表,XIAOAI-小爱倾听)")
    @TableField("source_type")
    private String sourceType;

    @ApiModelProperty(value = "来源记录ID")
    @TableField("source_id")
    private Long sourceId;

    @ApiModelProperty(value = "预警等级(2-危机,当前仅危机级入表)")
    private Integer level;

    @ApiModelProperty(value = "触发方式(MODEL-模型判定,KEYWORD-关键词兜底,MANUAL-人工)")
    @TableField("trigger_type")
    private String triggerType;

    @ApiModelProperty(value = "判定理由(平台侧)")
    private String reason;

    @ApiModelProperty(value = "触发该预警的原文摘录(供平台与家长端展示)")
    private String evidence;

    @ApiModelProperty(value = "处置状态(0-待处置,1-处置中,2-已处置,3-已忽略)")
    private Integer status;

    @ApiModelProperty(value = "处置人ID")
    @TableField("handler_id")
    private Long handlerId;

    @ApiModelProperty(value = "处置备注")
    @TableField("handle_note")
    private String handleNote;

    @ApiModelProperty(value = "处置时间")
    @TableField("handled_at")
    private LocalDateTime handledAt;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
