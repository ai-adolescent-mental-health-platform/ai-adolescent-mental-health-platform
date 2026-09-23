package com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinMoodTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 签到情绪标签Mapper
 */
@Mapper
public interface CheckinMoodTagMapper extends BaseMapper<CheckinMoodTag> {
}
