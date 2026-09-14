package com.xinyuzhilian.aiadolescentmentalhealthsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.User;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录接口端到端回归测试。
 *
 * 覆盖 POST /user/login 的完整链路：Security 放行 → @RequestBody User 反序列化
 * → AuthenticationManager 认证 → 生成 JWT → 写 Redis。
 *
 * 回归背景：User.password 曾用 @JsonIgnore 屏蔽序列化，但它同时阻断了反序列化，
 * 使登录请求的 password 恒为 null，认证抛异常后被通用兜底映射成 500。
 *
 * 测试自造数据并在结束后清理，不依赖库中既有用户（CI 的 user 表为空）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class LoginEndpointIntegrationTest {

    private static final String TEST_USERNAME = "it_login_regression";
    private static final String TEST_EMAIL = "it_login_regression@test.local";
    private static final String TEST_PASSWORD = "ItLogin#2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        userMapper.delete(new LambdaQueryWrapper<User>().eq(User::getUsername, TEST_USERNAME));
    }

    /** 造一个可登录的测试用户。注意 user.email 为 NOT NULL 且无默认值。 */
    private void insertTestUser() {
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setEmail(TEST_EMAIL);
        user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
        user.setRole(1);
        user.setStatus(1);
        user.setDeleted(false);
        userMapper.insert(user);
    }

    @Test
    @DisplayName("POST /user/login 有效凭据应返回 200 与 token（回归：曾因 @JsonIgnore 返回 500）")
    void login_withValidCredentials_returnsToken() throws Exception {
        insertTestUser();

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEST_USERNAME + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    @DisplayName("POST /user/login 错误密码不得返回 500，应是业务错误响应")
    void login_withWrongPassword_isNotServerError() throws Exception {
        insertTestUser();

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + TEST_USERNAME + "\",\"password\":\"definitely-wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
