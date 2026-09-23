package com.xinyuzhilian.aiadolescentmentalhealthsystem.utils;

import com.xinyuzhilian.aiadolescentmentalhealthsystem.utils.CheckinLogic.KeywordHit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 签到纯逻辑单元测试（不依赖 Spring / 数据库）。
 *
 * 覆盖设计文档 11 节「连续天数计算」「mood_polarity 计算」「关键词兜底规则」。
 */
class CheckinLogicTest {

    // ========== 1. 连续天数计算 ==========

    @Test
    void continuousDays_跨月() {
        LocalDate today = LocalDate.of(2026, 6, 3);
        Set<LocalDate> dates = Set.of(
                LocalDate.of(2026, 5, 30),
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2),
                LocalDate.of(2026, 6, 3)
        );
        assertEquals(5, CheckinLogic.calcContinuousDays(dates, today));
    }

    @Test
    void continuousDays_跨年() {
        LocalDate today = LocalDate.of(2026, 1, 2);
        Set<LocalDate> dates = Set.of(
                LocalDate.of(2025, 12, 30),
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2)
        );
        assertEquals(4, CheckinLogic.calcContinuousDays(dates, today));
    }

    @Test
    void continuousDays_中间断签() {
        LocalDate today = LocalDate.of(2026, 6, 3);
        Set<LocalDate> dates = Set.of(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2),
                LocalDate.of(2026, 6, 3)
        );
        assertEquals(3, CheckinLogic.calcContinuousDays(dates, today));
    }

    @Test
    void continuousDays_今天未签到() {
        LocalDate today = LocalDate.of(2026, 6, 3);
        Set<LocalDate> dates = Set.of(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2)
        );
        assertEquals(2, CheckinLogic.calcContinuousDays(dates, today));
    }

    @Test
    void continuousDays_首次签到() {
        LocalDate today = LocalDate.of(2026, 6, 3);
        Set<LocalDate> dates = Set.of(LocalDate.of(2026, 6, 3));
        assertEquals(1, CheckinLogic.calcContinuousDays(dates, today));
    }

    @Test
    void continuousDays_空集合() {
        assertEquals(0, CheckinLogic.calcContinuousDays(Set.of(), LocalDate.now()));
        assertEquals(0, CheckinLogic.calcContinuousDays(null, LocalDate.now()));
    }

    // ========== 2. mood_polarity 计算 ==========

    @Test
    void avgPolarity_多标签均值() {
        assertEquals(0, CheckinLogic.avgPolarity(List.of(2, -2)));
        assertEquals(2, CheckinLogic.avgPolarity(List.of(2, 1, 2)));
        assertEquals(-1, CheckinLogic.avgPolarity(List.of(-1, -1, -2)));
    }

    @Test
    void avgPolarity_单标签() {
        assertEquals(2, CheckinLogic.avgPolarity(List.of(2)));
        assertEquals(-2, CheckinLogic.avgPolarity(List.of(-2)));
        assertEquals(0, CheckinLogic.avgPolarity(List.of(0)));
    }

    @Test
    void avgPolarity_无标签() {
        assertEquals(0, CheckinLogic.avgPolarity(List.of()));
        assertEquals(0, CheckinLogic.avgPolarity(null));
    }

    // ========== 3. 关键词兜底规则 ==========

    @Test
    void scanKeyword_命中() {
        List<String> keywords = List.of("自杀", "轻生");
        KeywordHit hit = CheckinLogic.scanKeyword("我今天很想自杀。", keywords);
        assertNotNull(hit);
        assertEquals("自杀", hit.keyword());
        assertEquals("我今天很想自杀。", hit.sentence());
    }

    @Test
    void scanKeyword_未命中() {
        List<String> keywords = List.of("自杀", "轻生");
        assertNull(CheckinLogic.scanKeyword("今天心情还不错。", keywords));
    }

    @Test
    void scanKeyword_空输入() {
        assertNull(CheckinLogic.scanKeyword("内容", List.of()));
        assertNull(CheckinLogic.scanKeyword(null, List.of("自杀")));
        assertNull(CheckinLogic.scanKeyword("内容", null));
    }

    @Test
    void extractSentence_句号切分() {
        String content = "今天很累。我有点想自杀。明天会好吗";
        int idx = content.indexOf("自杀");
        assertEquals("我有点想自杀。", CheckinLogic.extractSentence(content, idx));
    }

    @Test
    void extractSentence_换行切分() {
        String content = "今天很累\n我有点想自杀\n明天会好吗";
        int idx = content.indexOf("自杀");
        assertEquals("我有点想自杀", CheckinLogic.extractSentence(content, idx));
    }
}
