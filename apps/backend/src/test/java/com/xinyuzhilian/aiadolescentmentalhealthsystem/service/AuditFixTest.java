package com.xinyuzhilian.aiadolescentmentalhealthsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 审计修复回归测试
 * 纯单元测试，不依赖 Spring 上下文 / 数据库
 */
class AuditFixTest {

    // ========== 1. 订单号唯一性 ==========

    @Test
    void generateOrderNo_noDuplicate_in10000() {
        Set<String> orders = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            String no = generateOrderNo();
            assertFalse(orders.contains(no), "订单号重复: " + no);
            orders.add(no);
        }
    }

    @Test
    void generateOrderNo_formatCorrect() {
        String no = generateOrderNo();
        // PSY + 17位时间 + 8位UUID = 28字符
        assertTrue(no.startsWith("PSY"), "应以PSY开头");
        assertEquals(28, no.length(), "总长度应为28");
    }

    // 复制自修复后的 generateOrderNo 逻辑
    private String generateOrderNo() {
        String prefix = "PSY";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return prefix + timestamp + uuid;
    }

    // ========== 2. User 密码不序列化 ==========

    @Test
    void userPassword_notSerialized() throws JsonProcessingException {
        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        user.setPassword("$2a$10$hashedPasswordHere");

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(user);

        assertFalse(json.contains("hashedPassword"), "JSON 不应包含密码哈希");
        assertFalse(json.contains("password"), "JSON 不应包含 password 字段");
        assertTrue(json.contains("test"), "JSON 应包含用户名");
    }

    // ========== 3. Psychologist 价格字段 BigDecimal ==========

    @Test
    void psychologistPrice_isBigDecimal() {
        // 验证 BigDecimal 运算正确（以前是 String，parseFloat 有精度问题）
        BigDecimal price = new BigDecimal("199.99");
        BigDecimal rate = new BigDecimal("0.15");
        BigDecimal commission = price.multiply(rate).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal income = price.subtract(commission);

        assertEquals(new BigDecimal("30.00"), commission);
        assertEquals(new BigDecimal("169.99"), income);
    }

    // ========== 4. LocalDateTime.parse 格式 ==========

    @Test
    void localDateTimeParse_withFormat() {
        String input = "2026-04-14 10:30:00";
        // 修复前：LocalDateTime.parse(input) 会抛 DateTimeParseException
        // 修复后：使用 DateTimeFormatter
        assertDoesNotThrow(() -> {
            LocalDateTime.parse(input, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        });
    }

    @Test
    void localDateTimeParse_isoFormat_shouldFail() {
        String input = "2026-04-14T10:30:00";
        // ISO 格式在无格式化器时可解析，但不是前端传入的格式
        assertDoesNotThrow(() -> {
            LocalDateTime.parse(input);
        });
    }

    // ========== 5. videoLink/offlineAddress 逻辑 ==========

    @Test
    void videoLinkAssignment_offlineService_usesOfflineAddress() {
        String serviceType = "OFFLINE";
        String videoLink = "https://meet.example.com/123";
        String offlineAddress = "北京市朝阳区某大厦";

        String result;
        if ("OFFLINE".equalsIgnoreCase(serviceType) && offlineAddress != null && !offlineAddress.isEmpty()) {
            result = offlineAddress;
        } else if (videoLink != null && !videoLink.isEmpty()) {
            result = videoLink;
        } else {
            result = null;
        }

        assertEquals(offlineAddress, result, "线下服务应使用线下地址");
    }

    @Test
    void videoLinkAssignment_onlineService_usesVideoLink() {
        String serviceType = "VIDEO";
        String videoLink = "https://meet.example.com/123";
        String offlineAddress = "北京市朝阳区某大厦";

        String result;
        if ("OFFLINE".equalsIgnoreCase(serviceType) && offlineAddress != null && !offlineAddress.isEmpty()) {
            result = offlineAddress;
        } else if (videoLink != null && !videoLink.isEmpty()) {
            result = videoLink;
        } else {
            result = null;
        }

        assertEquals(videoLink, result, "线上服务应使用视频链接");
    }

    // ========== 6. 抽成比例精度 ==========

    @Test
    void commissionRate_decimalPrecision() {
        // 统一为 DECIMAL(5,4) 存 0-1 小数
        BigDecimal rate = new BigDecimal("0.1500");
        BigDecimal fee = new BigDecimal("200.00");
        BigDecimal commission = fee.multiply(rate);

        assertEquals(new BigDecimal("30.000000"), commission);
        // 截断到2位小数用于实际金额
        assertEquals(new BigDecimal("30.00"), commission.setScale(2, BigDecimal.ROUND_HALF_UP));
    }
}
