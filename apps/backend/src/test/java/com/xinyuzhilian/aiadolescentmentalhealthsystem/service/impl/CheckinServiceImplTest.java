package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.dto.CheckinSubmitDTO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.checkin.vo.CheckinTodayVO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.common.Result;
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
