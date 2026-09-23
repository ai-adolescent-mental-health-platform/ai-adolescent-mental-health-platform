package com.xinyuzhilian.aiadolescentmentalhealthsystem.utils;

import com.alibaba.fastjson2.JSON;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.consultation.dto.DashScopeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 非流式 DashScope 对话客户端
 *
 * 与 xiaoai 流式对话（AiConsultationServiceImpl）共用
 * {@link DashScopeHttpSupport} 的连接骨架，走同一个 OpenAI 兼容端点，
 * 区别是这里一次性读完整 body（非 SSE 逐行解析），供签到分析流水线使用。
 * 模型与文字咨询共用 dashscope.api.chat-model。
 */
@Component
@Slf4j
public class DashScopeClient {

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${dashscope.api.chat-model:qwen3-max}")
    private String chatModel;

    @Value("${checkin.analysis.connect-timeout:30000}")
    private int connectTimeout;

    @Value("${checkin.analysis.read-timeout:30000}")
    private int readTimeout;

    @Value("${checkin.analysis.enable-thinking:true}")
    private boolean enableThinking;

    /**
     * 非流式调用，一次性读完整响应 body，返回原始 JSON 字符串。
     *
     * @param systemPrompt 系统提示词
     * @param userContent  用户内容
     * @return 响应 body 的原始 JSON 字符串
     * @throws IOException 网络或 HTTP 错误
     */
    public String chat(String systemPrompt, String userContent) throws IOException {
        DashScopeRequest request = new DashScopeRequest();
        request.setModel(chatModel);
        request.setStream(false);
        request.setEnable_thinking(enableThinking);

        List<DashScopeRequest.DashScopeMessage> messages = new ArrayList<>();
        messages.add(new DashScopeRequest.DashScopeMessage("system", systemPrompt));
        messages.add(new DashScopeRequest.DashScopeMessage("user", userContent));
        request.setMessages(messages);

        // 共享骨架：与 xiaoai 流式对话走同一份连接建立逻辑
        String jsonBody = JSON.toJSONString(request);
        HttpURLConnection conn = DashScopeHttpSupport.openJsonPost(apiKey, jsonBody, connectTimeout, readTimeout);

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder body = new StringBuilder();
        // 错误分支的 errorStream 可能为 null，直接交给 InputStreamReader 会 NPE 并掩盖真实状态码
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    body.append(line);
                }
            }
        }

        if (code < 200 || code >= 300) {
            throw new IOException("DashScope API 返回 " + code + ": " + body);
        }
        return body.toString();
    }
}
