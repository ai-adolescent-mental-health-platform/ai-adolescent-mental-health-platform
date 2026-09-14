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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 心理咨询师基础信息表
 * 存储心理咨询师的个人信息和资质
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("psychologist")
@ApiModel(value = "Psychologist对象", description = "心理咨询师基础信息表")
public class Psychologist implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID，自增")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "关联用户ID，唯一")
    @TableField("user_id")
    private Long userId;

    @ApiModelProperty(value = "真实姓名")
    @TableField("real_name")
    private String realName;

    @ApiModelProperty(value = "性别（1-男，2-女）")
    private Integer sex;

    @ApiModelProperty(value = "头像路径")
    @TableField("head_path")
    private String headPath;

    @ApiModelProperty(value = "详细个人介绍")
    private String introduction;

    @ApiModelProperty(value = "教育背景")
    @TableField("education_background")
    private String educationBackground;

    @ApiModelProperty(value = "受训经历")
    @TableField("training_experience")
    private String trainingExperience;

    @ApiModelProperty(value = "专业认证（JSON数组）")
    private String certifications;

    @ApiModelProperty(value = "咨询经验年限")
    @TableField("years_experience")
    private Integer yearsExperience;

    @ApiModelProperty(value = "咨询价格（元/次）")
    @TableField("consultation_price")
    private BigDecimal consultationPrice;

    @ApiModelProperty(value = "线下咨询价格（元/次）")
    @TableField("offline_price")
    private BigDecimal offlinePrice;

    @ApiModelProperty(value = "用户评分（0-5）")
    @TableField("rating_score")
    private BigDecimal ratingScore;

    @ApiModelProperty(value = "评分次数")
    @TableField("rating_count")
    private Integer ratingCount;

    @ApiModelProperty(value = "咨询接单量")
    @TableField("consultation_count")
    private Integer consultationCount;

    @ApiModelProperty(value = "线下咨询地区")
    @TableField("offline_region")
    private String offlineRegion;

    @ApiModelProperty(value = "详细地址")
    @TableField("offline_address")
    private String offlineAddress;

    @ApiModelProperty(value = "语言能力（JSON数组）")
    private String languages;

    @ApiModelProperty(value = "状态（0-禁用，1-启用）")
    private Integer status;

    @ApiModelProperty(value = "在线状态（0-离线，1-在线，2-忙碌）")
    @TableField("online_status")
    private Integer onlineStatus;

    @ApiModelProperty(value = "资料审核状态（0-无需审核，1-待审核，2-审核中，3-已通过，4-已拒绝）")
    @TableField("audit_status")
    private Integer auditStatus;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;

    // ========== 状态常量 ==========

    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 0;
    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;

    /** 在线状态：离线 */
    public static final int ONLINE_OFFLINE = 0;
    /** 在线状态：在线 */
    public static final int ONLINE_ONLINE = 1;
    /** 在线状态：忙碌 */
    public static final int ONLINE_BUSY = 2;

    /** 审核状态：无需审核 */
    public static final int AUDIT_NONE = 0;
    /** 审核状态：待审核 */
    public static final int AUDIT_PENDING = 1;
    /** 审核状态：审核中 */
    public static final int AUDIT_PROCESSING = 2;
    /** 审核状态：已通过 */
    public static final int AUDIT_PASSED = 3;
    /** 审核状态：已拒绝 */
    public static final int AUDIT_REJECTED = 4;
}
