package com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 签到统计视图
 */
@Data
@ApiModel(value = "CheckinStatsVO", description = "签到统计视图")
public class CheckinStatsVO {

    @ApiModelProperty(value = "连续签到天数")
    private Integer continuousDays;

    @ApiModelProperty(value = "本月签到数")
    private Integer monthCount;

    @ApiModelProperty(value = "近30天情绪极性趋势")
    private List<TrendPoint> trend;

    @Data
    @ApiModel(value = "TrendPoint", description = "趋势点")
    public static class TrendPoint {
        @ApiModelProperty(value = "日期 yyyy-MM-dd")
        private String date;

        @ApiModelProperty(value = "情绪极性（null 表示未签到）")
        private Integer polarity;
    }
}
