package com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinMoodTag;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.UserCheckin;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 今日签到状态视图
 */
@Data
@ApiModel(value = "CheckinTodayVO", description = "今日签到状态视图")
public class CheckinTodayVO {

    @ApiModelProperty(value = "今日签到记录（null 表示未签到）")
    private UserCheckin checkin;

    @ApiModelProperty(value = "已选情绪标签")
    private List<CheckinMoodTag> tags;

    @ApiModelProperty(value = "分析结果（null 表示未触发分析）")
    private CheckinAnalysisVO analysis;
}
