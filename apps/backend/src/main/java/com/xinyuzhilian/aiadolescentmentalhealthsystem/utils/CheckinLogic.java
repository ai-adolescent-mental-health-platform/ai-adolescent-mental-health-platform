package com.xinyuzhilian.aiadolescentmentalhealthsystem.utils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 签到模块纯逻辑工具类
 *
 * 将连续天数计算、极性均值、关键词扫描等无副作用逻辑抽离为静态方法，
 * 便于单元测试覆盖（见设计文档 11 节）。
 */
public final class CheckinLogic {

    private CheckinLogic() {
    }

    /**
     * 连续签到天数：今天未签则从昨天起算，逐日回溯，遇断点停止。
     * 纯函数，不依赖日期推进（today 由调用方传入）。
     */
    public static int calcContinuousDays(Set<LocalDate> dates, LocalDate today) {
        if (dates == null || dates.isEmpty()) {
            return 0;
        }
        LocalDate cursor = today;
        if (!dates.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int continuous = 0;
        while (dates.contains(cursor)) {
            continuous++;
            cursor = cursor.minusDays(1);
        }
        return continuous;
    }

    /**
     * 情绪极性均值（四舍五入到整数）。
     */
    public static int avgPolarity(List<Integer> polarities) {
        if (polarities == null || polarities.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (Integer p : polarities) {
            sum += p == null ? 0 : p;
        }
        return (int) Math.round((double) sum / polarities.size());
    }

    /**
     * 关键词确定性扫描，命中返回命中的词与所在句子，否则返回 null。
     */
    public static KeywordHit scanKeyword(String content, List<String> keywords) {
        if (content == null || keywords == null || keywords.isEmpty()) {
            return null;
        }
        for (String kw : keywords) {
            int idx = content.indexOf(kw);
            if (idx >= 0) {
                return new KeywordHit(kw, extractSentence(content, idx));
            }
        }
        return null;
    }

    /**
     * 提取关键词所在的那句话（按句号/换行切分），作为 evidence 的最小必要片段。
     */
    public static String extractSentence(String content, int idx) {
        int start = Math.max(content.lastIndexOf('。', idx), content.lastIndexOf('\n', idx)) + 1;
        int end = content.length();
        int dotEnd = content.indexOf('。', idx);
        if (dotEnd >= 0) {
            end = dotEnd + 1;
        }
        int nlEnd = content.indexOf('\n', idx);
        if (nlEnd >= 0 && nlEnd < end) {
            end = nlEnd;
        }
        return content.substring(start, end).trim();
    }

    /** 关键词命中结果。 */
    public record KeywordHit(String keyword, String sentence) {
    }
}
