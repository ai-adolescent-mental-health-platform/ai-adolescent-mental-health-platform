package com.xinyuzhilian.aiadolescentmentalhealthsystem.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * DashScope OpenAI 兼容端点的共享 HTTP 骨架。
 *
 * 小艾对话（流式 SSE）与签到分析（非流式）共用同一份连接建立与请求写入逻辑，
 * 避免两处各自维护一份 HttpURLConnection 骨架而产生漂移。
 * 调用方负责读取响应、关闭流。
 */
public final class DashScopeHttpSupport {

    public static final String CHAT_COMPLETIONS_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private DashScopeHttpSupport() {
    }

    /**
     * 建立 chat/completions 的 POST 连接并写入 JSON 请求体。
     * 返回时连接已建立、请求已发出，可直接读取响应码与响应流。
     *
     * @param apiKey         DashScope API Key
     * @param jsonBody       请求体 JSON
     * @param connectTimeout 连接超时（毫秒），&lt;= 0 表示沿用 JDK 默认
     * @param readTimeout    读取超时（毫秒），&lt;= 0 表示沿用 JDK 默认
     */
    public static HttpURLConnection openJsonPost(String apiKey, String jsonBody,
                                                 int connectTimeout, int readTimeout) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(CHAT_COMPLETIONS_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        if (connectTimeout > 0) {
            conn.setConnectTimeout(connectTimeout);
        }
        if (readTimeout > 0) {
            conn.setReadTimeout(readTimeout);
        }
        conn.setDoOutput(true);

        byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(input, 0, input.length);
        }
        return conn;
    }
}
