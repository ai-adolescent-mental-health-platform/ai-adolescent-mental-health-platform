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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日签到记录表
 * 一人一天一条记录，记录当日情绪标签与可选的日记正文
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_checkin")
@ApiModel(value = "UserCheckin对象", description = "每日签到记录表")
public class UserCheckin implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "用户ID")
    @TableField("user_id")
    private Long userId;

    @ApiModelProperty(value = "签到日期")
    @TableField("checkin_date")
    private LocalDate checkinDate;

    @ApiModelProperty(value = "当日情绪极性(所选标签polarity均值,写入时冗余)")
    @TableField("mood_polarity")
    private Integer moodPolarity;

    @ApiModelProperty(value = "日记/困惑/难处/开心事原文(可空,允许跳过)")
    @TableField("diary_content")
    private String diaryContent;

    @ApiModelProperty(value = "正文字数(冗余,列表页免count)")
    @TableField("content_length")
    private Integer contentLength;

    @ApiModelProperty(value = "创建时间")
    @TableField("create_time")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField("update_time")
    private LocalDateTime updateTime;
}
