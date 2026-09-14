package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.PageResult;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.*;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.*;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IPsychologistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 心理咨询师服务实现类
 *
 * @author AI Developer
 * @since 2026-04-14
 */
@Service
@RequiredArgsConstructor
public class PsychologistServiceImpl extends ServiceImpl<PsychologistMapper, Psychologist> implements IPsychologistService {

    private final UserMapper userMapper;
    private final PsychologistMapper psychologistMapper;
    private final PsychologistServiceMapper serviceMapper;
    private final PsychologistFieldRelationMapper fieldRelationMapper;
    private final PsychologistQualificationRelationMapper qualificationRelationMapper;
    private final UserFavoritePsychologistMapper favoriteMapper;
    private final UserPsychologistHistoryMapper historyMapper;
    private final PsychologistAppointmentMapper appointmentMapper;
    private final ConsultationFieldMapper consultationFieldMapper;
    private final PsychologistQualificationMapper qualificationMapper;

    @Override
    public Psychologist getByUserId(Long userId) {
        LambdaQueryWrapper<Psychologist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Psychologist::getUserId, userId);
        return psychologistMapper.selectOne(wrapper);
    }

    @Override
    public PageResult<Map<String, Object>> listPsychologists(Integer page, Integer size, String keyword,
            List<Integer> fieldIds, List<String> serviceTypes, Integer sex,
            BigDecimal minPrice, BigDecimal maxPrice, List<Integer> qualificationIds,
            BigDecimal minRating, String languages, String sortBy, String sortOrder, Long userId) {

        // 构建查询条件
        LambdaQueryWrapper<Psychologist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Psychologist::getStatus, Psychologist.STATUS_ENABLED);

        // 关键词搜索（姓名、简介、线下地址）
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Psychologist::getRealName, keyword)
                    .or()
                    .like(Psychologist::getIntroduction, keyword)
                    .or()
                    .like(Psychologist::getOfflineRegion, keyword)
                    .or()
                    .like(Psychologist::getOfflineAddress, keyword));
        }

        // 性别筛选
        if (sex != null) {
            wrapper.eq(Psychologist::getSex, sex);
        }

        // 评分筛选
        if (minRating != null) {
            wrapper.ge(Psychologist::getRatingScore, minRating);
        }

        // 排序
        if ("experience".equals(sortBy)) {
            wrapper.orderByDesc(Psychologist::getYearsExperience);
        } else if ("count".equals(sortBy)) {
            wrapper.orderByDesc(Psychologist::getConsultationCount);
        } else if ("price".equals(sortBy)) {
            // 价格排序需要在应用层处理
            wrapper.orderByDesc(Psychologist::getRatingScore);
        } else {
            wrapper.orderByDesc(Psychologist::getRatingScore);
        }

        // 分页查询
        Page<Psychologist> pageParam = new Page<>(page, size);
        Page<Psychologist> result = psychologistMapper.selectPage(pageParam, wrapper);

        // 获取用户收藏和预约的心理咨询师ID
        Set<Long> favoritedIds = new HashSet<>();
        Set<Long> orderedIds = new HashSet<>();
        if (userId != null) {
            LambdaQueryWrapper<UserFavoritePsychologist> favWrapper = new LambdaQueryWrapper<>();
            favWrapper.eq(UserFavoritePsychologist::getUserId, userId);
            List<UserFavoritePsychologist> favorites = favoriteMapper.selectList(favWrapper);
            favoritedIds = favorites.stream()
                    .map(UserFavoritePsychologist::getPsychologistId)
                    .collect(Collectors.toSet());

            LambdaQueryWrapper<UserPsychologistHistory> histWrapper = new LambdaQueryWrapper<>();
            histWrapper.eq(UserPsychologistHistory::getUserId, userId);
            List<UserPsychologistHistory> histories = historyMapper.selectList(histWrapper);
            orderedIds = histories.stream()
                    .map(UserPsychologistHistory::getPsychologistId)
                    .collect(Collectors.toSet());
        }

        // 转换为VO
        List<Map<String, Object>> records = new ArrayList<>();
        List<Psychologist> list = result.getRecords();

        // Lambda需要使用的变量声明为final
        final Set<Long> favIds = favoritedIds;
        final Set<Long> ordIds = orderedIds;

        // 按优先级排序：收藏 > 预约 > 其他
        list.sort((a, b) -> {
            boolean aFav = favIds.contains(a.getId());
            boolean bFav = favIds.contains(b.getId());
            boolean aOrd = ordIds.contains(a.getId());
            boolean bOrd = ordIds.contains(b.getId());

            if (aFav && !bFav) return -1;
            if (!aFav && bFav) return 1;
            if (aOrd && !bOrd) return -1;
            if (!aOrd && bOrd) return 1;
            return 0;
        });

        for (Psychologist p : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", p.getId());
            item.put("userId", p.getUserId());
            item.put("realName", p.getRealName());
            item.put("sex", p.getSex());
            item.put("headPath", p.getHeadPath());
            item.put("yearsExperience", p.getYearsExperience());
            item.put("ratingScore", p.getRatingScore());
            item.put("ratingCount", p.getRatingCount());
            item.put("consultationCount", p.getConsultationCount());
            item.put("offlineRegion", p.getOfflineRegion());
            item.put("offlineAddress", p.getOfflineAddress());
            item.put("offlinePrice", p.getOfflinePrice());
            item.put("consultationPrice", p.getConsultationPrice());
            item.put("onlineStatus", p.getOnlineStatus());
            item.put("statusText", getOnlineStatusText(p.getOnlineStatus()));
            item.put("languages", p.getLanguages());

            // 获取资质
            List<Map<String, Object>> quals = getQualificationList(p.getId());
            item.put("qualifications", quals);

            // 获取擅长领域
            List<Map<String, Object>> fields = getFieldList(p.getId());
            item.put("fields", fields);

            // 获取服务列表（动态生成，价格统一从consultationPrice/offlinePrice获取）
            List<Map<String, Object>> services = new ArrayList<>();
            if (p.getConsultationPrice() != null && p.getConsultationPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                try {
                    Map<String, Object> videoService = new HashMap<>();
                    videoService.put("serviceType", "VIDEO");
                    videoService.put("price", p.getConsultationPrice());
                    services.add(videoService);

                    Map<String, Object> voiceService = new HashMap<>();
                    voiceService.put("serviceType", "VOICE");
                    voiceService.put("price", p.getConsultationPrice());
                    services.add(voiceService);
                } catch (NumberFormatException ignored) {}
            }
            if (p.getOfflinePrice() != null && p.getOfflinePrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                try {
                    Map<String, Object> offlineService = new HashMap<>();
                    offlineService.put("serviceType", "OFFLINE");
                    offlineService.put("price", p.getOfflinePrice());
                    services.add(offlineService);
                } catch (NumberFormatException ignored) {}
            }
            // 图文咨询不计费
            Map<String, Object> textService = new HashMap<>();
            textService.put("serviceType", "TEXT");
            textService.put("price", BigDecimal.ZERO);
            services.add(textService);
            item.put("services", services);

            // 基础价格（统一从咨询师表consultationPrice获取）
            if (p.getConsultationPrice() != null && p.getConsultationPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                item.put("basePrice", p.getConsultationPrice());
            } else {
                item.put("basePrice", BigDecimal.ZERO);
            }

            // 收藏和预约状态
            item.put("isFavorited", favoritedIds.contains(p.getId()));
            item.put("isOrdered", orderedIds.contains(p.getId()));

            // 优先级标签
            List<String> priorityTags = new ArrayList<>();
            if (favoritedIds.contains(p.getId())) {
                priorityTags.add("已收藏");
            }
            if (orderedIds.contains(p.getId())) {
                priorityTags.add("已咨询过");
            }
            item.put("priorityTags", priorityTags);

            records.add(item);
        }

        // 价格排序处理（基于consultationPrice排序）
        if ("price".equals(sortBy)) {
            if ("asc".equalsIgnoreCase(sortOrder)) {
                records.sort((a, b) -> {
                    BigDecimal priceA = parsePrice(a.get("consultationPrice"));
                    BigDecimal priceB = parsePrice(b.get("consultationPrice"));
                    return priceA.compareTo(priceB);
                });
            } else {
                records.sort((a, b) -> {
                    BigDecimal priceA = parsePrice(a.get("consultationPrice"));
                    BigDecimal priceB = parsePrice(b.get("consultationPrice"));
                    return priceB.compareTo(priceA);
                });
            }
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
    public PageResult<Map<String, Object>> listPsychologistsForAdmin(Integer page, Integer size, String keyword, Integer status) {
        LambdaQueryWrapper<Psychologist> wrapper = new LambdaQueryWrapper<>();

        // 状态筛选（null表示查询所有状态）
        if (status != null) {
            wrapper.eq(Psychologist::getStatus, status);
        }

        // 关键词搜索
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Psychologist::getRealName, keyword)
                    .or()
                    .like(Psychologist::getIntroduction, keyword));
        }

        wrapper.orderByDesc(Psychologist::getCreateTime);

        Page<Psychologist> pageParam = new Page<>(page, size);
        Page<Psychologist> result = psychologistMapper.selectPage(pageParam, wrapper);

        List<Map<String, Object>> records = new ArrayList<>();
        for (Psychologist p : result.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", p.getId());
            item.put("userId", p.getUserId());
            item.put("realName", p.getRealName());
            item.put("sex", p.getSex());
            item.put("headPath", p.getHeadPath());
            item.put("introduction", p.getIntroduction());
            item.put("consultationPrice", p.getConsultationPrice());
            item.put("yearsExperience", p.getYearsExperience());
            item.put("ratingScore", p.getRatingScore());
            item.put("ratingCount", p.getRatingCount());
            item.put("consultationCount", p.getConsultationCount());
            item.put("status", p.getStatus());
            item.put("onlineStatus", p.getOnlineStatus());
            item.put("createTime", p.getCreateTime());
            item.put("statusText", p.getStatus() == 1 ? "正常" : "禁用");
            item.put("isFavorited", false);
            item.put("isOrdered", false);
            item.put("fields", new ArrayList<>());
            item.put("qualifications", new ArrayList<>());
            item.put("services", new ArrayList<>());
            // 基础价格（统一从consultationPrice获取）
            if (p.getConsultationPrice() != null && p.getConsultationPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                item.put("basePrice", p.getConsultationPrice());
            } else {
                item.put("basePrice", BigDecimal.ZERO);
            }

            // 获取用户信息
            if (p.getUserId() != null) {
                User user = userMapper.selectById(p.getUserId());
                if (user != null) {
                    item.put("userNickname", user.getNickname());
                    item.put("phone", user.getPhone());
                }
            }

            // 获取擅长领域
            List<PsychologistFieldRelation> fields = fieldRelationMapper.selectList(
                    new LambdaQueryWrapper<PsychologistFieldRelation>()
                            .eq(PsychologistFieldRelation::getPsychologistId, p.getId())
            );
            item.put("fields", fields);
            // 提取擅长领域ID列表
            List<Integer> fieldIds = fields.stream()
                    .map(PsychologistFieldRelation::getFieldId)
                    .collect(java.util.stream.Collectors.toList());
            item.put("fieldIds", fieldIds);

            // 获取资质
            List<PsychologistQualificationRelation> qualifications = qualificationRelationMapper.selectList(
                    new LambdaQueryWrapper<PsychologistQualificationRelation>()
                            .eq(PsychologistQualificationRelation::getPsychologistId, p.getId())
            );
            item.put("qualifications", qualifications);
            // 提取资质ID列表
            List<Integer> qualificationIds = qualifications.stream()
                    .map(PsychologistQualificationRelation::getQualificationId)
                    .collect(java.util.stream.Collectors.toList());
            item.put("qualificationIds", qualificationIds);

            records.add(item);
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
    public Map<String, Object> getPsychologistDetail(Long id, Long userId) {
        Psychologist p = psychologistMapper.selectById(id);
        if (p == null) {
            return null;
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("id", p.getId());
        detail.put("userId", p.getUserId());
        detail.put("realName", p.getRealName());
        detail.put("sex", p.getSex());
        detail.put("headPath", p.getHeadPath());
        detail.put("introduction", p.getIntroduction());
        detail.put("educationBackground", p.getEducationBackground());
        detail.put("trainingExperience", p.getTrainingExperience());
        detail.put("yearsExperience", p.getYearsExperience());
        detail.put("ratingScore", p.getRatingScore());
        detail.put("ratingCount", p.getRatingCount());
        detail.put("consultationCount", p.getConsultationCount());
        detail.put("offlineRegion", p.getOfflineRegion());
        detail.put("offlineAddress", p.getOfflineAddress());
        detail.put("offlinePrice", p.getOfflinePrice());
        detail.put("languages", p.getLanguages());
        detail.put("onlineStatus", p.getOnlineStatus());

        // 资质
        detail.put("qualifications", getQualificationList(id));
        // 领域
        detail.put("fields", getFieldList(id));
        // 服务列表（从数据库查询，包含status状态）
        List<Map<String, Object>> services = new ArrayList<>();
        LambdaQueryWrapper<PsychologistService> serviceWrapper = new LambdaQueryWrapper<>();
        serviceWrapper.eq(PsychologistService::getPsychologistId, id);
        List<PsychologistService> serviceList = serviceMapper.selectList(serviceWrapper);
        
        for (PsychologistService svc : serviceList) {
            Map<String, Object> serviceMap = new HashMap<>();
            serviceMap.put("serviceType", svc.getServiceType().toUpperCase());
            serviceMap.put("status", svc.getStatus());
            services.add(serviceMap);
        }
        // 如果没有查到服务记录，使用默认值（从consultationPrice/offlinePrice获取）
        if (services.isEmpty()) {
        if (p.getConsultationPrice() != null && p.getConsultationPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            try {
                Map<String, Object> videoService = new HashMap<>();
                videoService.put("serviceType", "VIDEO");
                videoService.put("price", p.getConsultationPrice());
                    videoService.put("status", 1);
                services.add(videoService);

                    Map<String, Object> voiceService = new HashMap<>();
                    voiceService.put("serviceType", "VOICE");
                    voiceService.put("price", p.getConsultationPrice());
                    voiceService.put("status", 1);
                    services.add(voiceService);
                } catch (NumberFormatException ignored) {}
            }
            if (p.getOfflinePrice() != null && p.getOfflinePrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                try {
                    Map<String, Object> offlineService = new HashMap<>();
                    offlineService.put("serviceType", "OFFLINE");
                    offlineService.put("price", p.getOfflinePrice());
                    offlineService.put("status", 1);
                    services.add(offlineService);
                } catch (NumberFormatException ignored) {}
            }
            // 图文咨询不计费
            Map<String, Object> textService = new HashMap<>();
            textService.put("serviceType", "TEXT");
            textService.put("price", BigDecimal.ZERO);
            textService.put("status", 1);
            services.add(textService);
        }
        detail.put("services", services);

        // 收藏状态
        if (userId != null) {
            LambdaQueryWrapper<UserFavoritePsychologist> favWrapper = new LambdaQueryWrapper<>();
            favWrapper.eq(UserFavoritePsychologist::getUserId, userId)
                    .eq(UserFavoritePsychologist::getPsychologistId, id);
            detail.put("isFavorited", favoriteMapper.selectCount(favWrapper) > 0);
        } else {
            detail.put("isFavorited", false);
        }

        return detail;
    }

    @Override
    public List<PsychologistQualificationRelation> getQualifications(Long psychologistId) {
        return qualificationRelationMapper.selectList(
                new LambdaQueryWrapper<PsychologistQualificationRelation>()
                        .eq(PsychologistQualificationRelation::getPsychologistId, psychologistId)
        );
    }

    @Override
    public List<PsychologistFieldRelation> getFields(Long psychologistId) {
        return fieldRelationMapper.selectList(
                new LambdaQueryWrapper<PsychologistFieldRelation>()
                        .eq(PsychologistFieldRelation::getPsychologistId, psychologistId)
        );
    }

    @Override
    @Transactional
    public Psychologist savePsychologist(Psychologist psychologist) {
        if (psychologist.getId() == null) {
            // 新增
            psychologist.setCreateTime(LocalDateTime.now());
            psychologist.setRatingScore(BigDecimal.ZERO);
            psychologist.setRatingCount(0);
            psychologist.setConsultationCount(0);
            psychologist.setStatus(Psychologist.STATUS_ENABLED);
            psychologist.setOnlineStatus(Psychologist.ONLINE_OFFLINE);
            psychologistMapper.insert(psychologist);
        } else {
            // 更新
            psychologist.setUpdateTime(LocalDateTime.now());
            psychologistMapper.updateById(psychologist);
        }
        return psychologist;
    }

    @Override
    @Transactional
    public Psychologist updatePsychologist(Long id, Psychologist psychologist) {
        Psychologist existing = psychologistMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("心理咨询师不存在");
        }

        // 更新字段
        if (psychologist.getRealName() != null) {
            existing.setRealName(psychologist.getRealName());
        }
        if (psychologist.getSex() != null) {
            existing.setSex(psychologist.getSex());
        }
        if (psychologist.getHeadPath() != null) {
            existing.setHeadPath(psychologist.getHeadPath());
        }
        if (psychologist.getIntroduction() != null) {
            existing.setIntroduction(psychologist.getIntroduction());
        }
        if (psychologist.getEducationBackground() != null) {
            existing.setEducationBackground(psychologist.getEducationBackground());
        }
        if (psychologist.getTrainingExperience() != null) {
            existing.setTrainingExperience(psychologist.getTrainingExperience());
        }
        if (psychologist.getYearsExperience() != null) {
            existing.setYearsExperience(psychologist.getYearsExperience());
        }
        if (psychologist.getOfflineRegion() != null) {
            existing.setOfflineRegion(psychologist.getOfflineRegion());
        }
        if (psychologist.getOfflineAddress() != null) {
            existing.setOfflineAddress(psychologist.getOfflineAddress());
        }
        if (psychologist.getLanguages() != null) {
            existing.setLanguages(psychologist.getLanguages());
        }
        if (psychologist.getOnlineStatus() != null) {
            existing.setOnlineStatus(psychologist.getOnlineStatus());
        }

        existing.setUpdateTime(LocalDateTime.now());
        psychologistMapper.updateById(existing);
        return existing;
    }

    @Override
    public Long getPsychologistIdByUserId(Long userId) {
        LambdaQueryWrapper<Psychologist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Psychologist::getUserId, userId);
        Psychologist p = psychologistMapper.selectOne(wrapper);
        return p != null ? p.getId() : null;
    }

    @Override
    public List<Map<String, Object>> getFavorites(Long userId) {
        LambdaQueryWrapper<UserFavoritePsychologist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavoritePsychologist::getUserId, userId)
                .orderByDesc(UserFavoritePsychologist::getCreateTime);
        List<UserFavoritePsychologist> favorites = favoriteMapper.selectList(wrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (UserFavoritePsychologist fav : favorites) {
            Psychologist p = psychologistMapper.selectById(fav.getPsychologistId());
            if (p != null && p.getStatus().equals(Psychologist.STATUS_ENABLED)) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", fav.getId());
                item.put("psychologistId", p.getId());
                item.put("psychologistName", p.getRealName());
                item.put("psychologistHead", p.getHeadPath());
                item.put("ratingScore", p.getRatingScore());
                if (p.getConsultationPrice() != null && p.getConsultationPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    try {
                        item.put("basePrice", p.getConsultationPrice());
                    } catch (NumberFormatException e) {
                        item.put("basePrice", BigDecimal.ZERO);
                    }
                } else {
                    item.put("basePrice", BigDecimal.ZERO);
                }
                item.put("fields", getFieldNames(p.getId()));
                item.put("favoritedTime", fav.getCreateTime());
                item.put("onlineStatus", p.getOnlineStatus());
                item.put("yearsExperience", p.getYearsExperience());
                item.put("consultationCount", p.getConsultationCount());
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getConsultationHistory(Long userId) {
        // 从预约表获取已完成和已评价的预约作为咨询历史
        LambdaQueryWrapper<PsychologistAppointment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PsychologistAppointment::getUserId, userId)
                .in(PsychologistAppointment::getStatus, 4, 8) // 已完成、已评价
                .orderByDesc(PsychologistAppointment::getCompleteTime);

        List<PsychologistAppointment> appointments = appointmentMapper.selectList(wrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (PsychologistAppointment appt : appointments) {
            Psychologist p = psychologistMapper.selectById(appt.getPsychologistId());
            if (p != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", appt.getId());
                item.put("psychologistId", p.getId());
                item.put("psychologistName", p.getRealName());
                item.put("psychologistHead", p.getHeadPath());
                item.put("psychologistHeadPath", p.getHeadPath());
                item.put("serviceType", appt.getServiceType());
                item.put("appointmentTime", appt.getAppointmentTime());
                item.put("status", appt.getStatus());
                item.put("problems", appt.getUserProblems());
                item.put("rating", appt.getRatingScore());
                item.put("ratingComment", appt.getRatingContent());
                item.put("completeTime", appt.getCompleteTime());
                item.put("createTime", appt.getCreateTime());
                result.add(item);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public void updateRating(Long psychologistId, BigDecimal newRating) {
        Psychologist p = psychologistMapper.selectById(psychologistId);
        if (p == null) {
            return;
        }

        // 计算新的平均评分
        int count = p.getRatingCount();
        BigDecimal oldScore = p.getRatingScore();
        BigDecimal totalScore = oldScore.multiply(new BigDecimal(count));
        BigDecimal newTotalScore = totalScore.add(newRating);
        int newCount = count + 1;
        BigDecimal avgScore = newTotalScore.divide(new BigDecimal(newCount), 2, RoundingMode.HALF_UP);

        p.setRatingScore(avgScore);
        p.setRatingCount(newCount);
        p.setUpdateTime(LocalDateTime.now());
        psychologistMapper.updateById(p);
    }

    @Override
    @Transactional
    public void incrementConsultationCount(Long psychologistId) {
        Psychologist p = psychologistMapper.selectById(psychologistId);
        if (p != null) {
            p.setConsultationCount(p.getConsultationCount() + 1);
            p.setUpdateTime(LocalDateTime.now());
            psychologistMapper.updateById(p);
        }
    }

    @Override
    public PageResult<Map<String, Object>> getUserAppointments(Long userId, Integer page, Integer size, Integer status) {
        LambdaQueryWrapper<PsychologistAppointment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PsychologistAppointment::getUserId, userId)
                .orderByDesc(PsychologistAppointment::getCreateTime);

        if (status != null) {
            wrapper.eq(PsychologistAppointment::getStatus, status);
        }

        Page<PsychologistAppointment> pageParam = new Page<>(page, size);
        Page<PsychologistAppointment> result = appointmentMapper.selectPage(pageParam, wrapper);

        List<Map<String, Object>> records = new ArrayList<>();
        for (PsychologistAppointment appt : result.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", appt.getId());
            item.put("orderNo", appt.getOrderNo());
            item.put("psychologistId", appt.getPsychologistId());
            item.put("scheduleId", appt.getScheduleId());
            item.put("serviceType", appt.getServiceType());
            item.put("price", appt.getFee());
            item.put("appointmentTime", appt.getAppointmentTime());
            item.put("status", appt.getStatus());
            item.put("payStatus", appt.getPayStatus());
            item.put("videoLink", appt.getVideoLink());
            item.put("rejectReason", appt.getRejectReason());
            item.put("personalSituation", appt.getUserBasicInfo());
            item.put("problems", appt.getUserProblems());
            item.put("userExperience", appt.getUserExperience());
            item.put("userHealth", appt.getUserHealth());
            item.put("consultationContent", appt.getConsultationContent());
            item.put("isRated", appt.getIsRated());
            item.put("rating", appt.getRatingScore());
            item.put("ratingComment", appt.getRatingContent());
            item.put("ratingTime", appt.getRatingTime());
            item.put("completeTime", appt.getCompleteTime());
            item.put("createTime", appt.getCreateTime());

            // 获取心理师信息
            Psychologist psy = psychologistMapper.selectById(appt.getPsychologistId());
            if (psy != null) {
                item.put("psychologistName", psy.getRealName());
                item.put("psychologistHeadPath", psy.getHeadPath());
            }

            records.add(item);
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
    public List<Map<String, Object>> getCurrentAppointments(Long userId) {
        LambdaQueryWrapper<PsychologistAppointment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PsychologistAppointment::getUserId, userId)
                .in(PsychologistAppointment::getStatus, 0, 1, 3, 7) // 待审核、已确认、进行中、待进行
                .orderByDesc(PsychologistAppointment::getCreateTime);

        List<PsychologistAppointment> appointments = appointmentMapper.selectList(wrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (PsychologistAppointment appt : appointments) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", appt.getId());
            item.put("orderNo", appt.getOrderNo());
            item.put("psychologistId", appt.getPsychologistId());
            item.put("scheduleId", appt.getScheduleId());
            item.put("serviceType", appt.getServiceType());
            item.put("price", appt.getFee());
            item.put("appointmentTime", appt.getAppointmentTime());
            item.put("status", appt.getStatus());
            item.put("payStatus", appt.getPayStatus());
            item.put("videoLink", appt.getVideoLink());
            item.put("rejectReason", appt.getRejectReason());
            item.put("personalSituation", appt.getUserBasicInfo());
            item.put("problems", appt.getUserProblems());
            item.put("userExperience", appt.getUserExperience());
            item.put("userHealth", appt.getUserHealth());
            item.put("consultationContent", appt.getConsultationContent());
            item.put("isRated", appt.getIsRated());
            item.put("rating", appt.getRatingScore());
            item.put("ratingComment", appt.getRatingContent());
            item.put("ratingTime", appt.getRatingTime());
            item.put("completeTime", appt.getCompleteTime());
            item.put("createTime", appt.getCreateTime());

            // 获取心理师信息
            Psychologist psy = psychologistMapper.selectById(appt.getPsychologistId());
            if (psy != null) {
                item.put("psychologistName", psy.getRealName());
                item.put("psychologistHeadPath", psy.getHeadPath());
            }

            result.add(item);
        }
        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 获取在线状态文本
     */
    private String getOnlineStatusText(Integer status) {
        if (status == null) {
            return "离线";
        }
        switch (status) {
            case Psychologist.ONLINE_ONLINE:
                return "在线";
            case Psychologist.ONLINE_BUSY:
                return "忙碌";
            default:
                return "离线";
        }
    }

    @Override
    public boolean updateOnlineStatus(Long userId, Integer status) {
        LambdaQueryWrapper<Psychologist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Psychologist::getUserId, userId);
        Psychologist psychologist = psychologistMapper.selectOne(wrapper);
        if (psychologist == null) {
            return false;
        }
        psychologist.setOnlineStatus(status);
        psychologist.setUpdateTime(java.time.LocalDateTime.now());
        return psychologistMapper.updateById(psychologist) > 0;
    }

    @Override
    public void checkAndUpdateBusyStatus(Long userId) {
        // 检查该心理师是否有进行中的咨询
        LambdaQueryWrapper<PsychologistAppointment> apptWrapper = new LambdaQueryWrapper<>();
        apptWrapper.eq(PsychologistAppointment::getPsychologistId, getPsychologistIdByUserId(userId))
                .eq(PsychologistAppointment::getStatus, PsychologistAppointment.STATUS_IN_PROGRESS);
        long inProgressCount = appointmentMapper.selectCount(apptWrapper);

        // 获取当前状态
        Psychologist psychologist = getByUserId(userId);
        if (psychologist == null) return;

        Integer currentStatus = psychologist.getOnlineStatus();
        if (inProgressCount > 0) {
            // 有进行中的咨询，设置为忙碌（如果当前不是忙碌状态）
            if (currentStatus == null || currentStatus != Psychologist.ONLINE_BUSY) {
                psychologist.setOnlineStatus(Psychologist.ONLINE_BUSY);
                psychologist.setUpdateTime(java.time.LocalDateTime.now());
                psychologistMapper.updateById(psychologist);
            }
        } else {
            // 没有进行中的咨询，设置为在线（如果当前是忙碌状态）
            if (currentStatus != null && currentStatus == Psychologist.ONLINE_BUSY) {
                psychologist.setOnlineStatus(Psychologist.ONLINE_ONLINE);
                psychologist.setUpdateTime(java.time.LocalDateTime.now());
                psychologistMapper.updateById(psychologist);
            }
        }
    }

    @Override
    public Long getPsychologistUserId(Long psychologistId) {
        Psychologist p = psychologistMapper.selectById(psychologistId);
        return p != null ? p.getUserId() : null;
    }

    /**
     * 获取资质列表（带资质名称）
     */
    private List<Map<String, Object>> getQualificationList(Long psychologistId) {
        List<PsychologistQualificationRelation> relations = getQualifications(psychologistId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PsychologistQualificationRelation r : relations) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getQualificationId());
            item.put("certificateUrl", r.getCertificateUrl());
            item.put("isVerified", r.getIsVerified());
            // 获取资质名称
            if (qualificationMapper != null) {
                PsychologistQualification q = qualificationMapper.selectById(r.getQualificationId());
                if (q != null) {
                    item.put("name", q.getName());
                }
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 获取领域列表（带领域名称）
     */
    private List<Map<String, Object>> getFieldList(Long psychologistId) {
        List<PsychologistFieldRelation> relations = getFields(psychologistId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PsychologistFieldRelation r : relations) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getFieldId());
            item.put("subTags", r.getSubTags());
            // 获取领域名称
            if (consultationFieldMapper != null) {
                ConsultationField field = consultationFieldMapper.selectById(r.getFieldId());
                if (field != null) {
                    item.put("name", field.getName());
                    item.put("icon", field.getIcon());
                }
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 获取领域名称列表
     */
    private List<String> getFieldNames(Long psychologistId) {
        List<PsychologistFieldRelation> relations = getFields(psychologistId);
        return relations.stream()
                .map(r -> String.valueOf(r.getFieldId()))
                .collect(Collectors.toList());
    }

    /**
     * 解析价格，支持多种数据类型
     */
    private BigDecimal parsePrice(Object price) {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        if (price instanceof BigDecimal) {
            return (BigDecimal) price;
        }
        if (price instanceof Number) {
            return BigDecimal.valueOf(((Number) price).doubleValue());
        }
        if (price instanceof String) {
            try {
                return new BigDecimal((String) price);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }
}
