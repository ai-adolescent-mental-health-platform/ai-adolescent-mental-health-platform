package com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinMoodTag;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 签到历史项视图
 */
@Data
@ApiModel(value = "CheckinHistoryVO", description = "签到历史项视图")
public class CheckinHistoryVO {

    @ApiModelProperty(value = "签到记录ID")
    private Long id;

    @ApiModelProperty(value = "签到日期")
    private LocalDate checkinDate;

    @ApiModelProperty(value = "当日情绪极性")
    private Integer moodPolarity;

    @ApiModelProperty(value = "日记正文")
    private String diaryContent;

    @ApiModelProperty(value = "已选情绪标签")
    private List<CheckinMoodTag> tags;

    @ApiModelProperty(value = "分析状态（null 表示未触发分析）")
    private Integer analysisStatus;

    @ApiModelProperty(value = "预警等级（null 表示未分析或分析失败）")
    private Integer riskLevel;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;
}
