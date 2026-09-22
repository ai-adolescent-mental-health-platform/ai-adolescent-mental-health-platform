package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.CheckinAnalysis;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.RiskAlert;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.UserCheckin;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.CheckinAnalysisMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.RiskAlertMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserCheckinMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.utils.DashScopeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 签到分析流水线单元测试（Mockito，不依赖数据库）。
 *
 * 覆盖设计文档 11 节「关键词兜底规则」「等级1不写 risk_alert」
 * 「解析失败 risk_level 保持 NULL」「分析抢占式更新」「扫尾任务」。
 */
@ExtendWith(MockitoExtension.class)
class CheckinAnalysisServiceImplTest {

    @Mock private CheckinAnalysisMapper checkinAnalysisMapper;
    @Mock private UserCheckinMapper userCheckinMapper;
    @Mock private DashScopeClient dashScopeClient;
    @Mock private RiskAlertMapper riskAlertMapper;
    @Mock private ThreadPoolTaskExecutor executor;

    private CheckinAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CheckinAnalysisServiceImpl(
                checkinAnalysisMapper, userCheckinMapper, dashScopeClient,
                riskAlertMapper, executor);
    }

    private CheckinAnalysis pendingAnalysis() {
        CheckinAnalysis analysis = new CheckinAnalysis();
        analysis.setId(1L);
        analysis.setCheckinId(10L);
        analysis.setUserId(1L);
        return analysis;
    }

    private UserCheckin checkinWith(String diary) {
        UserCheckin checkin = new UserCheckin();
        checkin.setId(10L);
        checkin.setUserId(1L);
        checkin.setDiaryContent(diary);
        return checkin;
    }

    // ========== 3. 关键词兜底：命中 + 模型判 0 → 最终 2 ==========

    @Test
    void process_关键词命中_强制level2() throws IOException {
        ReflectionTestUtils.setField(service, "riskKeywords", "自杀,轻生");
        CheckinAnalysis analysis = pendingAnalysis();
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(1);
        when(checkinAnalysisMapper.selectById(1L)).thenReturn(analysis);
        when(userCheckinMapper.selectById(10L)).thenReturn(checkinWith("我今天很想自杀。"));

        // 模型判 0（平稳），但关键词命中 → 强制 2
        when(dashScopeClient.chat(any(), any())).thenReturn(
                "{\"risk_level\":0,\"risk_reason\":\"无明显风险\",\"evidence\":\"\",\"user_feedback\":\"保持\",\"suggestion\":\"\"}");

        service.process(1L);

        ArgumentCaptor<CheckinAnalysis> captor = ArgumentCaptor.forClass(CheckinAnalysis.class);
        verify(checkinAnalysisMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getRiskLevel());
        verify(riskAlertMapper).insert(any(RiskAlert.class));
    }

    // ========== 3. 关键词兜底：未命中 + 模型判 2 → 最终 2 ==========

    @Test
    void process_模型判2_未命中关键词_落库level2() throws IOException {
        CheckinAnalysis analysis = pendingAnalysis();
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(1);
        when(checkinAnalysisMapper.selectById(1L)).thenReturn(analysis);
        when(userCheckinMapper.selectById(10L)).thenReturn(checkinWith("最近很难受。"));

        when(dashScopeClient.chat(any(), any())).thenReturn(
                "{\"risk_level\":2,\"risk_reason\":\"明显痛苦\",\"evidence\":\"最近很难受\",\"user_feedback\":\"请拨打12356\",\"suggestion\":\"寻求帮助\"}");

        service.process(1L);

        ArgumentCaptor<CheckinAnalysis> captor = ArgumentCaptor.forClass(CheckinAnalysis.class);
        verify(checkinAnalysisMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getRiskLevel());
        verify(riskAlertMapper).insert(any(RiskAlert.class));
    }

    // ========== 4. 等级 1 不写 risk_alert ==========

    @Test
    void process_模型判1_不写riskAlert() throws IOException {
        CheckinAnalysis analysis = pendingAnalysis();
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(1);
        when(checkinAnalysisMapper.selectById(1L)).thenReturn(analysis);
        when(userCheckinMapper.selectById(10L)).thenReturn(checkinWith("今天有点低落。"));

        when(dashScopeClient.chat(any(), any())).thenReturn(
                "{\"risk_level\":1,\"risk_reason\":\"持续低落\",\"evidence\":\"今天有点低落\",\"user_feedback\":\"抱抱你\",\"suggestion\":\"多休息\"}");

        service.process(1L);

        ArgumentCaptor<CheckinAnalysis> captor = ArgumentCaptor.forClass(CheckinAnalysis.class);
        verify(checkinAnalysisMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getRiskLevel());
        verify(riskAlertMapper, never()).insert(any(RiskAlert.class));
        verify(riskAlertMapper, never()).updateById(any(RiskAlert.class));
    }

    // ========== 5. 解析失败时 risk_level 保持 NULL ==========

    @Test
    void process_解析失败_riskLevel保持NULL() throws IOException {
        CheckinAnalysis analysis = pendingAnalysis();
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(1);
        when(checkinAnalysisMapper.selectById(1L)).thenReturn(analysis);
        when(userCheckinMapper.selectById(10L)).thenReturn(checkinWith("今天有点低落。"));
        when(dashScopeClient.chat(any(), any())).thenThrow(new IOException("网络错误"));

        service.process(1L);

        ArgumentCaptor<CheckinAnalysis> captor = ArgumentCaptor.forClass(CheckinAnalysis.class);
        verify(checkinAnalysisMapper).updateById(captor.capture());
        assertNull(captor.getValue().getRiskLevel(), "失败时 risk_level 必须保持 NULL");
        assertEquals(CheckinAnalysis.STATUS_FAILED, captor.getValue().getStatus());
    }

    // ========== 7. 分析抢占式更新 ==========

    @Test
    void process_抢占失败_不执行() throws IOException {
        when(checkinAnalysisMapper.claimPending(1L)).thenReturn(0);

        service.process(1L);

        verify(dashScopeClient, never()).chat(any(), any());
        verify(checkinAnalysisMapper, never()).updateById(any(CheckinAnalysis.class));
    }

    // ========== 9. 扫尾任务（三种捞取条件） ==========

    @Test
    void sweep_三种捞取条件() {
        CheckinAnalysis pending = new CheckinAnalysis();
        pending.setId(1L);
        CheckinAnalysis failed = new CheckinAnalysis();
        failed.setId(2L);
        failed.setRetryCount(1);
        CheckinAnalysis stuck = new CheckinAnalysis();
        stuck.setId(3L);

        when(checkinAnalysisMapper.selectList(any()))
                .thenReturn(List.of(pending), List.of(failed), List.of(stuck));

        service.sweep();

        // pending → submit（1 次）；failed → 置 PENDING + submit（1 次）；stuck → 置 FAILED（0 次）
        verify(executor, times(2)).execute(any());
        // failed 与 stuck 各自 updateById 一次
        verify(checkinAnalysisMapper, times(2)).updateById(any(CheckinAnalysis.class));
    }
}
