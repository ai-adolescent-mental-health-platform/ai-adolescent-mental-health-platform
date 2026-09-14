package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.PageResult;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.*;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.*;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistAppointmentService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistIncomeService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPlatformIncomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 心理咨询预约服务实现类
 *
 * @author AI Developer
 * @since 2026-04-14
 */
@Service
@RequiredArgsConstructor
public class PsychologistAppointmentServiceImpl extends ServiceImpl<PsychologistAppointmentMapper, PsychologistAppointment>
        implements IPsychologistAppointmentService {

    private final PsychologistAppointmentMapper appointmentMapper;
    private final PsychologistMapper psychologistMapper;
    private final PsychologistScheduleMapper scheduleMapper;
    private final UserMapper userMapper;
    private final PsychologistRatingMapper ratingMapper;
    private final PsychologistServiceMapper serviceMapper;
    private final IPsychologistService psychologistService;
    private final IPsychologistIncomeService incomeService;
    private final IPlatformIncomeService platformIncomeService;
    private final UserPsychologistHistoryMapper historyMapper;

    @Override
    @Transactional
    public Long createAppointment(Map<String, Object> request, Long userId) {
        // 解析请求参数
        Long psychologistId = Long.valueOf(request.get("psychologistId").toString());
        Long scheduleId = Long.valueOf(request.get("scheduleId").toString());
        String serviceType = request.get("serviceType").toString();

        // 获取排班信息
        PsychologistSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排班不存在");
        }
        if (schedule.getStatus().equals(PsychologistSchedule.STATUS_REST)) {
            throw new RuntimeException("该时段不可预约");
        }
        if (schedule.getBookedCount() >= schedule.getMaxAppointments()) {
            throw new RuntimeException("该时段已约满");
        }

        // 获取咨询师信息
        Psychologist psychologist = psychologistMapper.selectById(psychologistId);
        if (psychologist == null) {
            throw new RuntimeException("心理咨询师不存在");
        }

        // 校验服务类型是否启用
        String checkServiceType = "video";
        if ("OFFLINE".equalsIgnoreCase(serviceType)) {
            checkServiceType = "offline";
        }
        LambdaQueryWrapper<PsychologistService> serviceWrapper = new LambdaQueryWrapper<>();
        serviceWrapper.eq(PsychologistService::getPsychologistId, psychologistId)
                .eq(PsychologistService::getServiceType, checkServiceType);
        PsychologistService service = serviceMapper.selectOne(serviceWrapper);
        if (service == null || service.getStatus() == 0) {
            throw new RuntimeException("心理咨询师暂不支持该服务类型");
        }

        // 根据服务类型获取价格（统一从咨询师表consultationPrice/offlinePrice获取）
        BigDecimal fee;
        if ("OFFLINE".equalsIgnoreCase(serviceType)) {
            fee = psychologist.getOfflinePrice();
            if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("线下咨询价格未设置");
            }
        } else {
            fee = psychologist.getConsultationPrice();
            if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("线上咨询价格未设置");
            }
        }

        // 构建用户基本情况JSON
        Map<String, Object> basicInfo = new HashMap<>();
        if (request.containsKey("personalSituation")) {
            basicInfo.put("personalSituation", request.get("personalSituation"));
        }
        if (request.containsKey("problems")) {
            basicInfo.put("problems", request.get("problems"));
        }
        if (request.containsKey("experience")) {
            basicInfo.put("experience", request.get("experience"));
        }
        if (request.containsKey("health")) {
            basicInfo.put("health", request.get("health"));
        }

        // 创建预约订单
        PsychologistAppointment appointment = new PsychologistAppointment();
        appointment.setOrderNo(generateOrderNo());
        appointment.setUserId(userId);
        appointment.setPsychologistId(psychologistId);
        appointment.setScheduleId(scheduleId);
        appointment.setServiceType(serviceType);
        appointment.setAppointmentTime(schedule.getScheduleDate().atTime(schedule.getStartTime()));
        appointment.setFee(fee);
        appointment.setPayStatus(PsychologistAppointment.PAY_STATUS_UNPAID);
        appointment.setStatus(PsychologistAppointment.STATUS_PENDING);
        appointment.setUserBasicInfo(JSON.toJSONString(basicInfo));
        if (request.containsKey("problems")) {
            appointment.setUserProblems(request.get("problems").toString());
        }
        if (request.containsKey("experience")) {
            appointment.setUserExperience(request.get("experience").toString());
        }
        if (request.containsKey("health")) {
            appointment.setUserHealth(request.get("health").toString());
        }
        appointment.setIsRated(0);
        appointment.setCreateTime(LocalDateTime.now());

        appointmentMapper.insert(appointment);

        // 更新排班预约人数（原子操作，防超卖）
        int affected = scheduleMapper.incrementBookedCount(scheduleId);
        if (affected == 0) {
            throw new RuntimeException("该时段已约满");
        }

        return appointment.getId();
    }

    @Override
    @Transactional
    public String payAppointment(Long appointmentId, Long userId) {
        PsychologistAppointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            return "预约不存在";
        }
        if (!appointment.getUserId().equals(userId)) {
            return "无权操作此预约";
        }
        if (!appointment.getPayStatus().equals(PsychologistAppointment.PAY_STATUS_UNPAID)) {
            return "订单已支付";
        }

        // 虚拟支付：直接标记为已支付
        appointment.setPayStatus(PsychologistAppointment.PAY_STATUS_PAID);
        appointment.setPayTime(LocalDateTime.now());
        appointmentMapper.updateById(appointment);

        return "支付成功";
    }

    @Override
    @Transactional
    public String cancelAppointment(Long appointmentId, Long userId, String cancelReason) {
        PsychologistAppointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            return "预约不存在";
        }
        if (!appointment.getUserId().equals(userId)) {
            return "无权操作此预约";
        }
        // 允许取消的状态：待审核(0)、已确认(1)、待进行(7)
        int status = appointment.getStatus();
        if (status == PsychologistAppointment.STATUS_COMPLETED) {
            return "已完成无法取消";
        }
        if (status == PsychologistAppointment.STATUS_CANCELLED) {
            return "已取消";
        }
        if (status != PsychologistAppointment.STATUS_PENDING &&
            status != PsychologistAppointment.STATUS_CONFIRMED &&
            status != PsychologistAppointment.STATUS_TO_START) {
            return "当前状态不允许取消预约";
        }

        // 如果已支付，需要退款
        if (appointment.getPayStatus().equals(PsychologistAppointment.PAY_STATUS_PAID)) {
            appointment.setPayStatus(PsychologistAppointment.PAY_STATUS_REFUNDED);
        }

        appointment.setStatus(PsychologistAppointment.STATUS_CANCELLED);
        appointment.setRejectReason(cancelReason); // 保存取消原因
        appointment.setUpdateTime(LocalDateTime.now());
        appointmentMapper.updateById(appointment);

        // 恢复排班预约人数（原子操作，防负数）
        if (appointment.getScheduleId() != null) {
            scheduleMapper.decrementBookedCount(appointment.getScheduleId());
        }

        return "取消成功";
    }

    @Override
    public PageResult<Map<String, Object>> getMyAppointments(Integer page, Integer size, Long userId, Integer status) {
        Page<PsychologistAppointment> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PsychologistAppointment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PsychologistAppointment::getUserId, userId);
        if (status != null) {
            wrapper.eq(PsychologistAppointment::getStatus, status);
        }
        wrapper.orderByDesc(PsychologistAppointment::getCreateTime);

        Page<PsychologistAppointment> result = appointmentMapper.selectPage(pageParam, wrapper);

        // 转换为VO
        List<Map<String, Object>> records = new ArrayList<>();
        for (PsychologistAppointment appt : result.getRecords()) {
            records.add(convertToVO(appt));
        }

        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setRecords(records);
        pageResult.setTotal(result.getTotal());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setSize(result.getSize());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    @Override
    public Map<String, Object> getAppointmentDetail(Long appointmentId, Long userId) {
        PsychologistAppointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            return null;
        }
        // 检查权限
        if (!appointment.getUserId().equals(userId) && !appointment.getPsychologistId().equals(userId)) {
            return null;
        }
        return convertToVO(appointment);
    }

    @Override
    public PageResult<Map<String, Object>> getPsychologistAppointments(Integer page, Integer size, Long psychologistId, Integer status) {
        Page<PsychologistAppointment> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PsychologistAppointment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PsychologistAppointment::getPsychologistId, psychologistId);
        if (status != null) {
            wrapper.eq(PsychologistAppointment::getStatus, status);
        }
        wrapper.orderByDesc(PsychologistAppointment::getCreateTime);

        Page<PsychologistAppointment> result = appointmentMapper.selectPage(pageParam, wrapper);

        // 转换为VO
        List<Map<String, Object>> records = new ArrayList<>();
        for (PsychologistAppointment appt : result.getRecords()) {
            records.add(convertToVOForPsychologist(appt));
        }

        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setRecords(records);
        pageResult.setTotal(result.getTotal());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setSize(result.getSize());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    @Override
    @Transactional
    public String handleAppointment(Long appointmentId, Boolean accepted, String videoLink, String rejectReason, Long psychologistId) {
        PsychologistAppointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            return "预约不存在";
        }
        if (!appointment.getPsychologistId().equals(psychologistId)) {
            return "无权操作此预约";
        }
        if (!appointment.getStatus().equals(PsychologistAppointment.STATUS_PENDING)) {
            return "预约状态不允许此操作";
        }

        if (accepted) {
            // 接受预约后，状态变为"已确认"
            // 线上咨询的视频链接由咨询师在"已确认"状态下通过"发送视频链接"功能发送
            appointment.setStatus(PsychologistAppointment.STATUS_CONFIRMED);
        } else {
            appointment.setStatus(PsychologistAppointment.STATUS_REJECTED);
            appointment.setRejectReason(rejectReason);

            // 如果已支付，需要退款
            if (appointment.getPayStatus().equals(PsychologistAppointment.PAY_STATUS_PAID)) {
                appointment.setPayStatus(PsychologistAppointment.PAY_STATUS_REFUNDED);
            }

            // 恢复排班预约人数（原子操作，防负数）
            if (appointment.getScheduleId() != null) {
                scheduleMapper.decrementBookedCount(appointment.getScheduleId());
            }
        }

        appointment.setUpdateTime(LocalDateTime.now());
        appointmentMapper.updateById(appointment);

        return accepted ? "已接受预约" : "已拒绝预约";
    }

    @Override
    @Transactional
    public String sendVideoLink(Long appointmentId, String videoLink, String offlineAddress, String startTime, String endTime, Long psychologistId) {
        PsychologistAppointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            return "预约不存在";
        }
        if (!appointment.getPsychologistId().equals(psychologistId)) {
            return "无权操作此预约";
        }
        // 支持已确认(1)和待进行(7)两种状态
        if (!appointment.getStatus().equals(PsychologistAppointment.STATUS_CONFIRMED) &&
            !appointment.getStatus().equals(PsychologistAppointment.STATUS_TO_START)) {
            return "只有已确认或待进行的预约才能发送视频链接/线下地址";
        }

        // 保存视频链接或线下地址
        if ("OFFLINE".equalsIgnoreCase(appointment.getServiceType()) && StringUtils.hasText(offlineAddress)) {
            appointment.setVideoLink(offlineAddress);
        } else if (StringUtils.hasText(videoLink)) {
            appointment.setVideoLink(videoLink);
        }
        // 保存开始时间
        if (StringUtils.hasText(startTime)) {
            appointment.setStartTime(LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        // 保存结束时间
        if (StringUtils.hasText(endTime)) {
            appointment.setEndTime(LocalDateTime.parse(endTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        // 状态流转到"待进行"
        appointment.setStatus(PsychologistAppointment.STATUS_TO_START);
        appointment.setUpdateTime(LocalDateTime.now());
        appointmentMapper.updateById(appointment);

        return "已发送并进入待进行状态";
    }

    @Override
    @Transactional
    public String startConsultation(Long appointmentId, String startTime, Long psychologistId) {
        PsychologistAppointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            return "预约不存在";
        }
        if (!appointment.getPsychologistId().equals(psychologistId)) {
            return "无权操作此预约";
        }
        if (!appointment.getStatus().equals(PsychologistAppointment.STATUS_TO_START)) {
            return "只有待进行的预约才能开始咨询";
        }

        // 如果有开始时间，则保存
        if (StringUtils.hasText(startTime)) {
            appointment.setStartTime(LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        // 状态变为"进行中"
        appointment.setStatus(PsychologistAppointment.STATUS_IN_PROGRESS);
        appointment.setUpdateTime(LocalDateTime.now());
        appointmentMapper.updateById(appointment);

        // 自动更新心理师为忙碌状态
        psychologistService.checkAndUpdateBusyStatus(psychologistService.getPsychologistUserId(psychologistId));

        return "咨询已开始";
    }

    @Override
    @Transactional
    public String completeAppointment(Long appointmentId, Long psychologistId) {
        PsychologistAppointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            return "预约不存在";
        }
        if (!appointment.getPsychologistId().equals(psychologistId)) {
            return "无权操作此预约";
        }
        if (!appointment.getStatus().equals(PsychologistAppointment.STATUS_IN_PROGRESS)) {
            return "只有进行中的预约才能完成";
        }

        // 保存结束时间（自动记录当前时间）
        appointment.setEndTime(LocalDateTime.now());
        // 状态变为"已完成"
        appointment.setStatus(PsychologistAppointment.STATUS_COMPLETED);
        appointment.setCompleteTime(LocalDateTime.now());
        appointment.setUpdateTime(LocalDateTime.now());
        appointmentMapper.updateById(appointment);

        // 增加咨询次数
        psychologistService.incrementConsultationCount(appointment.getPsychologistId());

        // 更新咨询历史
        updateConsultationHistory(appointment.getUserId(), appointment.getPsychologistId(), appointmentId);

        // 自动更新心理师状态（如果所有咨询都完成了，设置为在线）
        psychologistService.checkAndUpdateBusyStatus(psychologistService.getPsychologistUserId(psychologistId));

        return "咨询已完成";
    }

    @Override
    @Transactional
    public String rateAppointment(Long appointmentId, Integer rating, String content, Integer isAnonymous, Long userId) {
        PsychologistAppointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            return "预约不存在";
        }
        if (!appointment.getUserId().equals(userId)) {
            return "无权操作此预约";
        }
        if (!appointment.getStatus().equals(PsychologistAppointment.STATUS_COMPLETED)) {
            return "只能评价已完成的预约";
        }
        if (appointment.getIsRated() != null && appointment.getIsRated() == 1) {
            return "已评价";
        }

        BigDecimal ratingDecimal = BigDecimal.valueOf(rating);

        // 创建评价记录
        PsychologistRating ratingRecord = new PsychologistRating();
        ratingRecord.setAppointmentId(appointmentId);
        ratingRecord.setUserId(userId);
        ratingRecord.setPsychologistId(appointment.getPsychologistId());
        ratingRecord.setRatingScore(ratingDecimal);
        ratingRecord.setRatingContent(content);
        ratingRecord.setIsAnonymous(isAnonymous != null ? isAnonymous : 0);
        ratingRecord.setCreateTime(LocalDateTime.now());
        ratingMapper.insert(ratingRecord);

        // 更新预约评价
        appointment.setIsRated(1);
        appointment.setRatingScore(ratingDecimal);
        appointment.setRatingContent(content);
        appointment.setRatingTime(LocalDateTime.now());
        appointment.setUpdateTime(LocalDateTime.now());
        // 状态变为"已评价"
        appointment.setStatus(PsychologistAppointment.STATUS_RATED);
        appointmentMapper.updateById(appointment);

        // 更新心理师评分
        psychologistService.updateRating(appointment.getPsychologistId(), ratingDecimal);

        // 生成收入记录
        incomeService.addIncome(appointmentId, appointment.getPsychologistId(), appointment.getFee(), ratingDecimal);

        // 生成平台收入记录
        platformIncomeService.generateConsultationIncome(
                appointmentId,
                appointment.getPsychologistId(),
                appointment.getUserId(),
                appointment.getFee(),
                ratingDecimal);

        return "评价成功";
    }

    @Override
    public String generateOrderNo() {
        String prefix = "PSY";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return prefix + timestamp + uuid;
    }

    // ==================== 私有方法 ====================

    /**
     * 转换为用户端VO
     */
    private Map<String, Object> convertToVO(PsychologistAppointment appointment) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", appointment.getId());
        vo.put("orderNo", appointment.getOrderNo());
        vo.put("psychologistId", appointment.getPsychologistId());
        vo.put("serviceType", appointment.getServiceType());
        vo.put("serviceTypeText", getServiceTypeText(appointment.getServiceType()));
        vo.put("appointmentTime", appointment.getAppointmentTime());
        vo.put("fee", appointment.getFee());
        vo.put("payStatus", appointment.getPayStatus());
        vo.put("payStatusText", getPayStatusText(appointment.getPayStatus()));
        vo.put("status", appointment.getStatus());
        vo.put("statusText", getStatusText(appointment.getStatus()));
        vo.put("rejectReason", appointment.getRejectReason());
        vo.put("videoLink", appointment.getVideoLink());
        vo.put("startTime", appointment.getStartTime());
        vo.put("endTime", appointment.getEndTime());
        vo.put("completeTime", appointment.getCompleteTime());
        vo.put("ratingTime", appointment.getRatingTime());
        vo.put("isRated", appointment.getIsRated());
        vo.put("ratingScore", appointment.getRatingScore());
        vo.put("ratingContent", appointment.getRatingContent());

        // 获取心理师信息
        Psychologist psychologist = psychologistMapper.selectById(appointment.getPsychologistId());
        if (psychologist != null) {
            vo.put("psychologistName", psychologist.getRealName());
            vo.put("psychologistHead", psychologist.getHeadPath());
            // 同时添加前端期望的字段名
            vo.put("psychologistHeadPath", psychologist.getHeadPath());
        }

        return vo;
    }

    /**
     * 转换为心理师端VO
     */
    private Map<String, Object> convertToVOForPsychologist(PsychologistAppointment appointment) {
        Map<String, Object> vo = convertToVO(appointment);

        // 获取用户信息
        User user = userMapper.selectById(appointment.getUserId());
        if (user != null) {
            vo.put("userName", user.getNickname());
            vo.put("userHead", user.getHeadPath());
        }

        // 用户基本情况
        if (StringUtils.hasText(appointment.getUserBasicInfo())) {
            try {
                vo.put("userBasicInfo", JSON.parseObject(appointment.getUserBasicInfo()));
            } catch (Exception e) {
                vo.put("userBasicInfo", appointment.getUserBasicInfo());
            }
        }

        return vo;
    }

    /**
     * 更新咨询历史
     */
    private void updateConsultationHistory(Long userId, Long psychologistId, Long appointmentId) {
        LambdaQueryWrapper<UserPsychologistHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPsychologistHistory::getUserId, userId)
                .eq(UserPsychologistHistory::getPsychologistId, psychologistId);
        UserPsychologistHistory history = historyMapper.selectOne(wrapper);

        if (history == null) {
            history = new UserPsychologistHistory();
            history.setUserId(userId);
            history.setPsychologistId(psychologistId);
            history.setAppointmentCount(1);
            history.setLastAppointmentTime(LocalDateTime.now());
            history.setLastAppointmentId(appointmentId);
            history.setCreateTime(LocalDateTime.now());
            historyMapper.insert(history);
        } else {
            history.setAppointmentCount(history.getAppointmentCount() + 1);
            history.setLastAppointmentTime(LocalDateTime.now());
            history.setLastAppointmentId(appointmentId);
            history.setUpdateTime(LocalDateTime.now());
            historyMapper.updateById(history);
        }
    }

    /**
     * 获取服务类型文本
     */
    private String getServiceTypeText(String type) {
        if (type == null) return "未知";
        switch (type) {
            case "VIDEO": return "视频咨询";
            case "VOICE": return "语音通话";
            case "TEXT": return "文字聊天";
            case "OFFLINE": return "线下咨询";
            default: return type;
        }
    }

    /**
     * 获取支付状态文本
     */
    private String getPayStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待支付";
            case 1: return "已支付";
            case 2: return "已退款";
            default: return "未知";
        }
    }

    /**
     * 获取预约状态文本
     */
    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待审核";
            case 1: return "已确认";
            case 2: return "已拒绝";
            case 3: return "进行中";
            case 4: return "已完成";
            case 5: return "已取消";
            case 6: return "已爽约";
            case 7: return "待进行";
            case 8: return "已评价";
            default: return "未知";
        }
    }
}
