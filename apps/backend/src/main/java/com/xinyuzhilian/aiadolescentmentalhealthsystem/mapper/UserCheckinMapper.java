package com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.UserCheckin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 每日签到记录Mapper
 */
@Mapper
public interface UserCheckinMapper extends BaseMapper<UserCheckin> {
}
