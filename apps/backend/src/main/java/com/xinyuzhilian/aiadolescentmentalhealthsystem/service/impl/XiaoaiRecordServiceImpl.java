package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.constant.XiaoaiConstants;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.XiaoaiMessage;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.XiaoaiSession;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.XiaoaiUsageStat;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.UserMembership;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.XiaoaiMessageMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.XiaoaiSessionMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.XiaoaiUsageStatMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserMembershipMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.XiaoaiRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 小爱倾听师会话记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XiaoaiRecordServiceImpl implements XiaoaiRecordService {

    private final XiaoaiSessionMapper sessionMapper;
    private final XiaoaiMessageMapper messageMapper;
    private final XiaoaiUsageStatMapper usageStatMapper;
    private final UserMembershipMapper membershipMapper;

    @Override
    @Transactional
    public Long createSession(Long userId) {
        XiaoaiSession session = new XiaoaiSession();
        session.setUserId(userId);
        session.setStartTime(LocalDateTime.now());
        session.setStatus(XiaoaiConstants.SESSION_STATUS_ACTIVE);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        log.info("[会话] 创建会话成功, sessionId: {}, userId: {}", session.getId(), userId);
        return session.getId();
    }

    @Override
    @Transactional
    public void endSession(Long sessionId, String endReason) {
        XiaoaiSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setEndTime(LocalDateTime.now());
            session.setStatus(XiaoaiConstants.SESSION_STATUS_ENDED);
            // end_reason 为 varchar(32)：调用方可能传入带错误详情的长串（如
            // "aliyun_failed:{...完整错误JSON...}"），直接写会 DataTruncation 导致
            // 整条更新失败、结束原因与时长全部丢失。此处截断，完整原因见下方日志。
            session.setEndReason(truncateEndReason(endReason));

            // 计算会话时长
            if (session.getStartTime() != null) {
                long seconds = java.time.Duration.between(session.getStartTime(), session.getEndTime()).getSeconds();
                session.setTotalSeconds((int) seconds);
            }

            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
            log.info("[会话] 结束会话, sessionId: {}, reason: {}, duration: {}s", 
                    sessionId, endReason, session.getTotalSeconds());
        }
    }

    /**
     * 将结束原因裁剪到 end_reason 列宽以内。
     * 完整原因由调用方通过日志保留，落库只存前缀，避免整条更新因截断错误而失败。
     */
    private String truncateEndReason(String endReason) {
        if (endReason == null || endReason.length() <= XiaoaiConstants.END_REASON_MAX_LENGTH) {
            return endReason;
        }
        return endReason.substring(0, XiaoaiConstants.END_REASON_MAX_LENGTH);
    }

    @Override
    @Transactional
    public void recordMessage(Long sessionId, String role, String content) {
        XiaoaiMessage message = new XiaoaiMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
        log.debug("[消息] 记录消息, sessionId: {}, role: {}, contentLength: {}", 
                sessionId, role, content != null ? content.length() : 0);
    }

    @Override
    @Transactional
    public void updateSessionDuration(Long sessionId, int seconds) {
        XiaoaiSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            int total = session.getTotalSeconds() != null ? session.getTotalSeconds() : 0;
            session.setTotalSeconds(total + seconds);
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }

    @Override
    public int getTodayUsedSeconds(Long userId) {
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<XiaoaiUsageStat> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XiaoaiUsageStat::getUserId, userId)
                .eq(XiaoaiUsageStat::getLastDate, today);
        XiaoaiUsageStat usageStat = usageStatMapper.selectOne(wrapper);

        if (usageStat == null) {
            return 0;
        }

        return usageStat.getTotalSeconds() != null ? usageStat.getTotalSeconds() : 0;
    }

    @Override
    @Transactional
    public void consumeTime(Long userId, int seconds) {
        LocalDate today = LocalDate.now();

        // 查询或创建今日使用记录
        LambdaQueryWrapper<XiaoaiUsageStat> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XiaoaiUsageStat::getUserId, userId)
                .eq(XiaoaiUsageStat::getLastDate, today);
        XiaoaiUsageStat usageStat = usageStatMapper.selectOne(wrapper);

        if (usageStat == null) {
            // 创建新记录
            usageStat = new XiaoaiUsageStat();
            usageStat.setUserId(userId);
            usageStat.setTotalSeconds(seconds);
            usageStat.setLastDate(today);
            usageStat.setCreateTime(LocalDateTime.now());
            usageStat.setUpdateTime(LocalDateTime.now());
            usageStatMapper.insert(usageStat);
        } else {
            // 更新已使用时长
            int current = usageStat.getTotalSeconds() != null ? usageStat.getTotalSeconds() : 0;
            usageStat.setTotalSeconds(current + seconds);
            usageStat.setUpdateTime(LocalDateTime.now());
            usageStatMapper.updateById(usageStat);
        }

        log.debug("[时长] 扣除时长, userId: {}, seconds: {}, totalToday: {}", 
                userId, seconds, usageStat.getTotalSeconds());
    }

    /**
     * 获取用户每日时长限制
     * @param userId 用户ID
     * @return 每日限制（秒）
     */
    public int getDailyLimit(Long userId) {
        LambdaQueryWrapper<UserMembership> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMembership::getUserId, userId);
        UserMembership membership = membershipMapper.selectOne(wrapper);

        if (membership == null) {
            return XiaoaiConstants.DEFAULT_DAILY_LIMIT;
        }

        // 检查会员是否过期
        if (membership.getMemberExpireTime() != null
                && membership.getMemberExpireTime().isBefore(LocalDateTime.now())) {
            return XiaoaiConstants.DEFAULT_DAILY_LIMIT;
        }

        int memberType = membership.getMemberType() != null ? membership.getMemberType() : 0;

        switch (memberType) {
            case XiaoaiConstants.MEMBER_TYPE_SVIP:
                return XiaoaiConstants.SVIP_DAILY_LIMIT;
            case XiaoaiConstants.MEMBER_TYPE_VIP:
                return XiaoaiConstants.VIP_DAILY_LIMIT;
            default:
                return XiaoaiConstants.DEFAULT_DAILY_LIMIT;
        }
    }
}
