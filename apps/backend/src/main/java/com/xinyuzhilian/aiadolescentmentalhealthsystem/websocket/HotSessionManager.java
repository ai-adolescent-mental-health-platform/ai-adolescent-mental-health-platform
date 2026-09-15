package com.xinyuzhilian.aiadolescentmentalhealthsystem.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 热会话池管理器
 * 
 * 功能：
 * 1. 应用启动时预热 N 个阿里云会话
 * 2. 用户连接时直接分配热会话，避免冷启动
 * 3. 心跳保活，自动检测不健康的会话
 * 4. 会话使用后归还热会话池复用
 */
@Slf4j
@Component
public class HotSessionManager {

    // ==================== 配置常量 ====================
    
    /** 热会话池最小数量 */
    private static final int MIN_HOT_SESSIONS = 0;
    
    /** 热会话池最大数量 */
    private static final int MAX_HOT_SESSIONS = 4;
    
    /** 心跳间隔（秒） */
    private static final int HEARTBEAT_INTERVAL_SECONDS = 25;
    
    /** 会话空闲超时（秒） */
    private static final int SESSION_TIMEOUT_SECONDS = 50;
    
    /** 预热超时（秒） */
    private static final int SESSION_WARMUP_TIMEOUT_SECONDS = 30;
    
    /** 阿里云 WebSocket URL 模板 */
    private static final String ALIYUN_WS_URL_TEMPLATE = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=%s";
    
    /** 使用的模型 */
    private static final String MODEL = "qwen-audio-3.0-realtime-plus";
    
    /** 系统提示词 - 小爱倾听师角色设定 */
    private static final String INSTRUCTIONS =
        "你的名字是【小爱倾听师】，你隶属于【智能青少年健康心理系统】，你的服务对象是青少年。\n\n" +
        "【核心定位】\n" +
        "你是一名专业的青少年心理倾听陪伴者，不是心理医生，不做任何心理疾病诊断、不开药方、不提供医疗治疗，只做情绪倾听、接纳与疏导，帮助青少年缓解不开心、孤独、委屈、低落、抑郁等负面情绪，让他们感受到被理解、被陪伴，重拾轻松愉悦的心情。\n\n" +
        "【核心原则】\n" +
        "1. 倾听优先：多听少说，先接住情绪，再温和回应，绝不打断、不敷衍\n" +
        "2. 无条件接纳：无论青少年表达多么消极、难过、愤怒，都不评判、不否定、不指责、不讲大道理\n" +
        "3. 情绪共情：精准回应情绪，而非急于解决问题，例如\"我能感受到你现在特别难过，好像心里堵得慌\"\n" +
        "4. 温和正向：不传播负面情绪，不强化抑郁、绝望感，用柔软的方式引导情绪舒缓\n" +
        "5. 保密安全：让青少年放心倾诉，告知对话是私密的，建立信任\n" +
        "6. 边界清晰：绝不越界做医疗诊断，遇到极端情况温和引导求助\n\n" +
        "【沟通语气与风格】\n" +
        "1. 语气：温柔、轻声、治愈、有耐心，适配语音/视频对话，口语化不生硬\n" +
        "2. 句式：简短温暖，不使用复杂专业术语，不说教、不灌输鸡汤\n" +
        "3. 节奏：缓慢、舒缓，符合情绪安抚的节奏，不急促\n" +
        "4. 态度：真诚、平等，像知心朋友一样陪伴\n\n" +
        "【禁止行为】\n" +
        "1. 禁止说\"你别想太多\"\"这点小事没必要难过\"\"你就是矫情\"等否定情绪的话\n" +
        "2. 禁止诊断：不说\"你这是抑郁症\"\"你有心理问题\"等专业判定\n" +
        "3. 禁止讲大道理、说教、指责、催促\n" +
        "4. 禁止传播消极、绝望、自伤自杀相关的负面引导\n" +
        "5. 禁止过度承诺，不做超出倾听陪伴的能力范围的事\n\n" +
        "【危机情况处理（自伤/自杀/极端抑郁）】\n" +
        "如果青少年表达想要伤害自己、放弃自己、极度绝望的想法：\n" +
        "1. 先共情：\"我知道你现在一定特别痛苦，才会有这样的想法，我很心疼你\"\n" +
        "2. 再温和引导：\"你的感受很重要，你也很珍贵，这种痛苦需要更专业的人来帮你，一定要告诉爸爸妈妈或者信任的老师，他们会好好保护你\"\n" +
        "3. 绝不鼓励、不强化极端想法，始终传递\"你很重要，值得被好好对待\"\n\n" +
        "【对话目标】\n" +
        "让青少年愿意敞开心扉，把负面情绪说出来，在倾诉中缓解压力，感受到陪伴与温暖，慢慢平复心情，变得轻松开心一点。";
    
    /**
     * 音频音色。
     * qwen-audio-3.0-realtime-plus 只支持 5 个系统音色：
     * longanqian(默认)、longanlingxin、longanlingxi、longanxiaoxin、longanlufeng。
     * 旧的 Ethan/Tina 等音色属于 Qwen3.5-Omni-Realtime 系列，对本模型无效。
     */
    private static final String VOICE = "longanqian";
    
    // ==================== Spring 配置 ====================
    
    @Value("${dashscope.api.key:}")
    private String apiKey;

    @Value("${dashscope.api.workspace:}")
    private String workspace;
    
    // ==================== 核心组件 ====================
    
    private OkHttpClient okHttpClient;
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    private ExecutorService executorService;
    
    // ==================== 会话池 ====================
    
    /** 热会话队列 */
    private final Queue<HotSession> hotSessionPool = new ConcurrentLinkedQueue<>();
    
    /** 统计指标 */
    private final AtomicLong totalSessionsCreated = new AtomicLong(0);
    private final AtomicLong hotSessionHits = new AtomicLong(0);
    private final AtomicLong hotSessionMisses = new AtomicLong(0);
    
    // ==================== 热会话类 ====================
    
    /**
     * 热会话封装类
     */
    public static class HotSession {
        /** 阿里云 WebSocket 连接 */
        public final WebSocket webSocket;
        
        /** 会话 ID */
        public String sessionId;
        
        /** 创建时间 */
        public final Instant createdTime;
        
        /** 最后活跃时间 */
        public volatile Instant lastActiveTime;
        
        /** 是否已就绪 */
        public volatile boolean isReady = false;
        
        /** 是否健康 */
        public volatile boolean isHealthy = true;
        
        /** 是否已连接 */
        public volatile boolean isConnected = true;
        
        /** 使用计数 */
        public final AtomicLong usageCount = new AtomicLong(0);
        
        /** 当前分配的前端 Session（用于消息转发） */
        public volatile jakarta.websocket.Session frontSession;
        
        /** 阿里云会话 ID */
        public volatile String aliyunSessionId;
        
        /** 消息回调（用于将阿里云消息转发给前端） */
        public volatile MessageCallback messageCallback;
        
        public HotSession(WebSocket webSocket) {
            this.webSocket = webSocket;
            this.createdTime = Instant.now();
            this.lastActiveTime = Instant.now();
        }
        
        public synchronized void markReady() {
            this.isReady = true;
            this.lastActiveTime = Instant.now();
            log.debug("热会话已就绪 - sessionId: {}, aliyunSessionId: {}", sessionId, aliyunSessionId);
        }
        
        public void updateActivity() {
            this.lastActiveTime = Instant.now();
        }
        
        public void markUnhealthy() {
            this.isHealthy = false;
        }
        
        public long getUsageCount() {
            return usageCount.get();
        }
        
        public long use() {
            return usageCount.incrementAndGet();
        }
        
        public boolean isIdleTimeout() {
            return Duration.between(lastActiveTime, Instant.now()).getSeconds() > SESSION_TIMEOUT_SECONDS;
        }
        
        public long getAliveSeconds() {
            return Duration.between(createdTime, Instant.now()).toSeconds();
        }
        
        /**
         * 分配给前端，激活热会话
         */
        public void assign(jakarta.websocket.Session frontSession, MessageCallback callback) {
            this.frontSession = frontSession;
            this.messageCallback = callback;
            this.updateActivity();
            log.info("热会话已分配 - sessionId: {}, frontSessionId: {}", sessionId, frontSession.getId());
        }
        
        /**
         * 释放热会话
         */
        public void release() {
            this.frontSession = null;
            this.messageCallback = null;
            log.debug("热会话已释放 - sessionId: {}", sessionId);
        }
    }
    
    /**
     * 消息回调接口
     */
    public interface MessageCallback {
        void onTextMessage(String text);
        void onBinaryMessage(byte[] data);
        void onError(String error);
    }
    
    // ==================== 生命周期 ====================
    
    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("热会话池管理器初始化中...");
        log.info("========================================");
        
        okHttpClient = new OkHttpClient.Builder()
                .pingInterval(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS))
                .build();
        
        executorService = Executors.newFixedThreadPool(MAX_HOT_SESSIONS, r -> {
            Thread t = new Thread(r, "hot-session-creator");
            t.setDaemon(true);
            return t;
        });
        
        warmUpSessions();
        
        log.info("========================================");
        log.info("热会话池管理器初始化完成！");
        log.info("  - 预热会话数: {}", MIN_HOT_SESSIONS);
        log.info("  - 最大会话数: {}", MAX_HOT_SESSIONS);
        log.info("========================================");
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("正在关闭热会话池管理器...");
        
        HotSession session;
        while ((session = hotSessionPool.poll()) != null) {
            try {
                session.webSocket.close(1000, "Server shutdown");
            } catch (Exception e) {
                log.debug("关闭热会话失败: {}", e.getMessage());
            }
        }
        
        executorService.shutdownNow();
        log.info("热会话池管理器已关闭");
    }
    
    // ==================== 核心方法 ====================

    /**
     * 构建 session.update JSON 消息
     */
    private String buildSessionUpdateMessage() {
        try {
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("modalities", Arrays.asList("audio", "text"));
            session.put("voice", VOICE);
            // 裸 PCM：输入 16kHz、输出 24kHz，均为 16bit 单声道
            session.put("input_audio_format", "pcm");
            session.put("output_audio_format", "pcm");
            session.put("instructions", INSTRUCTIONS);

            // turn_detection: 使用服务端 VAD 自动检测。
            // 注：qwen-audio-3.0-realtime-plus 只支持 server_vad 与 null(push-to-talk)，
            // semantic_vad 仅 Qwen3.5-Omni-Realtime 系列支持。
            Map<String, Object> turnDetection = new LinkedHashMap<>();
            turnDetection.put("type", "server_vad");
            session.put("turn_detection", turnDetection);

            // 刻意不传 input_audio_transcription：服务端默认即自动转录
            // （session.created 中为 qwen3-asr-flash-realtime），显式传入未支持的字段
            // 会触发服务端参数校验错误。

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("event_id", "warmup_" + System.currentTimeMillis());
            message.put("type", "session.update");
            message.put("session", session);

            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("构建 session.update 消息失败: {}", e.getMessage());
            return null;
        }
    }

    private void warmUpSessions() {
        for (int i = 0; i < MIN_HOT_SESSIONS; i++) {
            final int index = i + 1;
            executorService.submit(() -> createHotSession(index));
        }
    }
    
    /**
     * 创建热会话（异步）
     */
    private void createHotSession(int index) {
        log.info("[热会话#{}] 正在创建预热会话...", index);
        
        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean sessionReady = new AtomicBoolean(false);
            HotSession[] sessionHolder = new HotSession[1];
            
            String url = String.format(ALIYUN_WS_URL_TEMPLATE, MODEL);
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("User-Agent", "AIAdolescentMentalHealthSystem-HotSession/1.0");
            // 带业务空间归属的 Key 必须携带该头，否则鉴权/模型路由会失败
            if (workspace != null && !workspace.isEmpty()) {
                requestBuilder.addHeader("X-DashScope-WorkSpace", workspace);
            }
            Request request = requestBuilder.build();
            
            WebSocket webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
                
                @Override
                public void onOpen(WebSocket ws, Response response) {
                    log.info("[热会话#{}] 阿里云连接成功", index);
                    
                    HotSession session = new HotSession(ws);
                    sessionHolder[0] = session;
                    
                    // 发送 session.update 配置消息
                    String sessionUpdateMsg = buildSessionUpdateMessage();
                    if (sessionUpdateMsg != null) {
                        log.debug("[热会话#{}] 发送 session.update: {}", index, sessionUpdateMsg);
                        ws.send(sessionUpdateMsg);
                        log.debug("[热会话#{}] ✅ 已发送 session.update 配置", index);
                    } else {
                        log.error("[热会话#{}] ❌ 构建 session.update 消息失败", index);
                        ws.close(1011, "Failed to build session.update");
                    }
                }
                
                @Override
                public void onMessage(WebSocket ws, String text) {
                    try {
                        JsonNode root = objectMapper.readTree(text);
                        String type = root.has("type") ? root.get("type").asText() : "unknown";
                        
                        HotSession session = sessionHolder[0];
                        
                        // 如果有消息回调，转发消息
                        if (session != null && session.messageCallback != null) {
                            session.messageCallback.onTextMessage(text);
                        }
                        
                        if ("session.created".equals(type)) {
                            if (session != null && root.has("session") && root.get("session").has("id")) {
                                session.aliyunSessionId = root.get("session").get("id").asText();
                                session.sessionId = session.aliyunSessionId;
                                log.info("[热会话#{}] 收到 session.created - sessionId: {}", index, session.aliyunSessionId);
                            }
                        } else if ("session.updated".equals(type)) {
                            if (session != null && !session.isReady) {
                                session.markReady();
                                
                                log.info("[热会话#{}] ✅ 预热成功！sessionId: {}, 耗时: {}ms", 
                                        index, session.sessionId, 
                                        Duration.between(session.createdTime, Instant.now()).toMillis());
                                
                                hotSessionPool.offer(session);
                                totalSessionsCreated.incrementAndGet();
                                sessionReady.set(true);
                                latch.countDown();
                            }
                        } else if ("error".equals(type)) {
                            log.warn("[热会话#{}] ⚠️ 阿里云错误: {}", index, text);
                        } else if ("response.created".equals(type) || "response.done".equals(type)) {
                            log.debug("[热会话#{}] 收到预热响应: {}", index, type);
                        }
                    } catch (Exception e) {
                        log.debug("[热会话#{}] 解析消息失败: {}", index, e.getMessage());
                    }
                }
                
                @Override
                public void onMessage(WebSocket ws, ByteString bytes) {
                    HotSession session = sessionHolder[0];
                    if (session != null && session.messageCallback != null) {
                        session.messageCallback.onBinaryMessage(bytes.toByteArray());
                    }
                    log.debug("[热会话#{}] 收到二进制消息: {} bytes", index, bytes.size());
                }
                
                @Override
                public void onClosing(WebSocket ws, int code, String reason) {
                    log.info("[热会话#{}] 阿里云连接关闭中 - Code: {}, Reason: {}", index, code, reason);
                    ws.close(code, reason);
                }
                
                @Override
                public void onClosed(WebSocket ws, int code, String reason) {
                    log.info("[热会话#{}] 阿里云连接已关闭 - Code: {}, Reason: {}", index, code, reason);
                    HotSession session = sessionHolder[0];
                    if (session != null) {
                        session.isConnected = false;
                        session.isHealthy = false;
                        // 如果有回调，通知错误
                        if (session.messageCallback != null) {
                            session.messageCallback.onError("Connection closed: " + reason);
                        }
                    }
                    if (!sessionReady.get()) {
                        latch.countDown();
                    }
                }
                
                @Override
                public void onFailure(WebSocket ws, Throwable t, Response response) {
                    String errorDetail = "Unknown";
                    if (response != null) {
                        try {
                            errorDetail = response.body() != null ? response.body().string() : "No body";
                        } catch (Exception ignored) {}
                    }
                    log.error("[热会话#{}] ❌ 阿里云连接失败: {}", index, errorDetail);
                    
                    HotSession session = sessionHolder[0];
                    if (session != null) {
                        session.isHealthy = false;
                        if (session.messageCallback != null) {
                            session.messageCallback.onError("Connection failed: " + errorDetail);
                        }
                    }
                    if (!sessionReady.get()) {
                        latch.countDown();
                    }
                }
            });
            
            // 等待会话就绪或超时
            boolean waitResult = latch.await(SESSION_WARMUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            if (!waitResult || !sessionReady.get()) {
                log.warn("[热会话#{}] ⚠️ 预热超时或失败，关闭连接", index);
                try {
                    webSocket.close(1000, "Warmup timeout");
                } catch (Exception ignored) {}
            }
            
        } catch (Exception e) {
            log.error("[热会话#{}] ❌ 创建预热会话异常: {}", index, e.getMessage(), e);
        }
    }
    
    /**
     * 获取热会话
     */
    public HotSession getHotSession() {
        HotSession session = hotSessionPool.poll();
        
        if (session != null) {
            if (session.isHealthy && session.isConnected && session.isReady) {
                session.use();
                hotSessionHits.incrementAndGet();
                log.info("[会话池] 🔥 分配热会话 - session_id: {}, 使用次数: {}, 存活: {}s", 
                        session.sessionId, session.getUsageCount(), session.getAliveSeconds());
                
                executorService.submit(() -> createHotSession((int) totalSessionsCreated.incrementAndGet()));
                return session;
            } else {
                log.warn("[会话池] 热会话不健康，关闭 - session_id: {}, 健康: {}, 连接: {}, 就绪: {}", 
                        session.sessionId, session.isHealthy, session.isConnected, session.isReady);
                try {
                    session.webSocket.close(1000, "Unhealthy session");
                } catch (Exception ignored) {}
                
                hotSessionMisses.incrementAndGet();
                executorService.submit(() -> createHotSession((int) totalSessionsCreated.incrementAndGet()));
            }
        }
        
        hotSessionMisses.incrementAndGet();
        log.info("[会话池] 无可用热会话 - hits: {}, misses: {}", 
                hotSessionHits.get(), hotSessionMisses.get());
        return null;
    }
    
    /**
     * 归还热会话
     * 
     * 重要：为保证会话状态干净，归还会话时直接关闭旧会话，而不是在池中复用。
     * 因为 Realtime API 不提供会话状态重置接口，复用旧会话会残留上一轮的对话上下文
     * 与声学状态（VAD 判定、已提交的对话项），影响下一轮体验。
     */
    public void returnSession(HotSession session) {
        if (session == null) return;
        
        String oldSessionId = session.sessionId;
        
        // 释放分配
        session.release();
        
        // 关闭旧会话（不复用，确保下次分配的是全新会话）
        log.info("[会话池] 归还热会话，关闭旧会话 - session_id: {}, 避免状态污染", oldSessionId);
        try {
            session.webSocket.close(1000, "Session returned, closing for fresh start");
        } catch (Exception e) {
            log.debug("[会话池] 关闭旧会话时异常: {}", e.getMessage());
        }
        
        // 异步创建新会话补充池
        executorService.submit(() -> createHotSession((int) totalSessionsCreated.incrementAndGet()));
    }
    
    /**
     * 心跳保活
     * 作用：
     * 1. 发送应用层心跳消息，防止阿里云300秒超时关闭
     * 2. 发送心跳后重置 lastActiveTime，避免被判定为空闲超时
     * 3. 检查空闲超时会话并关闭
     * 4. 池数量不足时补充会话
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_SECONDS * 1000L)
    public void heartbeat() {
        int currentSize = hotSessionPool.size();
        
        if (currentSize < MIN_HOT_SESSIONS) {
            int needed = MIN_HOT_SESSIONS - currentSize;
            log.info("[心跳] 热会话池数量不足({}/{}), 补充 {} 个",
                    currentSize, MIN_HOT_SESSIONS, needed);
            
            for (int i = 0; i < needed; i++) {
                executorService.submit(() -> createHotSession((int) totalSessionsCreated.incrementAndGet()));
            }
        }
        
        // 向所有热会话发送心跳消息，保持连接活跃
        Iterator<HotSession> iterator = hotSessionPool.iterator();
        while (iterator.hasNext()) {
            HotSession session = iterator.next();
            
            // 发送应用层心跳（阿里云需要每几十秒有消息交互，否则会超时）
            try {
                // 发送一个轻量的 session.update 消息（不改变配置，只是保持连接）
                String heartbeatMsg = "{\"event_id\":\"heartbeat_" + System.currentTimeMillis() + "\",\"type\":\"session.update\",\"session\":{}}";
                session.webSocket.send(heartbeatMsg);

                
                // 发送心跳后立即重置 lastActiveTime，避免被判定为空闲超时
                session.updateActivity();


            } catch (Exception e) {
                log.warn("[心跳] 发送心跳失败，标记会话不健康 - session_id: {}, error: {}", 
                        session.sessionId, e.getMessage());
                iterator.remove();
                try {
                    session.webSocket.close(1000, "Heartbeat failed");
                } catch (Exception ignored) {}
                executorService.submit(() -> createHotSession((int) totalSessionsCreated.incrementAndGet()));
            }
        }
        
//        if (log.isInfoEnabled()) {
//            log.info("[心跳] 会话池状态 - 池大小: {}, 总创建: {}, 命中: {}, 未命中: {}",
//                    hotSessionPool.size(),
//                    totalSessionsCreated.get(),
//                    hotSessionHits.get(),
//                    hotSessionMisses.get());
//        }
    }
    
    public Map<String, Object> getPoolStatus() {
        long total = hotSessionHits.get() + hotSessionMisses.get();
        double hitRate = total > 0 ? (double) hotSessionHits.get() / total * 100 : 0;
        
        return Map.of(
                "pool_size", hotSessionPool.size(),
                "total_created", totalSessionsCreated.get(),
                "hit_count", hotSessionHits.get(),
                "miss_count", hotSessionMisses.get(),
                "hit_rate", String.format("%.1f%%", hitRate)
        );
    }
}
