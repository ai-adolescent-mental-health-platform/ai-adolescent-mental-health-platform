package com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.RiskAlert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 风险预警记录Mapper
 */
@Mapper
public interface RiskAlertMapper extends BaseMapper<RiskAlert> {
}
