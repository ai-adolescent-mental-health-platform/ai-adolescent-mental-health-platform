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
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.User;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.ConsultationFieldMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistFieldRelationMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistQualificationMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistQualificationRelationMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistIncomeService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistProfileAuditService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 心理咨询师管理端控制器（超级管理员）
 *
 * @author AI Developer
 * @since 2026-04-14
 */
@RestController
@RequestMapping("/admin/psychologist")
@RequiredArgsConstructor
public class AdminPsychologistController {

    private final IPsychologistService psychologistService;
    private final IPsychologistIncomeService incomeService;
    private final IPsychologistProfileAuditService profileAuditService;
    private final UserMapper userMapper;
    private final ConsultationFieldMapper consultationFieldMapper;
    private final PsychologistFieldRelationMapper fieldRelationMapper;
    private final PsychologistQualificationMapper qualificationMapper;
    private final PsychologistQualificationRelationMapper qualificationRelationMapper;

    /**
     * 获取心理咨询师列表（管理端）
     */
    @GetMapping("/list")
    public Result<PageResult<Map<String, Object>>> getPsychologistList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        // 管理端不限制状态，查询所有
        PageResult<Map<String, Object>> result = psychologistService.listPsychologistsForAdmin(
                page, size, keyword, status);
        return Result.success(result);
    }

    /**
     * 获取心理咨询师详情（管理端）
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getPsychologistDetail(@PathVariable Long id) {
        Psychologist psychologist = psychologistService.getById(id);
        if (psychologist == null) {
            return Result.error("心理咨询师不存在");
        }

        Map<String, Object> detail = psychologistService.getPsychologistDetail(id, null);

        // 获取关联用户信息并扁平化
        if (psychologist.getUserId() != null) {
            User user = userMapper.selectById(psychologist.getUserId());
            if (user != null) {
                user.setPassword(null); // 不返回密码
                detail.put("user", user);
                // 扁平化用户字段，方便前端直接使用
                detail.put("phone", user.getPhone());
                detail.put("userNickname", user.getNickname());
                detail.put("userUsername", user.getUsername());
                detail.put("userEmail", user.getEmail());
            }
        }

        // 统一字段名称（兼容前端）
        detail.put("rating", psychologist.getRatingScore()); // 前端期望 rating
        detail.put("bio", psychologist.getIntroduction());   // 前端期望 bio
        detail.put("status", psychologist.getStatus());
        detail.put("consultationPrice", psychologist.getConsultationPrice());
        detail.put("offlinePrice", psychologist.getOfflinePrice());

        return Result.success(detail);
    }

    /**
     * 添加心理咨询师
     */
    @PostMapping
    public Result<String> addPsychologist(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            String realName = (String) request.get("realName");

            // 检查用户是否已绑定心理咨询师
            Long existingId = psychologistService.getPsychologistIdByUserId(userId);
            if (existingId != null) {
                return Result.error("该用户已经是心理咨询师");
            }

            // 创建心理咨询师
            Psychologist psychologist = new Psychologist();
            psychologist.setUserId(userId);
            psychologist.setRealName(realName);

            if (request.containsKey("sex") && request.get("sex") != null) {
                Object sexObj = request.get("sex");
                if (sexObj instanceof Number) {
                    psychologist.setSex(((Number) sexObj).intValue());
                }
            }

            if (request.containsKey("headPath") && request.get("headPath") != null) {
                psychologist.setHeadPath((String) request.get("headPath"));
            }

            if (request.containsKey("yearsExperience") && request.get("yearsExperience") != null) {
                Object yearsObj = request.get("yearsExperience");
                if (yearsObj instanceof Number) {
                    psychologist.setYearsExperience(((Number) yearsObj).intValue());
                }
            }

            if (request.containsKey("consultationPrice") && request.get("consultationPrice") != null) {
                Object priceObj = request.get("consultationPrice");
                if (priceObj instanceof Number) {
                    psychologist.setConsultationPrice(java.math.BigDecimal.valueOf(((Number) priceObj).doubleValue()));
                }
            }

            // 处理线下咨询价格
            if (request.containsKey("offlinePrice") && request.get("offlinePrice") != null) {
                Object priceObj = request.get("offlinePrice");
                if (priceObj instanceof Number) {
                    psychologist.setOfflinePrice(java.math.BigDecimal.valueOf(((Number) priceObj).doubleValue()));
                }
            }

            // 默认启用
            psychologist.setStatus(1);
            if (request.containsKey("status") && request.get("status") != null) {
                Object statusObj = request.get("status");
                if (statusObj instanceof Number) {
                    psychologist.setStatus(((Number) statusObj).intValue());
                }
            }

            psychologistService.savePsychologist(psychologist);

            // 处理专业认证关联
            if (request.containsKey("qualificationIds") && request.get("qualificationIds") != null) {
                List<Integer> qualificationIds = (List<Integer>) request.get("qualificationIds");
                for (Integer qualificationId : qualificationIds) {
                    PsychologistQualificationRelation relation = new PsychologistQualificationRelation();
                    relation.setPsychologistId(psychologist.getId());
                    relation.setQualificationId(qualificationId);
                    relation.setIsVerified(1); // 管理员添加的直接认证
                    qualificationRelationMapper.insert(relation);
                }
            }

            // 处理擅长领域关联
            if (request.containsKey("fieldIds") && request.get("fieldIds") != null) {
                List<Integer> fieldIds = (List<Integer>) request.get("fieldIds");
                for (Integer fieldId : fieldIds) {
                    PsychologistFieldRelation fieldRelation = new PsychologistFieldRelation();
                    fieldRelation.setPsychologistId(psychologist.getId());
                    fieldRelation.setFieldId(fieldId);
                    fieldRelationMapper.insert(fieldRelation);
                }
            }

            // 更新用户表中的心理咨询师标记
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setIsPsychologist(1);
                userMapper.updateById(user);
            }

            return Result.success("添加成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新心理咨询师
     */
    @PutMapping("/{id}")
    public Result<String> updatePsychologist(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            Psychologist psychologist = psychologistService.getById(id);
            if (psychologist == null) {
                return Result.error("心理咨询师不存在");
            }

            if (request.containsKey("realName") && request.get("realName") != null) {
                psychologist.setRealName((String) request.get("realName"));
            }

            if (request.containsKey("sex") && request.get("sex") != null) {
                Object sexObj = request.get("sex");
                if (sexObj instanceof Number) {
                    psychologist.setSex(((Number) sexObj).intValue());
                }
            }

            if (request.containsKey("headPath") && request.get("headPath") != null) {
                psychologist.setHeadPath((String) request.get("headPath"));
            }

            if (request.containsKey("yearsExperience") && request.get("yearsExperience") != null) {
                Object yearsObj = request.get("yearsExperience");
                if (yearsObj instanceof Number) {
                    psychologist.setYearsExperience(((Number) yearsObj).intValue());
                }
            }

            if (request.containsKey("consultationPrice") && request.get("consultationPrice") != null) {
                Object priceObj = request.get("consultationPrice");
                if (priceObj instanceof Number) {
                    psychologist.setConsultationPrice(java.math.BigDecimal.valueOf(((Number) priceObj).doubleValue()));
                }
            }

            if (request.containsKey("offlinePrice") && request.get("offlinePrice") != null) {
                Object priceObj = request.get("offlinePrice");
                if (priceObj instanceof Number) {
                    psychologist.setOfflinePrice(java.math.BigDecimal.valueOf(((Number) priceObj).doubleValue()));
                }
            }

            if (request.containsKey("status") && request.get("status") != null) {
                Object statusObj = request.get("status");
                if (statusObj instanceof Number) {
                    psychologist.setStatus(((Number) statusObj).intValue());
                }
            }

            psychologistService.updateById(psychologist);

            // 处理专业认证关联更新
            if (request.containsKey("qualificationIds") && request.get("qualificationIds") != null) {
                // 删除旧的关联
                qualificationRelationMapper.delete(
                        new LambdaQueryWrapper<PsychologistQualificationRelation>()
                                .eq(PsychologistQualificationRelation::getPsychologistId, id));

                // 添加新的关联
                List<Integer> qualificationIds = (List<Integer>) request.get("qualificationIds");
                for (Integer qualificationId : qualificationIds) {
                    PsychologistQualificationRelation relation = new PsychologistQualificationRelation();
                    relation.setPsychologistId(id);
                    relation.setQualificationId(qualificationId);
                    relation.setIsVerified(1);
                    qualificationRelationMapper.insert(relation);
                }
            }

            // 处理擅长领域关联更新
            if (request.containsKey("fieldIds") && request.get("fieldIds") != null) {
                // 删除旧的擅长领域关联
                fieldRelationMapper.delete(
                        new LambdaQueryWrapper<PsychologistFieldRelation>()
                                .eq(PsychologistFieldRelation::getPsychologistId, id));

                // 添加新的擅长领域关联
                List<Integer> fieldIds = (List<Integer>) request.get("fieldIds");
                for (Integer fieldId : fieldIds) {
                    PsychologistFieldRelation fieldRelation = new PsychologistFieldRelation();
                    fieldRelation.setPsychologistId(id);
                    fieldRelation.setFieldId(fieldId);
                    fieldRelationMapper.insert(fieldRelation);
                }
            }

            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除心理咨询师
     */
    @DeleteMapping("/{id}")
    public Result<String> deletePsychologist(@PathVariable Long id) {
        try {
            Psychologist psychologist = psychologistService.getById(id);
            if (psychologist == null) {
                return Result.error("心理咨询师不存在");
            }

            // 删除心理咨询师
            psychologistService.removeById(id);

            // 更新用户表中的心理咨询师标记
            if (psychologist.getUserId() != null) {
                User user = userMapper.selectById(psychologist.getUserId());
                if (user != null) {
                    user.setIsPsychologist(0);
                    userMapper.updateById(user);
                }
            }

            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 启用/禁用心理咨询师
     */
    @PostMapping("/{id}/status")
    public Result<String> updateStatus(
            @PathVariable Long id,
            @RequestParam Boolean enabled) {
        try {
            Psychologist psychologist = new Psychologist();
            psychologist.setId(id);
            psychologist.setStatus(enabled ? Psychologist.STATUS_ENABLED : Psychologist.STATUS_DISABLED);
            psychologistService.updateById(psychologist);
            return Result.success(enabled ? "已启用" : "已禁用");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取平台收入统计
     */
    @GetMapping("/income/stats")
    public Result<Map<String, Object>> getPlatformIncomeStats() {
        Map<String, Object> stats = incomeService.getPlatformIncomeStats();
        return Result.success(stats);
    }

    /**
     * 获取咨询师收入明细列表（管理端）
     */
    @GetMapping("/income/list")
    public Result<PageResult<Map<String, Object>>> getIncomeList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long psychologistId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        PageResult<Map<String, Object>> result = incomeService.getAdminIncomeList(page, size, psychologistId, startDate, endDate);
        return Result.success(result);
    }

    /**
     * 获取咨询师余额列表（管理端）
     */
    @GetMapping("/balance/list")
    public Result<PageResult<Map<String, Object>>> getBalanceList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PageResult<Map<String, Object>> result = incomeService.getBalanceList(page, size);
        return Result.success(result);
    }

    /**
     * 获取咨询师提现列表（管理端）
     */
    @GetMapping("/withdraw/list")
    public Result<PageResult<Map<String, Object>>> getWithdrawList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
        PageResult<Map<String, Object>> result = incomeService.getAdminWithdrawList(page, size, status);
        return Result.success(result);
    }

    /**
     * 审核提现申请（管理端）
     */
    @PostMapping("/withdraw/{id}/audit")
    public Result<String> auditWithdraw(
            @PathVariable Long id,
            @RequestParam Integer status,
            @CurrentUserId Long adminId,
            @RequestParam(required = false) String remark) {
        try {
            String result = incomeService.processWithdraw(id, status == 1, remark);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    // ========== 资料审核管理 ==========

    /**
     * 获取待审核的资料变更列表（管理端）
     */
    @GetMapping("/audit/list")
    public Result<PageResult<Map<String, Object>>> getAuditList(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PageResult<Map<String, Object>> result = profileAuditService.getPendingAudits(status, page, size);
        return Result.success(result);
    }

    /**
     * 获取审核详情（管理端）
     */
    @GetMapping("/audit/{id}")
    public Result<Map<String, Object>> getAuditDetail(@PathVariable Long id) {
        Map<String, Object> detail = profileAuditService.getAuditDetail(id);
        if (detail == null) {
            return Result.error("审核记录不存在");
        }
        return Result.success(detail);
    }

    /**
     * 审核通过（管理端）
     */
    @PostMapping("/audit/{id}/approve")
    public Result<String> approveAudit(
            @PathVariable Long id,
            @CurrentUserId Long adminId,
            @RequestParam(required = false) String remark) {
        Map<String, Object> result = profileAuditService.approveAudit(id, adminId, remark);
        if ((boolean) result.get("success")) {
            return Result.success((String) result.get("message"));
        }
        return Result.error((String) result.get("message"));
    }

    /**
     * 审核拒绝（管理端）
     */
    @PostMapping("/audit/{id}/reject")
    public Result<String> rejectAudit(
            @PathVariable Long id,
            @CurrentUserId Long adminId,
            @RequestParam(required = false) String remark) {
        Map<String, Object> result = profileAuditService.rejectAudit(id, adminId, remark);
        if ((boolean) result.get("success")) {
            return Result.success((String) result.get("message"));
        }
        return Result.error((String) result.get("message"));
    }

    // ========== 擅长领域关联管理 ==========

    /**
     * 获取心理咨询师的擅长领域列表
     */
    @GetMapping("/{psychologistId}/fields")
    public Result<List<Map<String, Object>>> getPsychologistFields(@PathVariable Long psychologistId) {
        List<PsychologistFieldRelation> relations = fieldRelationMapper.selectList(
                new LambdaQueryWrapper<PsychologistFieldRelation>()
                        .eq(PsychologistFieldRelation::getPsychologistId, psychologistId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (PsychologistFieldRelation relation : relations) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", relation.getId());
            item.put("psychologistId", relation.getPsychologistId());
            item.put("fieldId", relation.getFieldId());
            item.put("subTags", relation.getSubTags());
            item.put("createTime", relation.getCreateTime());

            // 获取领域信息
            ConsultationField field = consultationFieldMapper.selectById(relation.getFieldId());
            if (field != null) {
                item.put("fieldName", field.getName());
                item.put("fieldCode", field.getCode());
                item.put("fieldIcon", field.getIcon());
            }
            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 添加擅长领域
     */
    @PostMapping("/{psychologistId}/fields")
    public Result<String> addPsychologistField(
            @PathVariable Long psychologistId,
            @RequestBody Map<String, Object> request) {
        try {
            Integer fieldId = Integer.valueOf(request.get("fieldId").toString());

            // 检查是否已存在
            PsychologistFieldRelation existingField = fieldRelationMapper.selectOne(
                    new LambdaQueryWrapper<PsychologistFieldRelation>()
                            .eq(PsychologistFieldRelation::getPsychologistId, psychologistId)
                            .eq(PsychologistFieldRelation::getFieldId, fieldId));
            if (existingField != null) {
                return Result.error("该领域已存在");
            }

            PsychologistFieldRelation relation = new PsychologistFieldRelation();
            relation.setPsychologistId(psychologistId);
            relation.setFieldId(fieldId);
            if (request.containsKey("subTags")) {
                relation.setSubTags((String) request.get("subTags"));
            }
            fieldRelationMapper.insert(relation);

            return Result.success("添加成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新擅长领域
     */
    @PutMapping("/fields/{id}")
    public Result<String> updatePsychologistField(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            PsychologistFieldRelation relation = fieldRelationMapper.selectById(id);
            if (relation == null) {
                return Result.error("记录不存在");
            }

            if (request.containsKey("fieldId")) {
                relation.setFieldId(Integer.valueOf(request.get("fieldId").toString()));
            }
            if (request.containsKey("subTags")) {
                relation.setSubTags((String) request.get("subTags"));
            }
            fieldRelationMapper.updateById(relation);

            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除擅长领域
     */
    @DeleteMapping("/fields/{id}")
    public Result<String> deletePsychologistField(@PathVariable Long id) {
        try {
            fieldRelationMapper.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    // ========== 资质关联管理 ==========

    /**
     * 获取心理咨询师的资质列表
     */
    @GetMapping("/{psychologistId}/qualifications")
    public Result<List<Map<String, Object>>> getPsychologistQualifications(@PathVariable Long psychologistId) {
        List<PsychologistQualificationRelation> relations = qualificationRelationMapper.selectList(
                new LambdaQueryWrapper<PsychologistQualificationRelation>()
                        .eq(PsychologistQualificationRelation::getPsychologistId, psychologistId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (PsychologistQualificationRelation relation : relations) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", relation.getId());
            item.put("psychologistId", relation.getPsychologistId());
            item.put("qualificationId", relation.getQualificationId());
            item.put("certificateUrl", relation.getCertificateUrl());
            item.put("isVerified", relation.getIsVerified());
            item.put("createTime", relation.getCreateTime());

            // 获取资质信息
            PsychologistQualification qualification = qualificationMapper.selectById(relation.getQualificationId());
            if (qualification != null) {
                item.put("qualificationName", qualification.getName());
                item.put("qualificationCode", qualification.getCode());
                item.put("qualificationDescription", qualification.getDescription());
            }
            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 添加资质
     */
    @PostMapping("/{psychologistId}/qualifications")
    public Result<String> addPsychologistQualification(
            @PathVariable Long psychologistId,
            @RequestBody Map<String, Object> request) {
        try {
            Integer qualificationId = Integer.valueOf(request.get("qualificationId").toString());

            // 检查是否已存在
            PsychologistQualificationRelation existing = qualificationRelationMapper.selectOne(
                    new LambdaQueryWrapper<PsychologistQualificationRelation>()
                            .eq(PsychologistQualificationRelation::getPsychologistId, psychologistId)
                            .eq(PsychologistQualificationRelation::getQualificationId, qualificationId));
            if (existing != null) {
                return Result.error("该资质已存在");
            }

            PsychologistQualificationRelation relation = new PsychologistQualificationRelation();
            relation.setPsychologistId(psychologistId);
            relation.setQualificationId(qualificationId);
            if (request.containsKey("certificateUrl")) {
                relation.setCertificateUrl((String) request.get("certificateUrl"));
            }
            if (request.containsKey("isVerified")) {
                relation.setIsVerified(Integer.valueOf(request.get("isVerified").toString()));
            } else {
                relation.setIsVerified(0);
            }
            qualificationRelationMapper.insert(relation);

            return Result.success("添加成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新资质
     */
    @PutMapping("/qualifications/{id}")
    public Result<String> updatePsychologistQualification(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            PsychologistQualificationRelation relation = qualificationRelationMapper.selectById(id);
            if (relation == null) {
                return Result.error("记录不存在");
            }

            if (request.containsKey("qualificationId")) {
                relation.setQualificationId(Integer.valueOf(request.get("qualificationId").toString()));
            }
            if (request.containsKey("certificateUrl")) {
                relation.setCertificateUrl((String) request.get("certificateUrl"));
            }
            if (request.containsKey("isVerified")) {
                relation.setIsVerified(Integer.valueOf(request.get("isVerified").toString()));
            }
            qualificationRelationMapper.updateById(relation);

            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除资质
     */
    @DeleteMapping("/qualifications/{id}")
    public Result<String> deletePsychologistQualification(@PathVariable Long id) {
        try {
            qualificationRelationMapper.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取所有咨询领域（用于下拉选择）
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
     * 获取所有咨询领域列表（管理端）
     */
    @GetMapping("/consultation-fields")
    public Result<List<ConsultationField>> getConsultationFieldList() {
        List<ConsultationField> fields = consultationFieldMapper.selectList(
                new LambdaQueryWrapper<ConsultationField>()
                        .orderByAsc(ConsultationField::getSortOrder));
        return Result.success(fields);
    }

    /**
     * 获取所有资质类型（用于下拉选择）
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
     * 获取所有资质类型列表（管理端）
     */
    @GetMapping("/qualifications")
    public Result<List<PsychologistQualification>> getQualificationList() {
        List<PsychologistQualification> qualifications = qualificationMapper.selectList(
                new LambdaQueryWrapper<PsychologistQualification>()
                        .orderByAsc(PsychologistQualification::getSortOrder));
        return Result.success(qualifications);
    }

    // ========== 咨询领域字典管理 ==========

    /**
     * 添加咨询领域
     */
    @PostMapping("/consultation-field")
    public Result<String> addConsultationField(@RequestBody ConsultationField field) {
        try {
            consultationFieldMapper.insert(field);
            return Result.success("添加成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新咨询领域
     */
    @PutMapping("/consultation-field/{id}")
    public Result<String> updateConsultationField(@PathVariable Integer id, @RequestBody ConsultationField field) {
        try {
            field.setId(id);
            consultationFieldMapper.updateById(field);
            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除咨询领域
     */
    @DeleteMapping("/consultation-field/{id}")
    public Result<String> deleteConsultationField(@PathVariable Integer id) {
        try {
            consultationFieldMapper.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    // ========== 资质字典管理 ==========

    /**
     * 添加资质类型
     */
    @PostMapping("/qualification")
    public Result<String> addQualification(@RequestBody PsychologistQualification qualification) {
        try {
            qualificationMapper.insert(qualification);
            return Result.success("添加成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新资质类型
     */
    @PutMapping("/qualification/{id}")
    public Result<String> updateQualification(@PathVariable Integer id, @RequestBody PsychologistQualification qualification) {
        try {
            qualification.setId(id);
            qualificationMapper.updateById(qualification);
            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除资质类型
     */
    @DeleteMapping("/qualification/{id}")
    public Result<String> deleteQualification(@PathVariable Integer id) {
        try {
            qualificationMapper.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
