package com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 签到AI分析结果Mapper
 * 含扫尾任务所需的抢占式更新
 */
@Mapper
public interface CheckinAnalysisMapper extends BaseMapper<CheckinAnalysis> {

    /**
     * 抢占式更新：仅当状态仍为待处理时才置为处理中。
     * 返回受影响行数，0 表示已被其他实例抢走，本次跳过。
     */
    @Update("UPDATE checkin_analysis SET status = 1, started_at = NOW(), update_time = NOW() "
            + "WHERE id = #{id} AND status = 0")
    int claimPending(@Param("id") Long id);
}
