package com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.UserCheckinMoodTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 签到-情绪标签关联Mapper
 */
@Mapper
public interface UserCheckinMoodTagMapper extends BaseMapper<UserCheckinMoodTag> {
}
