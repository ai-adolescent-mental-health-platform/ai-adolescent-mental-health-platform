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
 * 签到情绪标签字典表
 * 平台预置的情绪标签，如：开心、焦虑、低落、孤独等
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("checkin_mood_tag")
@ApiModel(value = "CheckinMoodTag对象", description = "签到情绪标签字典表")
public class CheckinMoodTag implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty(value = "标签名称")
    private String name;

    @ApiModelProperty(value = "标签代码")
    private String code;

    @ApiModelProperty(value = "lucide图标名(前端静态映射,不存emoji)")
    private String icon;

    @ApiModelProperty(value = "pouf色调(pink/purple/blue/mint/yellow/orange)")
    private String tone;

    @ApiModelProperty(value = "情绪极性(-2-很负面,-1-负面,0-中性,1-正面,2-很正面)")
    private Integer polarity;

    @ApiModelProperty(value = "状态(0-禁用,1-启用)")
    private Integer status;

    @ApiModelProperty(value = "排序")
    @TableField("sort_order")
    private Integer sortOrder;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;
}
