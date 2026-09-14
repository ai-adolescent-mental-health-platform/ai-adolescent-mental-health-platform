package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.PageResult;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.Psychologist;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistProfileAudit;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.User;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistFieldRelationMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistQualificationRelationMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistProfileAuditMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistProfileAuditService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistFieldRelation;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistQualificationRelation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 心理咨询师资料审核服务实现类
 *
 * @author AI Developer
 * @since 2026-04-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PsychologistProfileAuditServiceImpl extends ServiceImpl<PsychologistProfileAuditMapper, PsychologistProfileAudit>
        implements IPsychologistProfileAuditService {

    private final PsychologistProfileAuditMapper profileAuditMapper;
    private final PsychologistMapper psychologistMapper;
    private final UserMapper userMapper;
    private final PsychologistFieldRelationMapper fieldRelationMapper;
    private final PsychologistQualificationRelationMapper qualificationRelationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitProfileChange(Long psychologistId, String fieldName, String oldValue, String newValue, String proofUrls, String reason) {
        Map<String, Object> result = new HashMap<>();

        // 创建审核记录
        PsychologistProfileAudit audit = new PsychologistProfileAudit();
        audit.setPsychologistId(psychologistId);
        audit.setFieldName(fieldName);
        audit.setOldValue(oldValue);
        audit.setNewValue(newValue);
        audit.setProofUrls(proofUrls);
        audit.setReason(reason);
        audit.setAuditStatus(PsychologistProfileAudit.STATUS_PENDING);
        audit.setCreateTime(LocalDateTime.now());

        // 根据字段类型判断是否需要审核
        boolean requiresAudit = requiresAdminAudit(fieldName);
        if (!requiresAudit) {
            // 不需要审核的字段，直接更新
            updatePsychologistField(psychologistId, fieldName, newValue);
            audit.setAuditStatus(PsychologistProfileAudit.STATUS_PASSED);
            audit.setAuditTime(LocalDateTime.now());
            audit.setAuditRemark("系统自动通过（该字段无需审核）");
            result.put("directApply", true);
        } else {
            result.put("directApply", false);
        }

        profileAuditMapper.insert(audit);
        result.put("auditId", audit.getId());
        result.put("success", true);
        result.put("message", requiresAudit ? "提交成功，等待管理员审核" : "更新成功");

        return result;
    }

    @Override
    public PageResult<Map<String, Object>> getAuditListByPsychologist(Long psychologistId, int page, int size) {
        Page<PsychologistProfileAudit> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PsychologistProfileAudit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PsychologistProfileAudit::getPsychologistId, psychologistId)
               .orderByDesc(PsychologistProfileAudit::getCreateTime);

        Page<PsychologistProfileAudit> result = profileAuditMapper.selectPage(pageParam, wrapper);

        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPages(result.getPages());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setSize(result.getSize());
        pageResult.setRecords(new java.util.ArrayList<>());

        // 转换数据
        for (PsychologistProfileAudit audit : result.getRecords()) {
            Map<String, Object> item = convertAuditToMap(audit);
            pageResult.getRecords().add(item);
        }

        return pageResult;
    }

    @Override
    public PageResult<Map<String, Object>> getPendingAudits(Integer status, int page, int size) {
        Page<PsychologistProfileAudit> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<PsychologistProfileAudit> wrapper = new LambdaQueryWrapper<>();

        if (status != null) {
            wrapper.eq(PsychologistProfileAudit::getAuditStatus, status);
        }
        wrapper.orderByDesc(PsychologistProfileAudit::getCreateTime);

        Page<PsychologistProfileAudit> result = profileAuditMapper.selectPage(pageParam, wrapper);

        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setTotal(result.getTotal());
        pageResult.setPages(result.getPages());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setSize(result.getSize());
        pageResult.setRecords(new java.util.ArrayList<>());

        // 转换数据并关联心理咨询师信息
        for (PsychologistProfileAudit audit : result.getRecords()) {
            Map<String, Object> item = convertAuditToMap(audit);
            // 获取心理咨询师信息
            Psychologist psychologist = psychologistMapper.selectById(audit.getPsychologistId());
            if (psychologist != null) {
                item.put("psychologistName", psychologist.getRealName());
                // 获取关联用户信息
                if (psychologist.getUserId() != null) {
                    User user = userMapper.selectById(psychologist.getUserId());
                    if (user != null) {
                        item.put("userNickname", user.getNickname());
                        item.put("userAvatar", user.getHeadPath());
                    }
                }
            }
            pageResult.getRecords().add(item);
        }

        return pageResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> approveAudit(Long auditId, Long auditorId, String remark) {
        Map<String, Object> result = new HashMap<>();

        PsychologistProfileAudit audit = profileAuditMapper.selectById(auditId);
        if (audit == null) {
            result.put("success", false);
            result.put("message", "审核记录不存在");
            return result;
        }

        if (audit.getAuditStatus() != PsychologistProfileAudit.STATUS_PENDING) {
            result.put("success", false);
            result.put("message", "该审核记录已处理");
            return result;
        }

        // 更新审核状态
        audit.setAuditStatus(PsychologistProfileAudit.STATUS_PASSED);
        audit.setAuditorId(auditorId);
        audit.setAuditTime(LocalDateTime.now());
        audit.setAuditRemark(remark);
        profileAuditMapper.updateById(audit);

        // 更新心理咨询师对应字段
        updatePsychologistField(audit.getPsychologistId(), audit.getFieldName(), audit.getNewValue());

        result.put("success", true);
        result.put("message", "审核通过");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rejectAudit(Long auditId, Long auditorId, String remark) {
        Map<String, Object> result = new HashMap<>();

        PsychologistProfileAudit audit = profileAuditMapper.selectById(auditId);
        if (audit == null) {
            result.put("success", false);
            result.put("message", "审核记录不存在");
            return result;
        }

        if (audit.getAuditStatus() != PsychologistProfileAudit.STATUS_PENDING) {
            result.put("success", false);
            result.put("message", "该审核记录已处理");
            return result;
        }

        // 更新审核状态
        audit.setAuditStatus(PsychologistProfileAudit.STATUS_REJECTED);
        audit.setAuditorId(auditorId);
        audit.setAuditTime(LocalDateTime.now());
        audit.setAuditRemark(remark);
        profileAuditMapper.updateById(audit);

        result.put("success", true);
        result.put("message", "已拒绝");
        return result;
    }

    @Override
    public Map<String, Object> getAuditDetail(Long auditId) {
        PsychologistProfileAudit audit = profileAuditMapper.selectById(auditId);
        if (audit == null) {
            return null;
        }

        Map<String, Object> detail = convertAuditToMap(audit);

        // 获取心理咨询师信息
        Psychologist psychologist = psychologistMapper.selectById(audit.getPsychologistId());
        if (psychologist != null) {
            detail.put("psychologistName", psychologist.getRealName());
            detail.put("psychologistAvatar", psychologist.getHeadPath());
            if (psychologist.getUserId() != null) {
                User user = userMapper.selectById(psychologist.getUserId());
                if (user != null) {
                    detail.put("userNickname", user.getNickname());
                    detail.put("userAvatar", user.getHeadPath());
                }
            }
        }

        return detail;
    }

    /**
     * 判断字段是否需要管理员审核
     * 资质、擅长领域、价格、线下价格需要审核
     * 姓名、咨询经验年限不需要审核
     */
    private boolean requiresAdminAudit(String fieldName) {
        return PsychologistProfileAudit.FIELD_QUALIFICATION.equals(fieldName)
            || PsychologistProfileAudit.FIELD_FIELD.equals(fieldName)
            || PsychologistProfileAudit.FIELD_PRICE.equals(fieldName)
            || PsychologistProfileAudit.FIELD_OFFLINE_PRICE.equals(fieldName);
    }

    /**
     * 更新心理咨询师字段
     */
    private void updatePsychologistField(Long psychologistId, String fieldName, String newValue) {
        if (newValue == null || newValue.isEmpty()) {
            return;
        }

        // 处理擅长领域关联表
        if (PsychologistProfileAudit.FIELD_FIELD.equals(fieldName)) {
            // 删除旧关联
            fieldRelationMapper.delete(
                    new LambdaQueryWrapper<PsychologistFieldRelation>()
                            .eq(PsychologistFieldRelation::getPsychologistId, psychologistId));
            // 添加新关联
            String[] fieldIds = newValue.split(",");
            for (String fieldIdStr : fieldIds) {
                try {
                    Integer fieldId = Integer.parseInt(fieldIdStr.trim());
                    PsychologistFieldRelation relation = new PsychologistFieldRelation();
                    relation.setPsychologistId(psychologistId);
                    relation.setFieldId(fieldId);
                    fieldRelationMapper.insert(relation);
                } catch (NumberFormatException e) {
                    log.warn("无效的领域ID: {}", fieldIdStr);
                }
            }
            return;
        }

        // 处理资质关联表
        if (PsychologistProfileAudit.FIELD_QUALIFICATION.equals(fieldName)) {
            // 删除旧关联
            qualificationRelationMapper.delete(
                    new LambdaQueryWrapper<PsychologistQualificationRelation>()
                            .eq(PsychologistQualificationRelation::getPsychologistId, psychologistId));
            // 添加新关联
            String[] qualIds = newValue.split(",");
            for (String qualIdStr : qualIds) {
                try {
                    Integer qualId = Integer.parseInt(qualIdStr.trim());
                    PsychologistQualificationRelation relation = new PsychologistQualificationRelation();
                    relation.setPsychologistId(psychologistId);
                    relation.setQualificationId(qualId);
                    relation.setIsVerified(0); // 待审核状态
                    qualificationRelationMapper.insert(relation);
                } catch (NumberFormatException e) {
                    log.warn("无效的资质ID: {}", qualIdStr);
                }
            }
            return;
        }

        // 其他字段直接更新
        Psychologist psychologist = psychologistMapper.selectById(psychologistId);
        if (psychologist == null) {
            return;
        }

        switch (fieldName) {
            case "name":
            case "realName":
                psychologist.setRealName(newValue);
                break;
            case "yearsExperience":
                try {
                    psychologist.setYearsExperience(Integer.parseInt(newValue));
                } catch (NumberFormatException e) {
                    log.warn("无效的咨询经验年限: {}", newValue);
                }
                break;
            case "consultationPrice":
            case "price":
                psychologist.setConsultationPrice(new java.math.BigDecimal(newValue));
                break;
            case "offlinePrice":
            case "offline_price":
                psychologist.setOfflinePrice(new java.math.BigDecimal(newValue));
                break;
            default:
                log.warn("未知的字段类型: {}", fieldName);
                return;
        }

        psychologistMapper.updateById(psychologist);
    }

    /**
     * 转换审核记录为Map
     */
    private Map<String, Object> convertAuditToMap(PsychologistProfileAudit audit) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", audit.getId());
        map.put("psychologistId", audit.getPsychologistId());
        map.put("fieldName", audit.getFieldName());
        map.put("fieldNameText", getFieldNameText(audit.getFieldName()));
        map.put("oldValue", audit.getOldValue());
        map.put("newValue", audit.getNewValue());
        map.put("proofUrls", audit.getProofUrls());
        map.put("auditStatus", audit.getAuditStatus());
        map.put("auditStatusText", getAuditStatusText(audit.getAuditStatus()));
        map.put("auditRemark", audit.getAuditRemark());
        map.put("reason", audit.getReason());
        map.put("auditorId", audit.getAuditorId());
        map.put("auditTime", audit.getAuditTime());
        map.put("createTime", audit.getCreateTime());
        return map;
    }

    /**
     * 获取字段名称文本
     */
    private String getFieldNameText(String fieldName) {
        switch (fieldName) {
            case "qualification":
                return "资质标签";
            case "field":
                return "擅长领域";
            case "price":
            case "consultationPrice":
                return "线上咨询价格";
            case "offline_price":
            case "offlinePrice":
                return "线下咨询价格";
            case "name":
            case "realName":
                return "姓名";
            case "yearsExperience":
                return "咨询经验年限";
            default:
                return fieldName;
        }
    }

    /**
     * 获取审核状态文本
     */
    private String getAuditStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0:
                return "待审核";
            case 1:
                return "已通过";
            case 2:
                return "已拒绝";
            default:
                return "未知";
        }
    }
}
