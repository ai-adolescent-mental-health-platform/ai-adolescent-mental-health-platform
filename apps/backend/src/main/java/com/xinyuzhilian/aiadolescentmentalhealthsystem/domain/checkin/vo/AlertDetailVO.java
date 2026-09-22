package com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.RiskAlert;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 预警详情视图
 */
@Data
@ApiModel(value = "AlertDetailVO", description = "预警详情视图")
public class AlertDetailVO {

    @ApiModelProperty(value = "预警记录")
    private RiskAlert alert;

    @ApiModelProperty(value = "关联签到正文")
    private String diaryContent;
}
