package com.xinyuzhilian.aiadolescentmentalhealthsystem.service;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinMoodTag;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.ConsultationField;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistQualification;

import java.util.List;

/**
 * 字典数据服务接口
 * 提供咨询领域、资质类型、签到情绪标签等字典数据的加载和查询功能
 *
 * @author AI Developer
 * @since 2026-04-14
 */
public interface IDictDataService {

    /**
     * 获取所有咨询领域
     *
     * @return 咨询领域列表
     */
    List<ConsultationField> getAllConsultationFields();

    /**
     * 获取所有资质类型
     *
     * @return 资质类型列表
     */
    List<PsychologistQualification> getAllQualifications();

    /**
     * 获取所有签到情绪标签（仅启用，按 sort_order 升序）
     *
     * @return 情绪标签列表
     */
    List<CheckinMoodTag> getAllMoodTags();

    /**
     * 根据ID获取咨询领域
     *
     * @param id 领域ID
     * @return 咨询领域
     */
    ConsultationField getConsultationFieldById(Integer id);

    /**
     * 根据ID获取资质类型
     *
     * @param id 资质ID
     * @return 资质类型
     */
    PsychologistQualification getQualificationById(Integer id);

    /**
     * 根据ID获取签到情绪标签
     *
     * @param id 标签ID
     * @return 情绪标签
     */
    CheckinMoodTag getMoodTagById(Integer id);

    /**
     * 刷新字典数据缓存
     */
    void refreshDictCache();

    /**
     * 初始化字典数据（由ApplicationRunner调用）
     */
    default void initDictData() {
    }
}
