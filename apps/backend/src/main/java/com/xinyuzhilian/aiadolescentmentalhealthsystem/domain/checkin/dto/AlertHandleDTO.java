package com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 预警处置请求体
 */
@Data
@ApiModel(value = "AlertHandleDTO", description = "预警处置请求体")
public class AlertHandleDTO {

    @ApiModelProperty(value = "处置状态(1-处置中,2-已处置,3-已忽略)")
    private Integer status;

    @ApiModelProperty(value = "处置备注")
    private String handleNote;
}
