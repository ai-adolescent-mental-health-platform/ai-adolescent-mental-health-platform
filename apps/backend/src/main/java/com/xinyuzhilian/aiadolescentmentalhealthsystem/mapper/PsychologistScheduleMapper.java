package com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 心理咨询师排班Mapper
 * 继承MyBatis-Plus的BaseMapper，提供基本的CRUD操作
 */
@Mapper
public interface PsychologistScheduleMapper extends BaseMapper<PsychologistSchedule> {

    /**
     * 原子递增已预约人数（防超卖）
     * 仅当 booked_count < max_appointments 时才更新
     * @return affected rows (0 = 已满)
     */
    @Update("UPDATE psychologist_schedule SET booked_count = booked_count + 1 " +
            "WHERE id = #{id} AND booked_count < max_appointments")
    int incrementBookedCount(@Param("id") Long id);

    /**
     * 原子递减已预约人数（防负数）
     * 仅当 booked_count > 0 时才更新
     * @return affected rows (0 = 已经为0)
     */
    @Update("UPDATE psychologist_schedule SET booked_count = GREATEST(booked_count - 1, 0) " +
            "WHERE id = #{id} AND booked_count > 0")
    int decrementBookedCount(@Param("id") Long id);
}
