package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinMoodTag;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.ConsultationField;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.PsychologistQualification;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.CheckinMoodTagMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.ConsultationFieldMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.PsychologistQualificationMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IDictDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典数据服务实现类
 * 提供咨询领域、资质类型、签到情绪标签等字典数据的加载和查询功能
 *
 * @author AI Developer
 * @since 2026-04-14
 */
@Slf4j
@Service
public class DictDataServiceImpl implements IDictDataService, ApplicationRunner {

    private final ConsultationFieldMapper consultationFieldMapper;
    private final PsychologistQualificationMapper qualificationMapper;
    private final CheckinMoodTagMapper checkinMoodTagMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis缓存键
     */
    private static final String CACHE_KEY_CONSULTATION_FIELDS = "dict:consultation_fields";
    private static final String CACHE_KEY_QUALIFICATIONS = "dict:qualifications";
    private static final String CACHE_KEY_MOOD_TAGS = "dict:checkin_mood_tags";

    public DictDataServiceImpl(ConsultationFieldMapper consultationFieldMapper,
                               PsychologistQualificationMapper qualificationMapper,
                               CheckinMoodTagMapper checkinMoodTagMapper) {
        this.consultationFieldMapper = consultationFieldMapper;
        this.qualificationMapper = qualificationMapper;
        this.checkinMoodTagMapper = checkinMoodTagMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("开始初始化字典数据到Redis缓存...");
            refreshDictCache();
            log.info("字典数据初始化完成");
        } catch (Exception e) {
            log.error("字典数据初始化失败", e);
        }
    }

    /**
     * 检查Redis是否可用
     */
    private boolean isRedisAvailable() {
        return redisTemplate != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ConsultationField> getAllConsultationFields() {
        // 尝试从Redis获取
        if (isRedisAvailable()) {
            try {
                Object cached = redisTemplate.opsForValue().get(CACHE_KEY_CONSULTATION_FIELDS);
                if (cached != null) {
                    return (List<ConsultationField>) cached;
                }
            } catch (Exception e) {
                log.warn("从Redis获取咨询领域失败", e);
            }
        }

        // 从数据库获取
        List<ConsultationField> fields = consultationFieldMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConsultationField>()
                        .eq(ConsultationField::getStatus, 1)
                        .orderByAsc(ConsultationField::getSortOrder)
        );

        // 缓存到Redis
        if (isRedisAvailable()) {
            try {
                redisTemplate.opsForValue().set(CACHE_KEY_CONSULTATION_FIELDS, fields);
            } catch (Exception e) {
                log.warn("缓存咨询领域到Redis失败", e);
            }
        }

        return fields;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PsychologistQualification> getAllQualifications() {
        // 尝试从Redis获取
        if (isRedisAvailable()) {
            try {
                Object cached = redisTemplate.opsForValue().get(CACHE_KEY_QUALIFICATIONS);
                if (cached != null) {
                    return (List<PsychologistQualification>) cached;
                }
            } catch (Exception e) {
                log.warn("从Redis获取资质类型失败", e);
            }
        }

        // 从数据库获取
        List<PsychologistQualification> qualifications = qualificationMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PsychologistQualification>()
                        .eq(PsychologistQualification::getStatus, 1)
                        .orderByAsc(PsychologistQualification::getSortOrder)
        );

        // 缓存到Redis
        if (isRedisAvailable()) {
            try {
                redisTemplate.opsForValue().set(CACHE_KEY_QUALIFICATIONS, qualifications);
            } catch (Exception e) {
                log.warn("缓存资质类型到Redis失败", e);
            }
        }

        return qualifications;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CheckinMoodTag> getAllMoodTags() {
        // 尝试从Redis获取
        if (isRedisAvailable()) {
            try {
                Object cached = redisTemplate.opsForValue().get(CACHE_KEY_MOOD_TAGS);
                if (cached != null) {
                    return (List<CheckinMoodTag>) cached;
                }
            } catch (Exception e) {
                log.warn("从Redis获取签到情绪标签失败", e);
            }
        }

        // 从数据库获取
        List<CheckinMoodTag> tags = checkinMoodTagMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CheckinMoodTag>()
                        .eq(CheckinMoodTag::getStatus, 1)
                        .orderByAsc(CheckinMoodTag::getSortOrder)
        );

        // 缓存到Redis
        if (isRedisAvailable()) {
            try {
                redisTemplate.opsForValue().set(CACHE_KEY_MOOD_TAGS, tags);
            } catch (Exception e) {
                log.warn("缓存签到情绪标签到Redis失败", e);
            }
        }

        return tags;
    }

    @Override
    public ConsultationField getConsultationFieldById(Integer id) {
        List<ConsultationField> fields = getAllConsultationFields();
        return fields.stream()
                .filter(f -> f.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public PsychologistQualification getQualificationById(Integer id) {
        List<PsychologistQualification> qualifications = getAllQualifications();
        return qualifications.stream()
                .filter(q -> q.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public CheckinMoodTag getMoodTagById(Integer id) {
        List<CheckinMoodTag> tags = getAllMoodTags();
        return tags.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void refreshDictCache() {
        if (!isRedisAvailable()) {
            log.warn("Redis不可用，跳过缓存刷新");
            return;
        }

        // 刷新咨询领域
        List<ConsultationField> fields = consultationFieldMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConsultationField>()
                        .eq(ConsultationField::getStatus, 1)
                        .orderByAsc(ConsultationField::getSortOrder)
        );
        try {
            redisTemplate.opsForValue().set(CACHE_KEY_CONSULTATION_FIELDS, fields);
        } catch (Exception e) {
            log.warn("刷新咨询领域缓存失败", e);
        }

        // 刷新资质类型
        List<PsychologistQualification> qualifications = qualificationMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PsychologistQualification>()
                        .eq(PsychologistQualification::getStatus, 1)
                        .orderByAsc(PsychologistQualification::getSortOrder)
        );
        try {
            redisTemplate.opsForValue().set(CACHE_KEY_QUALIFICATIONS, qualifications);
        } catch (Exception e) {
            log.warn("刷新资质类型缓存失败", e);
        }

        // 刷新签到情绪标签
        List<CheckinMoodTag> moodTags = checkinMoodTagMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CheckinMoodTag>()
                        .eq(CheckinMoodTag::getStatus, 1)
                        .orderByAsc(CheckinMoodTag::getSortOrder)
        );
        try {
            redisTemplate.opsForValue().set(CACHE_KEY_MOOD_TAGS, moodTags);
        } catch (Exception e) {
            log.warn("刷新签到情绪标签缓存失败", e);
        }
    }
}
