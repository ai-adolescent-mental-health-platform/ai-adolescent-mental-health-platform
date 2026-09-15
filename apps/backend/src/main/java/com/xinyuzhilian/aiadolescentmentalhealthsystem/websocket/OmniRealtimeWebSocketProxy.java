package com.xinyuzhilian.aiadolescentmentalhealthsystem.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.XiaoaiRecordService;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 代理 - 支持热会话池
 */
@Slf4j
@ServerEndpoint("/ws/omni-realtime")
@Component
public class OmniRealtimeWebSocketProxy {
    
    // ==================== 常量配置 ====================
    
    private static final String ALIYUN_WS_URL_TEMPLATE = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=%s";
    private static final String PRIMARY_MODEL = "qwen-audio-3.0-realtime-plus";
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    
    // ==================== 静态变量 ====================
    
    private static String apiKeyStatic;
    
    @Value("${dashscope.api.key:}")
    public void setApiKey(String apiKey) {
        apiKeyStatic = apiKey;
        log.info("[DashScope] API Key 已加载: {}", apiKey != null && !apiKey.isEmpty() ? "yes" : "null");
    }

    private static String workspaceStatic;

    @Value("${dashscope.api.workspace:}")
    public void setWorkspace(String workspace) {
        workspaceStatic = workspace;
        log.info("[DashScope] 业务空间 ID(X-DashScope-WorkSpace): {}",
                workspace != null && !workspace.isEmpty() ? workspace : "未配置");
    }
    
    private static HotSessionManager hotSessionManager;

    private static XiaoaiRecordService recordService;

    @Autowired
    public void setHotSessionManager(HotSessionManager manager) {
        hotSessionManager = manager;
        log.info("[热会话] HotSessionManager 已注入");
    }

    @Autowired
    public void setRecordService(XiaoaiRecordService service) {
        recordService = service;
        log.info("[记录] XiaoaiRecordService 已注入");
    }
    
    // ==================== 实例变量 ====================
    
    private OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .pingInterval(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS))
            .build();
    
    /** 新建的会话 */
    private static final ConcurrentHashMap<Session, WebSocket> NEW_SESSION_MAP = new ConcurrentHashMap<>();
    
    /** 热会话 -> 前端 Session 映射 */
    private static final ConcurrentHashMap<Session, HotSessionManager.HotSession> HOT_SESSION_REVERSE_MAP = new ConcurrentHashMap<>();
    
    /** 会话元数据 */
    private static final ConcurrentHashMap<Session, SessionMetadata> METADATA_MAP = new ConcurrentHashMap<>();
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // ==================== 性能指标 ====================
    
    private static final AtomicLong connectionCounter = new AtomicLong(0);
    private static final AtomicLong messageCounter = new AtomicLong(0);
    
    // ==================== 内部类 ====================
    
    private static class SessionMetadata {
        public Instant frontConnectedTime = Instant.now();
        public Instant aliyunConnectedTime;
        public Instant firstResponseTime;
        public Instant lastActiveTime = Instant.now();
        public String aliyunSessionId;
        public String model;
        public boolean isHotSession = false;
        public long networkRttMs;
        public long ttfbMs;
        // 会话记录相关
        public Long userId;
        public Long recordSessionId;
        public StringBuilder currentTranscriptBuffer = new StringBuilder();
        public boolean isRecordingMessage = false;
        
        public void updateActivity() {
            this.lastActiveTime = Instant.now();
        }
        
        public long getElapsedMs() {
            return Duration.between(frontConnectedTime, Instant.now()).toMillis();
        }
        
        public long calculateTTFB() {
            if (firstResponseTime != null && aliyunConnectedTime != null) {
                return Duration.between(aliyunConnectedTime, firstResponseTime).toMillis();
            }
            return -1;
        }
    }
    
    // ==================== WebSocket 生命周期 ====================
    
    @OnOpen
    public void onOpen(Session frontSession) {
        long connectionId = connectionCounter.incrementAndGet();
        log.info("[连接#{}] 前端 WebSocket 连接请求 - FrontSessionId: {}", connectionId, frontSession.getId());
        
        METADATA_MAP.put(frontSession, new SessionMetadata());
        
        frontSession.setMaxTextMessageBufferSize(5 * 1024 * 1024);
        frontSession.setMaxBinaryMessageBufferSize(5 * 1024 * 1024);
        
        if (apiKeyStatic == null || apiKeyStatic.isEmpty()) {
            log.error("[连接#{}] ❌ DashScope API Key 未配置！", connectionId);
            closeWithError(frontSession, CloseReason.CloseCodes.CLOSED_ABNORMALLY, "服务器配置错误：API Key 未设置");
            return;
        }
        
        // 尝试获取热会话
        if (hotSessionManager != null) {
            HotSessionManager.HotSession hotSession = hotSessionManager.getHotSession();
            if (hotSession != null && hotSession.isReady) {
                // 使用热会话！
                HOT_SESSION_REVERSE_MAP.put(frontSession, hotSession);
                SessionMetadata metadata = METADATA_MAP.get(frontSession);
                metadata.isHotSession = true;
                metadata.aliyunSessionId = hotSession.aliyunSessionId;
                
                // 分配热会话
                hotSession.assign(frontSession, createMessageCallback(frontSession));
                
                log.info("[连接#{}] 🔥 使用热会话！session_id: {}, 使用次数: {}, 存活: {}s", 
                        connectionId, hotSession.sessionId, hotSession.getUsageCount(), hotSession.getAliveSeconds());
                
                // 通知前端
                notifySessionReady(frontSession, hotSession.aliyunSessionId);
                return;
            } else if (hotSession != null) {
                log.warn("[连接#{}] 热会话未就绪，关闭并创建新会话", connectionId);
                try {
                    hotSession.webSocket.close(1000, "Session not ready");
                } catch (Exception ignored) {}
            }
        }
        
        // 无热会话，创建新会话
        log.info("[连接#{}] 无热会话，创建新会话...", connectionId);
        createNewSession(frontSession, connectionId);
    }
    
    /**
     * 创建消息回调（用于热会话）
     */
    private HotSessionManager.MessageCallback createMessageCallback(Session frontSession) {
        return new HotSessionManager.MessageCallback() {
            @Override
            public void onTextMessage(String text) {
                try {
                    frontSession.getBasicRemote().sendText(text);
                    
                    // 统计消息
                    SessionMetadata metadata = METADATA_MAP.get(frontSession);
                    if (metadata != null) {
                        metadata.updateActivity();
                        
                        if (metadata.firstResponseTime == null) {
                            metadata.firstResponseTime = Instant.now();
                            metadata.ttfbMs = metadata.calculateTTFB();
                            log.info("[热会话消息] TTFB: {}ms", metadata.ttfbMs);
                        }
                        
                        // 解析并处理消息
                        handleAliYunMessage(frontSession, metadata, text);
                    }
                } catch (IOException e) {
                    log.error("[热会话消息] ❌ 转发到前端失败: {}", e.getMessage());
                }
            }
            
            @Override
            public void onBinaryMessage(byte[] data) {
                try {
                    frontSession.getBasicRemote().sendBinary(ByteBuffer.wrap(data));
                } catch (IOException e) {
                    log.error("[热会话二进制] 转发失败: {}", e.getMessage());
                }
            }
            
            @Override
            public void onError(String error) {
                log.error("[热会话错误] {}", error);
            }
        };
    }
    
    /**
     * 处理阿里云消息 - 用于存储会话和消息记录
     */
    private void handleAliYunMessage(Session frontSession, SessionMetadata metadata, String text) {
        try {
            JsonNode root = objectMapper.readTree(text);
            String type = root.has("type") ? root.get("type").asText() : "unknown";
            
            // 用户音频转录完成 - 存储用户消息
            if ("conversation.item.input_audio_transcription.completed".equals(type)) {
                String transcript = root.has("transcript") ? root.get("transcript").asText() : "";
                if (!transcript.isEmpty() && metadata.recordSessionId != null) {
                    recordService.recordMessage(metadata.recordSessionId, "user", transcript);
                    log.info("[消息记录] 用户消息已存储, sessionId: {}, content: {}", 
                            metadata.recordSessionId, transcript.substring(0, Math.min(50, transcript.length())));
                }
            }
            // AI 音频转录增量 - 开始记录AI回复
            else if ("response.audio_transcript.delta".equals(type)) {
                String delta = root.has("delta") ? root.get("delta").asText() : "";
                metadata.currentTranscriptBuffer.append(delta);
                metadata.isRecordingMessage = true;
            }
            // AI 音频转录完成 - 存储AI消息
            else if ("response.audio_transcript.done".equals(type)) {
                String transcript = root.has("transcript") ? root.get("transcript").asText() : "";
                if (metadata.currentTranscriptBuffer.length() > 0) {
                    transcript = metadata.currentTranscriptBuffer.toString();
                }
                if (!transcript.isEmpty() && metadata.recordSessionId != null) {
                    recordService.recordMessage(metadata.recordSessionId, "assistant", transcript);
                    log.info("[消息记录] AI消息已存储, sessionId: {}, content: {}", 
                            metadata.recordSessionId, transcript.substring(0, Math.min(50, transcript.length())));
                }
                metadata.currentTranscriptBuffer.setLength(0);
                metadata.isRecordingMessage = false;
            }
            // 响应完成 - 更新会话时长
            else if ("response.done".equals(type)) {
                if (metadata.recordSessionId != null) {
                    int duration = (int) Duration.between(metadata.frontConnectedTime, Instant.now()).toSeconds();
                    recordService.updateSessionDuration(metadata.recordSessionId, duration);
                    log.debug("[会话记录] 会话时长已更新, sessionId: {}, duration: {}s", metadata.recordSessionId, duration);
                }
            }
        } catch (Exception e) {
            log.debug("[消息处理] 非JSON消息或解析失败: {}", e.getMessage());
        }
    }
    
    /**
     * 通知前端会话已就绪
     */
    private void notifySessionReady(Session frontSession, String sessionId) {
        try {
            String sessionCreatedMsg = String.format(
                    "{\"type\":\"session.created\",\"session\":{\"id\":\"%s\"}}", sessionId);
            frontSession.getBasicRemote().sendText(sessionCreatedMsg);
            
            String sessionUpdatedMsg = "{\"type\":\"session.updated\",\"session\":{}}";
            frontSession.getBasicRemote().sendText(sessionUpdatedMsg);
            
            log.info("[连接] 会话就绪通知已发送 - FrontSessionId: {}, sessionId: {}", 
                    frontSession.getId(), sessionId);
        } catch (IOException e) {
            log.error("发送会话就绪通知失败: {}", e.getMessage());
        }
    }
    
    /**
     * 创建新的阿里云会话
     */
    private void createNewSession(Session frontSession, long connectionId) {
        String model = PRIMARY_MODEL;
        String aliyunWsUrl = String.format(ALIYUN_WS_URL_TEMPLATE, model);
        
        METADATA_MAP.get(frontSession).model = model;
        log.info("[连接#{}] 正在连接阿里云 - 模型: {}", connectionId, model);
        
        Request.Builder requestBuilder = new Request.Builder()
                .url(aliyunWsUrl)
                .addHeader("Authorization", "Bearer " + apiKeyStatic)
                .addHeader("User-Agent", "AIAdolescentMentalHealthSystem/1.0");
        // 带业务空间归属的 Key 必须携带该头，否则鉴权/模型路由会失败
        if (workspaceStatic != null && !workspaceStatic.isEmpty()) {
            requestBuilder.addHeader("X-DashScope-WorkSpace", workspaceStatic);
        }
        Request request = requestBuilder.build();
        
        WebSocket aliyunWs = okHttpClient.newWebSocket(request, new WebSocketListener() {
            
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                SessionMetadata metadata = METADATA_MAP.get(frontSession);
                if (metadata != null) {
                    metadata.aliyunConnectedTime = Instant.now();
                    metadata.networkRttMs = Duration.between(metadata.frontConnectedTime, metadata.aliyunConnectedTime).toMillis();
                }
                log.info("[连接#{}] ✅ 阿里云 WebSocket 连接成功！", connectionId);
                log.info("[连接#{}]   └─ 网络延迟: {}ms", connectionId, metadata != null ? metadata.networkRttMs : "N/A");
                
                NEW_SESSION_MAP.put(frontSession, webSocket);
            }
            
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                long msgId = messageCounter.incrementAndGet();
                SessionMetadata metadata = METADATA_MAP.get(frontSession);
                if (metadata != null) {
                    metadata.updateActivity();
                    
                    if (metadata.firstResponseTime == null) {
                        metadata.firstResponseTime = Instant.now();
                        metadata.ttfbMs = metadata.calculateTTFB();
                        log.info("[消息#{}] TTFB: {}ms", msgId, metadata.ttfbMs);
                    }
                    
                    // 处理消息存储
                    handleAliYunMessage(frontSession, metadata, text);
                }
                
                try {
                    frontSession.getBasicRemote().sendText(text);
                } catch (IOException e) {
                    log.error("[消息#{}] ❌ 转发到前端失败: {}", msgId, e.getMessage());
                }
            }
            
            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                log.debug("[二进制] 收到 {} bytes", bytes.size());
                try {
                    frontSession.getBasicRemote().sendBinary(ByteBuffer.wrap(bytes.toByteArray()));
                } catch (IOException e) {
                    log.error("转发二进制消息失败: {}", e.getMessage());
                }
            }
            
            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                log.info("[连接#{}] 阿里云连接关闭中 - Code: {}, Reason: {}", connectionId, code, reason);
                webSocket.close(code, reason);
            }
            
            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                SessionMetadata metadata = METADATA_MAP.get(frontSession);
                log.info("[连接#{}] 阿里云连接已关闭 - Code: {}, Reason: {}", connectionId, code, reason);
                log.info("[连接#{}] 📊 会话统计 - 总耗时: {}ms, TTFB: {}ms, network_rtt: {}ms",
                        connectionId,
                        metadata != null ? metadata.getElapsedMs() : -1,
                        metadata != null ? metadata.ttfbMs : -1,
                        metadata != null ? metadata.networkRttMs : -1);
                
                // 结束会话记录
                if (metadata != null && metadata.recordSessionId != null) {
                    try {
                        recordService.endSession(metadata.recordSessionId, "aliyun_closed:" + code);
                    } catch (Exception e) {
                        log.error("[会话记录] 结束会话失败: {}", e.getMessage());
                    }
                }
                
                try {
                    frontSession.close(new CloseReason(CloseReason.CloseCodes.getCloseCode(code), reason));
                } catch (IOException e) {
                    log.error("关闭前端连接失败", e);
                }
            }
            
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                String errorDetail = "Unknown";
                int httpCode = -1;
                
                if (response != null) {
                    httpCode = response.code();
                    try {
                        errorDetail = response.body() != null ? response.body().string() : "No body";
                    } catch (Exception ignored) {}
                }
                
                log.error("[连接#{}] ❌ 阿里云连接失败 - HTTP: {}, Error: {}", connectionId, httpCode, errorDetail);
                log.error("[连接#{}]   └─ Throwable: {}", connectionId, t.getMessage());
                
                // 结束会话记录
                SessionMetadata metadata = METADATA_MAP.get(frontSession);
                if (metadata != null && metadata.recordSessionId != null) {
                    try {
                        recordService.endSession(metadata.recordSessionId, "aliyun_failed:" + errorDetail);
                    } catch (Exception e) {
                        log.error("[会话记录] 结束会话失败: {}", e.getMessage());
                    }
                }
                
                closeWithError(frontSession, CloseReason.CloseCodes.SERVICE_RESTART, 
                        "连接失败: " + errorDetail);
            }
        });
    }
    
    @OnMessage
    public void onMessage(Session frontSession, String message) {
        SessionMetadata metadata = METADATA_MAP.get(frontSession);
        if (metadata != null) {
            metadata.updateActivity();
        }
        
        // 处理用户初始化消息（包含 userId）
        if (message.startsWith("{")) {
            try {
                JsonNode root = objectMapper.readTree(message);
                String type = root.has("type") ? root.get("type").asText() : "";
                
                // 处理 session.init 消息，创建会话记录
                if ("session.init".equals(type)) {
                    if (root.has("userId") && !root.get("userId").isNull()) {
                        Long userId = root.get("userId").asLong();
                        metadata.userId = userId;
                        
                        // 创建会话记录
                        if (recordService != null) {
                            try {
                                Long sessionId = recordService.createSession(userId);
                                metadata.recordSessionId = sessionId;
                                log.info("[会话记录] 会话已创建, userId: {}, sessionId: {}", userId, sessionId);
                                
                                // 发送确认消息给前端
                                String ackMsg = String.format(
                                        "{\"type\":\"session.init.ack\",\"recordSessionId\":%d}", sessionId);
                                frontSession.getBasicRemote().sendText(ackMsg);
                            } catch (Exception e) {
                                log.error("[会话记录] 创建会话失败: {}", e.getMessage());
                            }
                        }
                        return;
                    }
                }
            } catch (Exception e) {
                log.debug("[消息处理] 非JSON消息或解析失败: {}", e.getMessage());
            }
        }
        
        // 检查热会话
        HotSessionManager.HotSession hotSession = HOT_SESSION_REVERSE_MAP.get(frontSession);
        if (hotSession != null) {
            hotSession.updateActivity();
            hotSession.webSocket.send(message);
            log.debug("[发送] 热会话 - FrontSessionId: {}, length: {}", 
                    frontSession.getId(), message.length());
            return;
        }
        
        // 普通会话
        WebSocket aliyunWs = NEW_SESSION_MAP.get(frontSession);
        if (aliyunWs != null) {
            aliyunWs.send(message);
            log.debug("[发送] 新会话 - FrontSessionId: {}, length: {}", 
                    frontSession.getId(), message.length());
        } else {
            log.warn("[发送] 未找到 WebSocket - FrontSessionId: {}", frontSession.getId());
        }
    }
    
    @OnMessage
    public void onBinaryMessage(Session frontSession, byte[] data) {
        SessionMetadata metadata = METADATA_MAP.get(frontSession);
        if (metadata != null) {
            metadata.updateActivity();
        }
        
        HotSessionManager.HotSession hotSession = HOT_SESSION_REVERSE_MAP.get(frontSession);
        if (hotSession != null) {
            hotSession.updateActivity();
            hotSession.webSocket.send(ByteString.of(data));
            return;
        }
        
        WebSocket aliyunWs = NEW_SESSION_MAP.get(frontSession);
        if (aliyunWs != null) {
            aliyunWs.send(ByteString.of(data));
        }
    }
    
    @OnClose
    public void onClose(Session frontSession) {
        log.info("前端 WebSocket 关闭 - FrontSessionId: {}", frontSession.getId());
        
        SessionMetadata metadata = METADATA_MAP.get(frontSession);
        if (metadata != null) {
            log.info("📊 持续时间: {}ms, 是否热会话: {}", metadata.getElapsedMs(), metadata.isHotSession);
            
            // 结束会话记录
            if (metadata.recordSessionId != null && recordService != null) {
                try {
                    recordService.endSession(metadata.recordSessionId, "frontend_closed");
                } catch (Exception e) {
                    log.error("[会话记录] 结束会话失败: {}", e.getMessage());
                }
            }
        }
        
        // 归还热会话
        HotSessionManager.HotSession hotSession = HOT_SESSION_REVERSE_MAP.remove(frontSession);
        if (hotSession != null && hotSessionManager != null) {
            hotSessionManager.returnSession(hotSession);
            log.info("热会话已归还池中 - session_id: {}", hotSession.sessionId);
        }
        
        // 关闭普通会话
        WebSocket aliyunWs = NEW_SESSION_MAP.remove(frontSession);
        if (aliyunWs != null) {
            aliyunWs.close(1000, "Frontend closed");
        }
        
        METADATA_MAP.remove(frontSession);
    }
    
    @OnError
    public void onError(Session frontSession, Throwable error) {
        log.error("WebSocket 错误 - FrontSessionId: {}, Error: {}", 
                frontSession.getId(), error.getMessage());
        
        // 结束会话记录
        SessionMetadata metadata = METADATA_MAP.get(frontSession);
        if (metadata != null && metadata.recordSessionId != null && recordService != null) {
            try {
                recordService.endSession(metadata.recordSessionId, "error:" + error.getMessage());
            } catch (Exception e) {
                log.error("[会话记录] 结束会话失败: {}", e.getMessage());
            }
        }
        
        HotSessionManager.HotSession hotSession = HOT_SESSION_REVERSE_MAP.remove(frontSession);
        if (hotSession != null && hotSessionManager != null) {
            hotSessionManager.returnSession(hotSession);
        }
        
        WebSocket aliyunWs = NEW_SESSION_MAP.remove(frontSession);
        if (aliyunWs != null) {
            try {
                aliyunWs.close(1000, "Error");
            } catch (Exception ignored) {}
        }
        
        closeWithError(frontSession, CloseReason.CloseCodes.UNEXPECTED_CONDITION, 
                "发生错误: " + error.getMessage());
    }
    
    // ==================== 辅助方法 ====================
    
    private void closeWithError(Session session, CloseReason.CloseCodes code, String reason) {
        try {
            session.close(new CloseReason(code, reason));
            NEW_SESSION_MAP.remove(session);
            HOT_SESSION_REVERSE_MAP.remove(session);
            METADATA_MAP.remove(session);
        } catch (IOException e) {
            log.error("关闭连接失败", e);
        }
    }
    
    // ==================== 静态方法 ====================
    
    public static int getActiveSessionCount() {
        return NEW_SESSION_MAP.size() + HOT_SESSION_REVERSE_MAP.size();
    }
    
    public static Map<String, Object> getSessionStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("active_sessions", getActiveSessionCount());
        stats.put("new_sessions", NEW_SESSION_MAP.size());
        stats.put("hot_sessions", HOT_SESSION_REVERSE_MAP.size());
        stats.put("total_connections", connectionCounter.get());
        stats.put("total_messages", messageCounter.get());
        
        if (hotSessionManager != null) {
            stats.put("pool_status", hotSessionManager.getPoolStatus());
        }
        
        return stats;
    }
}
