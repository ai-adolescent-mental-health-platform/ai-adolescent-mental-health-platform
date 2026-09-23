package com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 签到提交请求体
 */
@Data
@ApiModel(value = "CheckinSubmitDTO", description = "签到提交请求体")
public class CheckinSubmitDTO {

    @ApiModelProperty(value = "情绪标签ID列表（可多选）")
    private List<Integer> tagIds;

    @ApiModelProperty(value = "日记/困惑/难处/开心事原文（可空，允许跳过）")
    private String diaryContent;
}
