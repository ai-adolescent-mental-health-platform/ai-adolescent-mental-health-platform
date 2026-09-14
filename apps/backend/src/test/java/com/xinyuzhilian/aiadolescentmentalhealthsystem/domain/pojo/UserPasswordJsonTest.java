package com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * User.password 的 Jackson 行为约束。
 *
 * 背景：POST /user/login 使用 @RequestBody User 接收请求体，因此 password
 * 必须能被反序列化；同时响应中 result.put("userInfo", user) 会把 User
 * 序列化输出，密码哈希不得外泄。
 *
 * 回归用例：曾用 @JsonIgnore 实现后者，但它同时阻断了反序列化，
 * 导致登录请求的 password 为 null，认证抛异常返回 500。
 */
class UserPasswordJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("登录请求体的 password 必须能被反序列化（否则 /user/login 认证失败）")
    void password_isDeserializedFromLoginRequest() throws Exception {
        String loginBody = "{\"username\":\"xiaoxinchaoren\",\"password\":\"123456\"}";

        User user = objectMapper.readValue(loginBody, User.class);

        assertEquals("xiaoxinchaoren", user.getUsername());
        assertEquals("123456", user.getPassword(),
                "password 必须能被反序列化，否则 UsernamePasswordAuthenticationToken 拿到的凭据为 null");
    }

    @Test
    @DisplayName("序列化输出的 userInfo 不得包含密码哈希")
    void password_isNotSerializedIntoResponse() throws Exception {
        User user = new User();
        user.setUsername("xiaoxinchaoren");
        user.setPassword("$2a$10$gCMZXTTm7CqG2/h3/2N8iu/6Yfac9TEMFGkUc8stDr8rJI5ReL7.m");

        String json = objectMapper.writeValueAsString(user);

        assertFalse(json.contains("password"), "响应 JSON 中不应出现 password 字段");
        assertFalse(json.contains("$2a$10$"), "响应 JSON 中不应出现密码哈希");
    }

    @Test
    @DisplayName("反序列化时不因缺少 password 字段而报错（其它接口复用 User 接收请求体）")
    void missingPasswordField_doesNotThrow() throws Exception {
        User user = objectMapper.readValue("{\"username\":\"onlyname\"}", User.class);

        assertEquals("onlyname", user.getUsername());
        assertNull(user.getPassword());
        assertTrue(true);
    }
}
