package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.dto.CheckinSubmitDTO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinTodayVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.Result;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinMoodTag;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.UserCheckin;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.exception.ServiceException;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.CheckinAnalysisMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.CheckinMoodTagMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserCheckinMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserCheckinMoodTagMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.ICheckinAnalysisService;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IDictDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 签到服务单元测试（Mockito，不依赖数据库）。
 *
 * 覆盖设计文档 11 节「POST 幂等」「归属校验」。
 */
@ExtendWith(MockitoExtension.class)
class CheckinServiceImplTest {

    @Mock private UserCheckinMapper userCheckinMapper;
    @Mock private UserCheckinMoodTagMapper userCheckinMoodTagMapper;
    @Mock private CheckinMoodTagMapper checkinMoodTagMapper;
    @Mock private CheckinAnalysisMapper checkinAnalysisMapper;
    @Mock private IDictDataService dictDataService;
    @Mock private ICheckinAnalysisService checkinAnalysisService;

    @InjectMocks
    private CheckinServiceImpl checkinService;

    // ========== 6. POST 幂等 ==========

    @Test
    void submit_同日重复_返回已有不插新() {
        UserCheckin existing = new UserCheckin();
        existing.setId(100L);
        existing.setUserId(1L);
        existing.setCheckinDate(LocalDate.now());
        when(userCheckinMapper.selectOne(any())).thenReturn(existing);
        when(userCheckinMoodTagMapper.selectList(any())).thenReturn(List.of());
        when(checkinAnalysisMapper.selectOne(any())).thenReturn(null);

        Result<CheckinTodayVO> result = checkinService.submit(1L, new CheckinSubmitDTO());

        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals(100L, result.getData().getCheckin().getId());
        verify(userCheckinMapper, never()).insert(any(UserCheckin.class));
        verify(checkinAnalysisService, never()).scheduleIfNeeded(any(), any(), any());
    }

    // ========== 建议修复 4：并发同日提交撞唯一键 → 回查返回既有记录 ==========

    @Test
    void submit_并发撞唯一键_回查返回既有记录() {
        UserCheckin concurrent = new UserCheckin();
        concurrent.setId(200L);
        concurrent.setUserId(1L);
        concurrent.setCheckinDate(LocalDate.now());

        // 首次查无记录 → 进入插入；插入撞并发唯一键 → 回查取到并发行
        when(userCheckinMapper.selectOne(any())).thenReturn(null, concurrent);
        when(checkinMoodTagMapper.selectBatchIds(any())).thenReturn(List.of(moodTag()));
        when(userCheckinMapper.insert(any(UserCheckin.class)))
                .thenThrow(new DuplicateKeyException("uk_user_checkin_date"));
        when(userCheckinMoodTagMapper.selectList(any())).thenReturn(List.of());
        when(checkinAnalysisMapper.selectOne(any())).thenReturn(null);

        CheckinSubmitDTO dto = new CheckinSubmitDTO();
        dto.setTagIds(List.of(1));
        Result<CheckinTodayVO> result = checkinService.submit(1L, dto);

        assertEquals(200L, result.getData().getCheckin().getId(), "并发撞键应返回既有记录而非 500");
        verify(checkinAnalysisService, never()).scheduleIfNeeded(any(), any(), any());
    }

    // ========== 建议修复 5：日记正文长度上限（信任边界） ==========

    @Test
    void submit_日记超长_拒绝() {
        when(userCheckinMapper.selectOne(any())).thenReturn(null);
        when(checkinMoodTagMapper.selectBatchIds(any())).thenReturn(List.of(moodTag()));

        CheckinSubmitDTO dto = new CheckinSubmitDTO();
        dto.setTagIds(List.of(1));
        dto.setDiaryContent("字".repeat(2001));

        ServiceException ex = assertThrows(ServiceException.class, () -> checkinService.submit(1L, dto));
        assertTrue(ex.getMessage().contains("2000"));
        verify(userCheckinMapper, never()).insert(any(UserCheckin.class));
    }

    private CheckinMoodTag moodTag() {
        CheckinMoodTag tag = new CheckinMoodTag();
        tag.setId(1);
        tag.setPolarity(2);
        return tag;
    }

    // ========== 8. 归属校验 ==========

    @Test
    void update_他人记录_拒绝403() {
        UserCheckin checkin = new UserCheckin();
        checkin.setId(100L);
        checkin.setUserId(999L);
        checkin.setCheckinDate(LocalDate.now());
        when(userCheckinMapper.selectById(100L)).thenReturn(checkin);

        ServiceException ex = assertThrows(ServiceException.class, () ->
                checkinService.update(1L, 100L, new CheckinSubmitDTO()));
        assertEquals(403, ex.getCode());
    }

    @Test
    void getAnalysis_他人记录_拒绝403() {
        UserCheckin checkin = new UserCheckin();
        checkin.setId(100L);
        checkin.setUserId(999L);
        when(userCheckinMapper.selectById(100L)).thenReturn(checkin);

        ServiceException ex = assertThrows(ServiceException.class, () ->
                checkinService.getAnalysis(1L, 100L));
        assertEquals(403, ex.getCode());
    }
}
