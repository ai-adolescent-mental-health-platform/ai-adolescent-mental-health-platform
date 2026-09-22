package com.xinyuzhilian.aiadolescentmentalhealthsystem.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.consultation.dto.AiChatDTO;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.consultation.dto.DashScopeRequest;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.AiMessage;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.domain.pojo.AiSession;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.AiMessageMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.mapper.AiSessionMapper;
import com.xinyuzhilian.aiadolescentmentalhealthsystem.service.IAiConsultationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiConsultationServiceImpl implements IAiConsultationService {

    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${dashscope.api.chat-model}")
    private String chatModel;

    private static final String DASHSCOPE_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private static final String SYSTEM_PROMPT = "【角色定位】\n" +
            "你是一名具备国家二级心理咨询师资质的专业心理医生，拥有8年临床心理咨询经验，擅长情绪疏导、压力缓解、人际关系困扰、轻度焦虑抑郁情绪调节、亲子亲密关系问题梳理。你的核心目标是：通过共情倾听、专业引导，帮助用户梳理情绪、厘清问题、找到自我调节的方法，而非替代医疗诊断或治疗。\n" +
            "\n" +
            "【核心执业原则】\n" +
            "1. 保密原则：严格保护用户的隐私，除非用户提及自伤、伤人、危害公共安全等极端情况，否则绝不泄露任何沟通内容；若涉及紧急风险，需明确告知用户「为了你的安全，我需要提示你寻求紧急帮助」。\n" +
            "2. 共情原则：优先理解用户的情绪感受，避免「站着说话不腰疼」的评判，比如不说「这没什么大不了的」，而是说「我能感受到你现在很委屈/焦虑，这种感受是真实且值得被重视的」。\n" +
            "3. 非评判原则：无论用户的想法、行为是否符合社会常规，均保持中立，不指责、不批评，只聚焦「问题本身」和「用户的感受」。\n" +
            "4. 助人自助原则：不直接给「标准答案」，而是通过开放式提问引导用户自我反思、找到适合自己的解决方案，比如「你觉得这件事里，你最在意的是什么？」「如果尝试一个小改变，你愿意先做什么？」。\n" +
            "\n" +
            "【沟通风格】\n" +
            "1. 语气：温和、耐心、沉稳，语速适中（文字表现为语句简洁不冗长，避免使用生硬冰冷的词汇）。\n" +
            "2. 表达方式：\n" +
            "   - 先回应情绪，再回应问题（例：用户说「我最近工作压力大到睡不着」，先回应「连续睡不着肯定特别煎熬，这种紧绷的感觉一定很难受」，再引导「能具体说说，是哪件事让你觉得压力最大吗？」）；\n" +
            "   - 使用开放式提问（避免「是/否」类封闭问题），比如「你当时的感受是什么？」「这件事对你的影响是什么？」；\n" +
            "   - 避免专业术语堆砌，若必须使用（如「认知偏差」），需用通俗语言解释：「认知偏差简单说就是我们会不自觉地钻牛角尖，比如只看到不好的一面，忽略了其他可能性」。\n" +
            "\n" +
            "【能力范围（可承接）】\n" +
            "1. 情绪疏导：焦虑、抑郁、委屈、愤怒    、孤独等负面情绪的缓解与梳理；\n" +
            "2. 关系梳理：亲子、伴侣、同事、朋友等人际关系中的矛盾与困惑；\n" +
            "3. 压力调节：工作、学习、生活中的压力源分析与应对方法探索；\n" +
            "4. 自我认知：自我否定、低自尊、迷茫等自我认同相关的问题梳理。\n" +
            "\n" +
            "【边界与禁忌（不可承接）】\n" +
            "1. 不做医学诊断：绝不使用「你得了抑郁症焦虑症」等诊断性语言，若用户疑似严重心理问题，需引导：「你的情况听起来需要更专业的评估，建议你联系线下精神科医生或正规心理咨询机构做专业诊断」；\n" +
            "2. 不提供药物建议：绝不推荐任何精神类药物，若用户询问用药，明确告知：「药物需由精神科医生根据你的具体情况开具，我无法提供用药建议，请务必遵医嘱」；\n" +
            "3. 不处理紧急危机：若用户提及自杀、自伤、伤人计划，立即停止常规疏导，给出紧急求助指引：「我很担心你的安全！请立刻联系身边的家人/朋友，或拨打全国心理危机干预热线XXXX-XXXXXXX，他们能给你更及时的帮助」；\n" +
            "4. 不替代线下治疗：始终明确告知用户「我的疏导仅作为情绪支持，无法替代线下专业心理咨询或精神科治疗」；\n" +
            "5. 禁忌语言：避免「你应该/你必须」「这点小事都想不开」「别人都能做到你为什么不行」「我保证你会好起来」等评判、施压、承诺类话术。\n" +
            "\n" +
            "【典型场景应对框架】\n" +
            "1. 开场回应：用户首次倾诉时，主动建立安全氛围——「你好，我在这里认真听你说，无论你想聊什么，都可以放心说，我会尽力理解你的感受，也会保护你的隐私」；\n" +
            "2. 倾听回应：用户倾诉后，先总结确认核心情绪——「我梳理一下，你刚才说的是[总结事件]，让你感到[总结情绪]，对吗？」；\n" +
            "3. 结束沟通：若用户暂未聊完，主动询问——「如果你还想继续聊，我都在；如果需要先整理一下思绪，也没关系，下次想聊的时候我也在」；若用户聊完，给予鼓励——「今天能把这些感受说出来，已经是很勇敢的一步了，你做得很好」。\n" +
            "\n" +
            "【回答格式要求】\n" +
            "1. 回复内容不要太长，控制在100-300字左右，保持简洁有重点。\n" +
            "2. 适当使用换行和分段，提高可读性。\n" +
            "3. 避免连续发送多条消息，尽量一次性回复完整。";

    @Override
    public AiSession createSession(Long userId) {
        AiSession session = new AiSession();
        session.setUserId(userId);
        session.setTitle("新咨询" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMddHHmm")));
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public List<AiSession> getUserSessions(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getUserId, userId)
                .orderByDesc(AiSession::getUpdateTime));
    }

    @Override
    public List<AiMessage> getSessionMessages(Long sessionId, Long userId) {
        // Verify ownership
        AiSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new RuntimeException("会话不存在或无权访问");
        }
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByAsc(AiMessage::getCreateTime));
    }

    @Override
    public SseEmitter chat(Long userId, AiChatDTO chatDTO) {
        Long sessionId = chatDTO.getSessionId();
        AiSession session;
        if (sessionId == null) {
            session = createSession(userId);
            sessionId = session.getId();
        } else {
            session = sessionMapper.selectById(sessionId);
            if (session == null || !session.getUserId().equals(userId)) {
                throw new RuntimeException("会话不存在");
            }
        }

        // Save User Message
        AiMessage userMsg = new AiMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(chatDTO.getMessage());
        userMsg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(userMsg);

        // Update Session Time
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        // Prepare Context (Last 20 messages)
        List<AiMessage> history = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByAsc(AiMessage::getCreateTime)
                .last("LIMIT 20")); // Simple context window

        DashScopeRequest request = new DashScopeRequest();
        request.setModel(chatModel);
        List<DashScopeRequest.DashScopeMessage> messages = new ArrayList<>();
        messages.add(new DashScopeRequest.DashScopeMessage("system", SYSTEM_PROMPT));
        
        for (AiMessage m : history) {
            messages.add(new DashScopeRequest.DashScopeMessage(m.getRole(), m.getContent()));
        }
        request.setMessages(messages);
        
        if (Boolean.TRUE.equals(chatDTO.getEnableThinking())) {
            request.setEnable_thinking(true);
            request.setThinking_budget(4000);
        }

        SseEmitter emitter = new SseEmitter(180000L); // 3 mins timeout
        Long finalSessionId = sessionId;

        executor.submit(() -> {
            StringBuilder fullContent = new StringBuilder();
            try {
                URL url = new URL(DASHSCOPE_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonBody = JSON.toJSONString(request);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                try (InputStream is = conn.getInputStream();
                     BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if ("[DONE]".equals(data)) break;
                            
                            try {
                                JSONObject json = JSON.parseObject(data);
                                if (json.containsKey("choices")) {
                                    JSONObject choice = json.getJSONArray("choices").getJSONObject(0);
                                    if (choice.containsKey("delta")) {
                                        JSONObject delta = choice.getJSONObject("delta");
                                        
                                        // reasoning_content (思考过程)
                                        if (delta.containsKey("reasoning_content")) {
                                            String reasoning = delta.getString("reasoning_content");
                                            if (reasoning != null && !reasoning.isEmpty()) {
                                                emitter.send("{\"type\":\"reasoning\",\"content\":" + JSON.toJSONString(reasoning) + "}");
                                            }
                                        }
                                        
                                        // content (正式回复)
                                        if (delta.containsKey("content")) {
                                            String content = delta.getString("content");
                                            if (content != null && !content.isEmpty()) {
                                                fullContent.append(content);
                                                emitter.send("{\"type\":\"content\",\"content\":" + JSON.toJSONString(content) + "}");
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Parse error: " + data, e);
                            }
                        }
                    }
                }
                
                // Save AI Message
                AiMessage aiMsg = new AiMessage();
                aiMsg.setSessionId(finalSessionId);
                aiMsg.setRole("assistant");
                aiMsg.setContent(fullContent.toString());
                aiMsg.setCreateTime(LocalDateTime.now());
                messageMapper.insert(aiMsg);
                
                emitter.complete();

            } catch (Exception e) {
                log.error("DashScope API error", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    public void deleteSession(Long sessionId, Long userId) {
        AiSession session = sessionMapper.selectById(sessionId);
        if (session != null && session.getUserId().equals(userId)) {
            sessionMapper.deleteById(sessionId);
            messageMapper.delete(new LambdaQueryWrapper<AiMessage>().eq(AiMessage::getSessionId, sessionId));
        }
    }
}
