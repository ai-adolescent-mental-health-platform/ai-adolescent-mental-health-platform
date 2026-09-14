package com.xinyuzhilian.aiadolescentmentalhealthsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.annotation.CurrentUserId;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.PageResult;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.Result;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.ConsultationField;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.Psychologist;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistFieldRelation;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistQualification;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistQualificationRelation;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistSchedule;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistProfileAudit;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistWithdraw;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.ConsultationFieldMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistFieldRelationMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistQualificationMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistQualificationRelationMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistProfileAuditMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistWithdrawMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistServiceMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistAppointmentService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistMessage;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistMessageService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistProfileAuditService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistScheduleService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl.PsychologistMessageSseServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 心理咨询师管理端控制器
 *
 * @author AI Developer
 * @since 2026-04-14
 */
@RestController
@RequestMapping("/psychologist/admin")
@RequiredArgsConstructor
@Slf4j
public class PsychologistAdminController {

    private final IPsychologistService psychologistService;
    private final IPsychologistScheduleService scheduleService;
    private final IPsychologistAppointmentService appointmentService;
    private final IPsychologistMessageService messageService;
    private final IPsychologistProfileAuditService profileAuditService;
    private final ConsultationFieldMapper consultationFieldMapper;
    private final PsychologistFieldRelationMapper fieldRelationMapper;
    private final PsychologistQualificationMapper qualificationMapper;
    private final PsychologistQualificationRelationMapper qualificationRelationMapper;
    private final PsychologistProfileAuditMapper profileAuditMapper;
    private final PsychologistWithdrawMapper withdrawMapper;
    private final PsychologistMessageSseServiceImpl sseService;
    private final PsychologistServiceMapper serviceMapper;
    private final com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistRatingMapper ratingMapper;
    private final com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserMapper userMapper;

    /**
     * 获取当前心理咨询师的ID
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> getMyProfile(@CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        Psychologist psychologist = psychologistService.getById(psychologistId);
        Map<String, Object> result = new HashMap<>();
        result.put("psychologistId", psychologistId);
        result.put("psychologist", psychologist);
        return Result.success(result);
    }

    /**
     * 获取工作台统计数据
     */
    @GetMapping("/dashboard/stats")
    public Result<Map<String, Object>> getDashboardStats(@CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        
        Map<String, Object> stats = new HashMap<>();
        
        // 获取心理师基本信息
        Psychologist psychologist = psychologistService.getById(psychologistId);
        if (psychologist != null) {
            stats.put("realName", psychologist.getRealName());
            stats.put("rating", psychologist.getRatingScore());
            stats.put("totalConsultations", psychologist.getConsultationCount());
        }
        
        // 今日日期范围
        LocalDate today = LocalDate.now();

        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();
        
        // 今日预约数
        LambdaQueryWrapper<com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment> todayWrapper = 
                new LambdaQueryWrapper<>();
        todayWrapper.eq(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getPsychologistId, psychologistId)
                .ge(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getAppointmentTime, todayStart)
                .lt(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getAppointmentTime, todayEnd)
                .ne(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getStatus, 
                    com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment.STATUS_CANCELLED);
        long todayAppointments = appointmentService.count(todayWrapper);
        stats.put("todayAppointments", todayAppointments);
        
        // 今日收入（已完成的订单）
        LambdaQueryWrapper<com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment> todayIncomeWrapper = 
                new LambdaQueryWrapper<>();
        todayIncomeWrapper.eq(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getPsychologistId, psychologistId)
                .eq(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getStatus, 
                    com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment.STATUS_COMPLETED)
                .ge(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getCompleteTime, todayStart)
                .lt(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getCompleteTime, todayEnd);
        List<com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment> todayCompleted = 
                appointmentService.list(todayIncomeWrapper);
        BigDecimal todayIncome = todayCompleted.stream()
                .map(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("todayIncome", todayIncome);
        
        // 待处理事项统计
        // 待接受预约 (status=0)
        LambdaQueryWrapper<com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment> pendingWrapper = 
                new LambdaQueryWrapper<>();
        pendingWrapper.eq(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getPsychologistId, psychologistId)
                .eq(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getStatus, 
                    com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment.STATUS_PENDING);
        long pendingAppointments = appointmentService.count(pendingWrapper);
        stats.put("pendingAppointments", pendingAppointments);
        
        // 已确认预约 (status=1)
        LambdaQueryWrapper<com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment> confirmedWrapper = 
                new LambdaQueryWrapper<>();
        confirmedWrapper.eq(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getPsychologistId, psychologistId)
                .eq(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getStatus, 
                    com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment.STATUS_CONFIRMED);
        long confirmedAppointments = appointmentService.count(confirmedWrapper);
        stats.put("confirmedAppointments", confirmedAppointments);
        
        // 待进行预约 (status=7)
        LambdaQueryWrapper<com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment> toStartWrapper = 
                new LambdaQueryWrapper<>();
        toStartWrapper.eq(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getPsychologistId, psychologistId)
                .eq(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getStatus, 
                    com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment.STATUS_TO_START);
        long toStartAppointments = appointmentService.count(toStartWrapper);
        stats.put("toStartAppointments", toStartAppointments);
        
        // 进行中预约 (status=3)
        LambdaQueryWrapper<com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment> inProgressWrapper = 
                new LambdaQueryWrapper<>();
        inProgressWrapper.eq(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getPsychologistId, psychologistId)
                .eq(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment::getStatus, 
                    com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment.STATUS_IN_PROGRESS);
        long inProgressAppointments = appointmentService.count(inProgressWrapper);
        stats.put("inProgressAppointments", inProgressAppointments);
        
        // 待处理提现 (status=0)
        LambdaQueryWrapper<PsychologistWithdraw> withdrawWrapper = 
                new LambdaQueryWrapper<>();
        withdrawWrapper.eq(PsychologistWithdraw::getPsychologistId, psychologistId)
                .eq(PsychologistWithdraw::getStatus, 0);
        long pendingWithdrawals = withdrawMapper.selectCount(withdrawWrapper);
        stats.put("pendingWithdrawals", pendingWithdrawals);
        
        return Result.success(stats);
    }

    /**
     * 获取我的预约用户列表
     */
    @GetMapping("/appointments")
    public Result<PageResult<Map<String, Object>>> getMyAppointments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @CurrentUserId Long userId,
            @RequestParam(required = false) Integer status) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        PageResult<Map<String, Object>> result = appointmentService.getPsychologistAppointments(
                page, size, psychologistId, status);
        return Result.success(result);
    }

    /**
     * 接受/拒绝预约
     */
    @PostMapping("/appointments/{appointmentId}/handle")
    public Result<String> handleAppointment(
            @PathVariable Long appointmentId,
            @RequestParam Boolean accepted,
            @RequestParam(required = false) String videoLink,
            @RequestParam(required = false) String rejectReason,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        try {
            String result = appointmentService.handleAppointment(
                    appointmentId, accepted, videoLink, rejectReason, psychologistId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 发送视频会议链接或线下地址
     */
    @PostMapping("/appointments/{appointmentId}/video-link")
    public Result<String> sendVideoLink(
            @PathVariable Long appointmentId,
            @RequestParam(required = false) String videoLink,
            @RequestParam(required = false) String offlineAddress,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        try {
            String result = appointmentService.sendVideoLink(appointmentId, videoLink, offlineAddress, startTime, endTime, psychologistId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 开始咨询
     */
    @PostMapping("/appointments/{appointmentId}/start")
    public Result<String> startConsultation(
            @PathVariable Long appointmentId,
            @RequestParam(required = false) String startTime,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        try {
            String result = appointmentService.startConsultation(appointmentId, startTime, psychologistId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 完成咨询
     */
    @PostMapping("/appointments/{appointmentId}/complete")
    public Result<String> completeAppointment(
            @PathVariable Long appointmentId,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        try {
            String result = appointmentService.completeAppointment(appointmentId, psychologistId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取我的排班
     * 首次访问时自动删除当天之前无预约的排班，并创建近7天的默认排班数据
     */
    @GetMapping("/schedules")
    public Result<List<Map<String, Object>>> getMySchedules(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        // 自动删除当天之前无预约的排班
        scheduleService.autoDeleteOldSchedules(psychologistId);
        // 自动初始化近7天排班
        scheduleService.autoInitSchedules(psychologistId);
        List<Map<String, Object>> schedules = scheduleService.getSchedulesByDate(psychologistId, startDate, endDate);
        return Result.success(schedules);
    }

    /**
     * 获取排班时段详情（包含已预约人数、最大预约人数、预约列表）
     */
    @GetMapping("/schedule/slot-detail")
    public Result<Map<String, Object>> getScheduleSlotDetail(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate scheduleDate,
            @RequestParam String timeSlot,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        Map<String, Object> detail = scheduleService.getScheduleSlotDetail(psychologistId, scheduleDate, timeSlot);
        return Result.success(detail);
    }

    /**
     * 更新排班状态（仅允许 休息<->可预约 的切换）
     * 必须先设置为休息状态后才能修改其他属性
     */
    @PostMapping("/schedule/status")
    public Result<String> updateScheduleStatus(
            @RequestParam Long scheduleId,
            @RequestParam Integer status,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        try {
            String result = scheduleService.updateScheduleStatus(psychologistId, scheduleId, status);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新排班最大预约人数
     */
    @PutMapping("/schedule")
    public Result<String> updateSchedule(
            @RequestParam Long scheduleId,
            @RequestParam(required = false) Integer maxAppointments,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        try {
            String result = scheduleService.updateSchedule(psychologistId, scheduleId, maxAppointments);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除指定日期之前的所有历史排班（仅删除无预约的记录）
     */
    @DeleteMapping("/schedules/old")
    public Result<Integer> deleteOldSchedules(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate beforeDate,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        int deletedCount = scheduleService.deleteOldSchedulesBefore(psychologistId, beforeDate);
        return Result.success(deletedCount);
    }

    /**
     * 保存排班
     */
    @PostMapping("/schedule")
    public Result<String> saveSchedule(
            @RequestBody PsychologistSchedule schedule,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        try {
            String result = scheduleService.saveSchedule(schedule, psychologistId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量保存排班
     */
    @PostMapping("/schedules/batch")
    public Result<String> saveSchedules(
            @RequestBody List<PsychologistSchedule> schedules,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        try {
            String result = scheduleService.saveSchedules(schedules, psychologistId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除排班
     */
    @DeleteMapping("/schedule/{scheduleId}")
    public Result<String> deleteSchedule(
            @PathVariable Long scheduleId,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        try {
            String result = scheduleService.deleteSchedule(scheduleId, psychologistId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新个人资料
     */
    @PutMapping("/profile")
    public Result<String> updateProfile(
            @RequestBody Map<String, Object> request,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        try {
            Psychologist psychologist = psychologistService.getById(psychologistId);
            if (psychologist == null) {
                return Result.error("心理咨询师不存在");
            }

            if (request.containsKey("headPath")) {
                psychologist.setHeadPath((String) request.get("headPath"));
            }
            if (request.containsKey("realName")) {
                psychologist.setRealName((String) request.get("realName"));
            }
            if (request.containsKey("sex")) {
                Object sexObj = request.get("sex");
                if (sexObj instanceof Number) {
                    psychologist.setSex(((Number) sexObj).intValue());
                }
            }
            if (request.containsKey("introduction")) {
                psychologist.setIntroduction((String) request.get("introduction"));
            }
            if (request.containsKey("offlineRegion")) {
                psychologist.setOfflineRegion((String) request.get("offlineRegion"));
            }
            if (request.containsKey("offlineAddress")) {
                psychologist.setOfflineAddress((String) request.get("offlineAddress"));
            }
            if (request.containsKey("languages")) {
                psychologist.setLanguages((String) request.get("languages"));
            }
            if (request.containsKey("yearsExperience")) {
                Object yearsObj = request.get("yearsExperience");
                if (yearsObj instanceof Number) {
                    psychologist.setYearsExperience(((Number) yearsObj).intValue());
                }
            }
            if (request.containsKey("educationBackground")) {
                psychologist.setEducationBackground((String) request.get("educationBackground"));
            }
            if (request.containsKey("trainingExperience")) {
                psychologist.setTrainingExperience((String) request.get("trainingExperience"));
            }

            psychologistService.updateById(psychologist);
            return Result.success("资料更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取当前心理咨询师的完整资料（用于个人中心）
     */
    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile(@CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        Psychologist psychologist = psychologistService.getById(psychologistId);
        if (psychologist == null) {
            return Result.error("心理咨询师不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", psychologist.getId());
        result.put("headPath", psychologist.getHeadPath());
        result.put("realName", psychologist.getRealName());
        result.put("sex", psychologist.getSex());
        result.put("introduction", psychologist.getIntroduction());
        result.put("offlineRegion", psychologist.getOfflineRegion());
        result.put("offlineAddress", psychologist.getOfflineAddress());
        result.put("languages", psychologist.getLanguages());
        result.put("yearsExperience", psychologist.getYearsExperience());
        result.put("educationBackground", psychologist.getEducationBackground());
        result.put("trainingExperience", psychologist.getTrainingExperience());
        result.put("consultationPrice", psychologist.getConsultationPrice());
        result.put("offlinePrice", psychologist.getOfflinePrice());

        // 获取擅长领域
        List<PsychologistFieldRelation> fields = fieldRelationMapper.selectList(
                new LambdaQueryWrapper<PsychologistFieldRelation>()
                        .eq(PsychologistFieldRelation::getPsychologistId, psychologistId));
        result.put("fields", fields);

        // 获取资质认证
        List<PsychologistQualificationRelation> qualifications = qualificationRelationMapper.selectList(
                new LambdaQueryWrapper<PsychologistQualificationRelation>()
                        .eq(PsychologistQualificationRelation::getPsychologistId, psychologistId));
        result.put("qualifications", qualifications);

        return Result.success(result);
    }

    /**
     * 获取擅长领域选项列表
     */
    @GetMapping("/fields/options")
    public Result<List<ConsultationField>> getFieldOptions() {
        List<ConsultationField> fields = consultationFieldMapper.selectList(
                new LambdaQueryWrapper<ConsultationField>()
                        .eq(ConsultationField::getStatus, 1)
                        .orderByAsc(ConsultationField::getSortOrder));
        return Result.success(fields);
    }

    /**
     * 获取资质认证选项列表
     */
    @GetMapping("/qualifications/options")
    public Result<List<PsychologistQualification>> getQualificationOptions() {
        List<PsychologistQualification> qualifications = qualificationMapper.selectList(
                new LambdaQueryWrapper<PsychologistQualification>()
                        .eq(PsychologistQualification::getStatus, 1)
                        .orderByAsc(PsychologistQualification::getSortOrder));
        return Result.success(qualifications);
    }

    /**
     * 获取心理咨询师的服务列表（从数据库读取）
     */
    @GetMapping("/{psychologistId}/services")
    public Result<List<Map<String, Object>>> getServices(@PathVariable Long psychologistId) {
        Psychologist psychologist = psychologistService.getById(psychologistId);
        if (psychologist == null) {
            return Result.error("心理咨询师不存在");
        }

        List<Map<String, Object>> result = new ArrayList<>();
        String onlinePriceStr = psychologist.getConsultationPrice() != null ? psychologist.getConsultationPrice().toPlainString() : "0";
        String offlinePriceStr = psychologist.getOfflinePrice() != null ? psychologist.getOfflinePrice().toPlainString() : "0";

        // 查询数据库中的服务记录
        LambdaQueryWrapper<PsychologistService> serviceWrapper = new LambdaQueryWrapper<>();
        serviceWrapper.eq(PsychologistService::getPsychologistId, psychologistId);
        List<PsychologistService> dbServices = serviceMapper.selectList(serviceWrapper);

        // 构建服务状态映射（统一使用大写key）
        Map<String, Integer> serviceStatusMap = new HashMap<>();
        for (PsychologistService s : dbServices) {
            if (s.getServiceType() != null) {
                serviceStatusMap.put(s.getServiceType().toUpperCase(), s.getStatus());
            }
        }

        // video服务
        Map<String, Object> videoService = new HashMap<>();
        videoService.put("serviceType", "VIDEO");
        videoService.put("price", parsePrice(onlinePriceStr));
        videoService.put("status", serviceStatusMap.getOrDefault("VIDEO", 1));
        result.add(videoService);

        // voice服务
        Map<String, Object> voiceService = new HashMap<>();
        voiceService.put("serviceType", "VOICE");
        voiceService.put("price", parsePrice(onlinePriceStr));
        voiceService.put("status", serviceStatusMap.getOrDefault("VOICE", 1));
        result.add(voiceService);

        // text服务（不计费）
        Map<String, Object> textService = new HashMap<>();
        textService.put("serviceType", "TEXT");
        textService.put("price", BigDecimal.ZERO);
        textService.put("status", serviceStatusMap.getOrDefault("TEXT", 1));
        result.add(textService);

        // offline服务
        Map<String, Object> offlineService = new HashMap<>();
        offlineService.put("serviceType", "OFFLINE");
        offlineService.put("price", parsePrice(offlinePriceStr));
        offlineService.put("status", serviceStatusMap.getOrDefault("OFFLINE", 0));
        result.add(offlineService);

        return Result.success(result);
    }

    private BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(priceStr);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 保存或更新服务（启用/禁用服务）
     */
    @PostMapping("/{psychologistId}/services/{serviceType}")
    public Result<String> saveService(
            @PathVariable Long psychologistId,
            @PathVariable String serviceType,
            @RequestBody Map<String, Object> request,
            @CurrentUserId Long userId) {
        // 验证权限
        Long pId = psychologistService.getPsychologistIdByUserId(userId);
        if (pId == null || !pId.equals(psychologistId)) {
            return Result.error("无权限操作");
        }
        // 验证咨询师存在
        Psychologist psychologist = psychologistService.getById(psychologistId);
        if (psychologist == null) {
            return Result.error("心理咨询师不存在");
        }

        // 获取请求参数
        Integer status = request.containsKey("status") ? ((Number) request.get("status")).intValue() : null;

        // 查询或创建服务记录
        LambdaQueryWrapper<PsychologistService> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PsychologistService::getPsychologistId, psychologistId)
                .eq(PsychologistService::getServiceType, serviceType.toUpperCase());
        PsychologistService service = serviceMapper.selectOne(wrapper);

        if (service == null) {
            // 创建新记录
            service = new PsychologistService();
            service.setPsychologistId(psychologistId);
            service.setServiceType(serviceType.toUpperCase());
            service.setStatus(status != null ? status : 1);
            service.setCreateTime(LocalDateTime.now());
            service.setUpdateTime(LocalDateTime.now());
            serviceMapper.insert(service);
        } else {
            // 更新现有记录
            if (status != null) {
                service.setStatus(status);
            }
            service.setUpdateTime(LocalDateTime.now());
            serviceMapper.updateById(service);
        }

        return Result.success("保存成功");
    }

    /**
     * 提交价格修改审核申请
     */
    @PostMapping("/profile/price/audit/apply")
    public Result<String> applyPriceAudit(
            @RequestBody Map<String, Object> request,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }

        // 获取新价格、理由和证明材料
        Object newPriceObj = request.get("newPrice");
        String reason = request.containsKey("reason") ? (String) request.get("reason") : null;
        String proofUrls = request.containsKey("proofUrls") ? (String) request.get("proofUrls") : null;

        if (newPriceObj == null) {
            return Result.error("请填写新价格");
        }

        double newPrice;
        try {
            newPrice = ((Number) newPriceObj).doubleValue();
        } catch (Exception e) {
            return Result.error("价格格式不正确");
        }

        if (reason == null || reason.trim().isEmpty()) {
            return Result.error("请填写修改理由");
        }

        try {
            // 获取当前价格
            Psychologist psychologist = psychologistService.getById(psychologistId);
            if (psychologist == null) {
                return Result.error("心理咨询师不存在");
            }
            String oldPrice = psychologist.getConsultationPrice() != null ? psychologist.getConsultationPrice().toPlainString() : "0";

            // 提交价格变更审核
            Map<String, Object> auditResult = profileAuditService.submitProfileChange(
                    psychologistId,
                    PsychologistProfileAudit.FIELD_PRICE,
                    oldPrice,
                    String.valueOf(newPrice),
                    proofUrls,
                    reason
            );

            if ((Boolean) auditResult.get("success")) {
                return Result.success((String) auditResult.get("message"));
            } else {
                return Result.error((String) auditResult.get("message"));
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 提交线下价格修改审核申请
     */
    @PostMapping("/profile/offline-price/audit/apply")
    public Result<String> applyOfflinePriceAudit(
            @RequestBody Map<String, Object> request,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }

        // 获取新价格、理由和证明材料
        Object newPriceObj = request.get("newPrice");
        String reason = request.containsKey("reason") ? (String) request.get("reason") : null;
        String proofUrls = request.containsKey("proofUrls") ? (String) request.get("proofUrls") : null;

        if (newPriceObj == null) {
            return Result.error("请填写新价格");
        }

        double newPrice;
        try {
            newPrice = ((Number) newPriceObj).doubleValue();
        } catch (Exception e) {
            return Result.error("价格格式不正确");
        }

        if (reason == null || reason.trim().isEmpty()) {
            return Result.error("请填写修改理由");
        }

        try {
            // 获取当前价格
            Psychologist psychologist = psychologistService.getById(psychologistId);
            if (psychologist == null) {
                return Result.error("心理咨询师不存在");
            }
            String oldPrice = psychologist.getOfflinePrice() != null ? psychologist.getOfflinePrice().toPlainString() : "0";

            // 提交价格变更审核
            Map<String, Object> auditResult = profileAuditService.submitProfileChange(
                    psychologistId,
                    PsychologistProfileAudit.FIELD_OFFLINE_PRICE,
                    oldPrice,
                    String.valueOf(newPrice),
                    proofUrls,
                    reason
            );

            if ((Boolean) auditResult.get("success")) {
                return Result.success((String) auditResult.get("message"));
            } else {
                return Result.error((String) auditResult.get("message"));
            }
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 提交资质审核申请
     */
    @PostMapping("/profile/audit/apply")
    public Result<String> applyProfileAudit(
            @RequestBody Map<String, Object> request,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }

        // 获取审核理由和证明材料
        String reason = request.containsKey("reason") ? (String) request.get("reason") : null;
        String proofUrls = request.containsKey("proofUrls") ? (String) request.get("proofUrls") : null;

        try {
            // 处理擅长领域
            if (request.containsKey("fieldIds")) {
                List<Integer> fieldIds = (List<Integer>) request.get("fieldIds");

                // 提交审核
                profileAuditService.submitProfileChange(
                        psychologistId,
                        PsychologistProfileAudit.FIELD_FIELD,
                        "",
                        String.join(",", fieldIds.stream().map(String::valueOf).collect(Collectors.toList())),
                        proofUrls,
                        reason
                );
            }

            // 处理资质认证
            if (request.containsKey("qualificationIds")) {
                List<Integer> qualificationIds = (List<Integer>) request.get("qualificationIds");

                // 提交审核
                profileAuditService.submitProfileChange(
                        psychologistId,
                        PsychologistProfileAudit.FIELD_QUALIFICATION,
                        "",
                        String.join(",", qualificationIds.stream().map(String::valueOf).collect(Collectors.toList())),
                        proofUrls,
                        reason
                );
            }

            return Result.success("已提交审核申请");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取审核记录列表
     */
    @GetMapping("/profile/audit/list")
    public Result<PageResult<Map<String, Object>>> getAuditList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }

        PageResult<Map<String, Object>> result = profileAuditService.getAuditListByPsychologist(
                psychologistId, page, size);
        return Result.success(result);
    }

    /**
     * 获取聊天记录
     */
    @GetMapping("/messages/{appointmentId}")
    public Result<List<Map<String, Object>>> getMessages(
            @PathVariable Long appointmentId,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        List<PsychologistMessage> messages = messageService.getMessageHistory(appointmentId);
        List<Map<String, Object>> result = messages.stream().map(msg -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", msg.getId());
            map.put("content", msg.getContent());
            map.put("contentType", msg.getContentType());
            map.put("createTime", msg.getCreateTime());
            map.put("senderType", msg.getSenderId().equals(psychologistId) ? "psychologist" : "user");
            return map;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    /**
     * 发送消息
     */
    @PostMapping("/messages/send")
    public Result<Map<String, Object>> sendMessage(
            @RequestBody Map<String, Object> request,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        try {
            Long appointmentId = Long.valueOf(request.get("appointmentId").toString());
            String content = request.get("content").toString();
            Integer contentType = request.containsKey("type") ?
                    ("image".equals(request.get("type")) ? 1 : 0) : 0;

            // 获取用户ID（接收者）
            com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment appointment =
                    appointmentService.getById(appointmentId);
            if (appointment == null) {
                return Result.error("预约不存在");
            }
            Long receiverId = appointment.getUserId();

            PsychologistMessage message = messageService.sendMessage(
                    appointmentId, psychologistId, receiverId, content, contentType);

            // 通过SSE广播消息给订阅者（用户端）
            try {
                sseService.broadcastToAppointment(appointmentId, message);
                sseService.broadcastToPsychologist(appointment.getPsychologistId(), message);
            } catch (Exception e) {
                log.error("广播消息失败: {}", e.getMessage());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("id", message.getId());
            result.put("content", message.getContent());
            result.put("contentType", message.getContentType());
            result.put("createTime", message.getCreateTime());
            result.put("senderType", "psychologist");
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取预约详情（包含评价信息和收入计算）
     * 已评价状态返回评价信息和收入抽成
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/appointments/{appointmentId}/detail")
    public Result<Map<String, Object>> getAppointmentDetail(
            @PathVariable Long appointmentId,
            @CurrentUserId Long userId) {
        Long psychologistId = psychologistService.getPsychologistIdByUserId(userId);
        if (psychologistId == null) {
            return Result.error("您还不是心理咨询师");
        }
        
        // 获取预约信息
        com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistAppointment appointment = 
                appointmentService.getById(appointmentId);
        if (appointment == null) {
            return Result.error("预约不存在");
        }
        
        // 验证权限
        if (!appointment.getPsychologistId().equals(psychologistId)) {
            return Result.error("无权限查看此预约");
        }
        
        Map<String, Object> result = new HashMap<>();
        
        // 预约基本信息
        result.put("id", appointment.getId());
        result.put("orderNo", appointment.getOrderNo());
        result.put("userId", appointment.getUserId());
        result.put("psychologistId", appointment.getPsychologistId());
        result.put("serviceType", appointment.getServiceType());
        result.put("serviceTypeText", getServiceTypeText(appointment.getServiceType()));
        result.put("appointmentTime", appointment.getAppointmentTime());
        result.put("fee", appointment.getFee());
        result.put("payStatus", appointment.getPayStatus());
        result.put("payStatusText", getPayStatusText(appointment.getPayStatus()));
        result.put("status", appointment.getStatus());
        result.put("statusText", getStatusText(appointment.getStatus()));
        result.put("rejectReason", appointment.getRejectReason());
        result.put("videoLink", appointment.getVideoLink());
        result.put("startTime", appointment.getStartTime());
        result.put("endTime", appointment.getEndTime());
        result.put("completeTime", appointment.getCompleteTime());
        result.put("userBasicInfo", appointment.getUserBasicInfo());
        result.put("userProblems", appointment.getUserProblems());
        result.put("createTime", appointment.getCreateTime());
        result.put("updateTime", appointment.getUpdateTime());
        result.put("isRated", appointment.getIsRated());
        
        // 获取用户信息
        com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.User user = userMapper.selectById(appointment.getUserId());
        if (user != null) {
            result.put("userName", user.getNickname() != null ? user.getNickname() : "匿名用户");
            result.put("userHead", user.getHeadPath());
            result.put("userPhone", user.getPhone());
        }
        
        // 如果已评价，获取评价信息和收入计算
        if (appointment.getIsRated() != null && appointment.getIsRated() == 1) {
            // 获取评价信息
            LambdaQueryWrapper<com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistRating> ratingWrapper = 
                    new LambdaQueryWrapper<>();
            ratingWrapper.eq(com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistRating::getAppointmentId, appointmentId);
            com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistRating rating = ratingMapper.selectOne(ratingWrapper);
            
            if (rating != null) {
                result.put("ratingScore", rating.getRatingScore());
                result.put("ratingContent", rating.getRatingContent());
                result.put("isAnonymous", rating.getIsAnonymous());
                result.put("ratingTime", rating.getCreateTime());
                
                // 计算收入抽成
                if (appointment.getFee() != null) {
                    BigDecimal fee = appointment.getFee();
                    BigDecimal ratingScoreBD = rating.getRatingScore();
                    double ratingScore = ratingScoreBD != null ? ratingScoreBD.doubleValue() : 0;
                    
                    // 心理咨询收入计算规则
                    double commissionRate;
                    if (ratingScore <= 1.5) {
                        commissionRate = 0.60;  // 抽成60%
                    } else if (ratingScore <= 3) {
                        commissionRate = 0.45;  // 抽成45%
                    } else if (ratingScore <= 4.5) {
                        commissionRate = 0.30;  // 抽成30%
                    } else {
                        commissionRate = 0.15;  // 抽成15%
                    }
                    
                    BigDecimal commissionAmount = fee.multiply(BigDecimal.valueOf(commissionRate));
                    BigDecimal psychologistIncome = fee.subtract(commissionAmount);
                    
                    result.put("commissionRate", commissionRate);
                    result.put("commissionAmount", commissionAmount);
                    result.put("psychologistIncome", psychologistIncome);
                    result.put("totalFee", fee);
                }
            }
        }
        
        return Result.success(result);
    }
    
    private String getServiceTypeText(String serviceType) {
        if (serviceType == null) return "-";
        switch (serviceType.toLowerCase()) {
            case "video": return "线上咨询";
            case "offline": return "线下面询";
            case "text": return "图文咨询";
            case "voice": return "语音咨询";
            default: return serviceType;
        }
    }
    
    private String getPayStatusText(Integer payStatus) {
        if (payStatus == null) return "-";
        switch (payStatus) {
            case 0: return "待支付";
            case 1: return "支付中";
            case 2: return "已支付";
            default: return String.valueOf(payStatus);
        }
    }
    
    private String getStatusText(Integer status) {
        if (status == null) return "-";
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
            default: return String.valueOf(status);
        }
    }
}
