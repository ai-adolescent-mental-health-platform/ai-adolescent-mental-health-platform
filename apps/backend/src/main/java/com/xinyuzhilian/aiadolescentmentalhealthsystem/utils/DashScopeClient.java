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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 非流式 DashScope 对话客户端
 *
 * 复用 AiConsultationServiceImpl 的 HttpURLConnection 骨架，走同一个
 * OpenAI 兼容端点，区别是这里一次性读完整 body（非 SSE 逐行解析），
 * 供签到分析流水线使用。模型与文字咨询共用 dashscope.api.chat-model。
 */
@Component
@Slf4j
public class DashScopeClient {

    private static final String DASHSCOPE_API_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

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

        URL url = new URL(DASHSCOPE_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setDoOutput(true);

        String jsonBody = JSON.toJSONString(request);
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder body = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                body.append(line);
            }
        }

        if (code < 200 || code >= 300) {
            throw new IOException("DashScope API 返回 " + code + ": " + body);
        }
        return body.toString();
    }
}
