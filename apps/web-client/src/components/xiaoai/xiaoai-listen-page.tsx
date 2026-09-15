"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { api } from "@/lib/api";
import { getStoredUser } from "@/lib/session";
import type { UserProfile } from "@/lib/types";

// ========== Voice list ==========
const VOICE_LIST = [
  { voice: "Tina", name: "甜甜 Tina", desc: "温暖治愈系小姐姐", gender: "female" },
  { voice: "Liora Mira", name: "清欢 Liora", desc: "烟火人间的温柔", gender: "female" },
  { voice: "Sunnybobi", name: "知芝 Sunny", desc: "社恐邻家姑娘", gender: "female" },
  { voice: "Raymond", name: "林川 Raymond", desc: "爱吃外卖的宅男", gender: "male" },
  { voice: "Ethan", name: "晨煦 Ethan", desc: "阳光温暖活力少年", gender: "male" },
  { voice: "Theo Calm", name: "予安 Theo", desc: "静默处传递理解", gender: "male" },
  { voice: "Serena", name: "苏瑶 Serena", desc: "温柔小姐姐", gender: "female" },
  { voice: "Harvey", name: "厚 Harvey", desc: "岁月沉淀的大叔", gender: "male" },
  { voice: "Maia", name: "四月 Maia", desc: "知性与温柔碰撞", gender: "female" },
  { voice: "Evan", name: "江晨 Evan", desc: "年下奶狗男孩", gender: "male" },
  { voice: "Momo", name: "茉兔 Momo", desc: "撒娇搞怪小可爱", gender: "female" },
  { voice: "Angel", name: "安琪 Angel", desc: "台式甜美女孩", gender: "female" },
  { voice: "Mia", name: "舒然 Mia", desc: "温柔生活博主", gender: "female" },
  { voice: "Joyner", name: "阿逗 Joyner", desc: "搞笑接地气", gender: "male" },
  { voice: "Katerina", name: "卡捷琳娜", desc: "御姐韵律音色", gender: "female" },
  { voice: "Ryan", name: "甜茶 Ryan", desc: "戏感炸裂男孩", gender: "male" },
  { voice: "Jennifer", name: "詹妮弗", desc: "电影质感美语女声", gender: "female" },
  { voice: "Aiden", name: "艾登 Aiden", desc: "精通厨艺大男孩", gender: "male" },
  { voice: "Mione", name: "敏儿 Mione", desc: "成熟知性妹妹", gender: "female" },
  { voice: "Sohee", name: "素熙 Sohee", desc: "韩国开朗欧尼", gender: "female" },
  { voice: "Lenn", name: "莱恩 Lenn", desc: "理性叛逆德国青年", gender: "male" },
  { voice: "Andre", name: "安德雷 Andre", desc: "磁性沉稳男生", gender: "male" },
  { voice: "Roya", name: "萝雅 Roya", desc: "热爱运动女孩", gender: "female" },
  { voice: "Arda", name: "阿尔达 Arda", desc: "干净温润男生", gender: "male" },
  { voice: "Marina", name: "玛丽娜 Marina", desc: "多元文化女孩", gender: "female" },
];

// ========== Constants ==========
const WS_PATH = "/ws/omni-realtime";
const AUDIO_SAMPLE_RATE = 16000;
const MAX_IMAGE_SIZE = 5 * 1024 * 1024;

type ChatMessage = { id: string; role: string; content: string; timestamp: Date };

// ========== Helpers ==========
function generateId() {
  return `msg_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * 解析 WebSocket 基地址。
 *
 * HTTPS 页面禁止发起 ws:// 连接（浏览器按 mixed content 直接拦截，CSP 无法放行），
 * 因此安全页面下必须用 wss://，且要经 443 由边缘 nginx 升级转发到后端——
 * 后端 8080 是明文端口，wss://host:8080 同样连不上。
 *
 * 注：NEXT_PUBLIC_* 是构建期内联值，配置错了只能重新构建镜像才能修正，
 * 所以这里对 https 页面忽略非 wss 的配置，按同源推导，避免构建期误配直接打死线上。
 */
function resolveWsBase(): string {
  const configured = process.env.NEXT_PUBLIC_WS_BASE_URL;

  if (typeof window === "undefined") return configured || "ws://127.0.0.1:8080";

  if (window.location.protocol === "https:") {
    return configured?.startsWith("wss://") ? configured : `wss://${window.location.host}`;
  }

  // 本地开发：页面在 3300，后端 WS 在 8080，不能按同源推导
  return configured || "ws://127.0.0.1:8080";
}

function formatTime(date: Date) {
  return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
}

function formatRemaining(seconds: number) {
  if (seconds <= 0) return "00:00";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

function arrayBufferToBase64(buffer: ArrayBuffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary);
}

function writeString(view: DataView, offset: number, str: string) {
  for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i));
}

function pcmToWavBase64(pcmData: Int16Array, sampleRate = 16000) {
  const numChannels = 1;
  const bitsPerSample = 16;
  const dataSize = pcmData.byteLength;
  const headerSize = 44;
  const totalSize = headerSize + dataSize;

  const buffer = new ArrayBuffer(totalSize);
  const view = new DataView(buffer);
  const uint8 = new Uint8Array(buffer);

  writeString(view, 0, "RIFF");
  view.setUint32(4, totalSize - 8, true);
  writeString(view, 8, "WAVE");
  writeString(view, 12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, numChannels, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * numChannels * (bitsPerSample / 8), true);
  view.setUint16(32, numChannels * (bitsPerSample / 8), true);
  view.setUint16(34, bitsPerSample, true);
  writeString(view, 36, "data");
  view.setUint32(40, dataSize, true);

  const pcmBytes = new Uint8Array(pcmData.buffer, pcmData.byteOffset, pcmData.byteLength);
  uint8.set(pcmBytes, headerSize);

  let binary = "";
  for (let i = 0; i < uint8.length; i++) binary += String.fromCharCode(uint8[i]);
  return btoa(binary);
}

export function XiaoaiListenPage() {
  // Connection & recording state
  const [isConnected, setIsConnected] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);

  // Voice
  const [voiceIndex, setVoiceIndex] = useState(0);
  const [showVoiceSelector, setShowVoiceSelector] = useState(false);

  // Time tracking
  const [memberType, setMemberType] = useState(0);
  const [dailyLimit, setDailyLimit] = useState(0);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const [isTimeExpired, setIsTimeExpired] = useState(false);

  // Video mode
  const [isVideoMode, setIsVideoMode] = useState(false);
  const [videoPosition, setVideoPosition] = useState({ x: 20, y: 80 });
  const [previewImage, setPreviewImage] = useState("");

  // Refs
  const chatBoxRef = useRef<HTMLDivElement>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const scriptProcessorRef = useRef<ScriptProcessorNode | null>(null);
  const mediaStreamSourceRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const countdownTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const usageTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const videoStreamRef = useRef<MediaStream | null>(null);
  const wavHeaderSentRef = useRef(false);
  const reconnectCountRef = useRef(0);
  const isTimeExpiredRef = useRef(false);
  const remainingSecondsRef = useRef(0);
  const isRecordingRef = useRef(false);

  // Audio playback queue
  const audioQueueRef = useRef<AudioBuffer[]>([]);
  const isPlayingRef = useRef(false);

  // Video drag
  const dragRef = useRef<{ dragging: boolean; ox: number; oy: number }>({ dragging: false, ox: 0, oy: 0 });

  const currentVoice = VOICE_LIST[voiceIndex];

  // Keep refs in sync
  useEffect(() => { remainingSecondsRef.current = remainingSeconds; }, [remainingSeconds]);
  useEffect(() => { isRecordingRef.current = isRecording; }, [isRecording]);

  const scrollToBottom = useCallback(() => {
    if (chatBoxRef.current) chatBoxRef.current.scrollTop = chatBoxRef.current.scrollHeight;
  }, []);

  const addSystemMessage = useCallback((content: string) => {
    setChatMessages((prev) => [...prev, { id: generateId(), role: "系统", content, timestamp: new Date() }]);
  }, []);

  useEffect(() => { scrollToBottom(); }, [chatMessages, scrollToBottom]);

  // Initialize AudioContext
  const initAudioContext = useCallback(() => {
    if (audioContextRef.current) return audioContextRef.current;
    const Ctor = window.AudioContext || (window as unknown as Record<string, unknown>).webkitAudioContext as typeof AudioContext;
    if (!Ctor) { addSystemMessage("当前浏览器不支持音频播放"); return null; }
    audioContextRef.current = new Ctor({ sampleRate: AUDIO_SAMPLE_RATE });
    return audioContextRef.current;
  }, [addSystemMessage]);

  // Audio playback
  const playNextInQueue = useCallback(() => {
    if (audioQueueRef.current.length === 0 || isPlayingRef.current) return;
    isPlayingRef.current = true;
    const ctx = audioContextRef.current;
    if (!ctx) { isPlayingRef.current = false; return; }

    const audioBuffer = audioQueueRef.current.shift()!;
    const source = ctx.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(ctx.destination);
    source.onended = () => {
      isPlayingRef.current = false;
      if (audioQueueRef.current.length > 0) playNextInQueue();
    };
    source.start();
  }, []);

  const clearAudioQueue = useCallback(() => {
    audioQueueRef.current = [];
    isPlayingRef.current = false;
  }, []);

  const playAudio = useCallback((base64Audio: string) => {
    const ctx = initAudioContext();
    if (!ctx) return;
    try {
      const binaryString = atob(base64Audio);
      const len = binaryString.length;
      const bytes = new Uint8Array(len);
      for (let i = 0; i < len; i++) bytes[i] = binaryString.charCodeAt(i);
      const pcmData = new Int16Array(bytes.buffer, bytes.byteOffset, bytes.byteLength / 2);
      const audioBuffer = ctx.createBuffer(1, pcmData.length, 24000);
      const channelData = audioBuffer.getChannelData(0);
      for (let i = 0; i < pcmData.length; i++) channelData[i] = pcmData[i] / 32768;
      audioQueueRef.current.push(audioBuffer);
      playNextInQueue();
    } catch { /* ignore */ }
  }, [initAudioContext, playNextInQueue]);

  // Load time info
  const loadTimeInfo = useCallback(async () => {
    try {
      const user = getStoredUser<UserProfile>();
      const [memberRes, limitRes, remainRes] = await Promise.all([
        api.xiaoai.getMemberType(),
        api.xiaoai.getDailyLimit(),
        api.xiaoai.getRemainingTime(),
      ]);
      setMemberType((memberRes as unknown as number) ?? 0);
      setDailyLimit((limitRes as unknown as number) ?? 0);
      const rem = (remainRes as unknown as number) ?? 0;
      setRemainingSeconds(rem);
      remainingSecondsRef.current = rem;
    } catch {
      if (memberType === 0 && dailyLimit === 0) {
        setDailyLimit(300);
        setRemainingSeconds(300);
        remainingSecondsRef.current = 300;
      }
    }
  }, [memberType, dailyLimit]);

  // Load today messages
  const loadTodayMessages = useCallback(async () => {
    try {
      const res = await api.xiaoai.getTodayMessages();
      const msgs = res as unknown as { role: string; content: string; createTime?: string }[];
      if (msgs?.length) {
        setChatMessages((prev) => [
          ...prev,
          ...msgs.map((m) => ({
            id: generateId(),
            role: m.role === "user" ? "用户" : m.role === "assistant" ? "AI" : m.role,
            content: m.content,
            timestamp: m.createTime ? new Date(m.createTime) : new Date(),
          })),
        ]);
      }
    } catch { /* ignore */ }
  }, []);

  // Report usage
  const reportUsage = useCallback(async () => {
    if (remainingSecondsRef.current <= 0) return;
    try { await api.xiaoai.reportUsageTime(5); } catch { /* ignore */ }
  }, []);

  // Time expired handler
  const handleTimeExpired = useCallback(() => {
    if (isTimeExpiredRef.current) return;
    isTimeExpiredRef.current = true;
    setIsTimeExpired(true);
    addSystemMessage("⏰ 今日免费时长已用完！");
    api.xiaoai.reportUsageTime(5).catch(() => {});
    if (isRecordingRef.current) {
      setIsRecording(false);
      cleanupAudioNodes();
    }
    if (wsRef.current) {
      wsRef.current.close(1000, "Time expired");
      wsRef.current = null;
    }
    setIsConnected(false);
    stopTimers();
  }, [addSystemMessage]);

  // Stop timers
  const stopTimers = useCallback(() => {
    if (countdownTimerRef.current) { clearInterval(countdownTimerRef.current); countdownTimerRef.current = null; }
    if (usageTimerRef.current) { clearInterval(usageTimerRef.current); usageTimerRef.current = null; }
  }, []);

  // Cleanup audio nodes
  const cleanupAudioNodes = useCallback(() => {
    if (mediaStreamSourceRef.current) { mediaStreamSourceRef.current.disconnect(); mediaStreamSourceRef.current = null; }
    if (scriptProcessorRef.current) {
      scriptProcessorRef.current.disconnect();
      scriptProcessorRef.current.onaudioprocess = null;
      scriptProcessorRef.current = null;
    }
    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((t) => t.stop());
      mediaStreamRef.current = null;
    }
    wavHeaderSentRef.current = false;
  }, []);

  // Handle WebSocket events
  const handleWsEvent = useCallback((data: Record<string, unknown>) => {
    const type = data.type as string;
    switch (type) {
      case "conversation.item.input_audio_transcription.completed": {
        const transcript = (data.transcript as string) || "";
        if (transcript) {
          setChatMessages((prev) => [...prev, { id: generateId(), role: "用户", content: transcript, timestamp: new Date() }]);
        }
        break;
      }
      case "response.audio_transcript.done": {
        const transcript = (data.transcript as string) || "";
        if (transcript) {
          setChatMessages((prev) => {
            const last = prev[prev.length - 1];
            if (last?.role === "AI") {
              return [...prev.slice(0, -1), { ...last, content: transcript }];
            }
            return [...prev, { id: generateId(), role: "AI", content: transcript, timestamp: new Date() }];
          });
        }
        break;
      }
      case "response.audio.delta":
        playAudio(data.delta as string);
        break;
      case "response.done":
        addSystemMessage("✅ 响应完成");
        break;
      case "session.created":
      case "session.updated":
        break;
      case "response.created":
        clearAudioQueue();
        break;
      case "input_audio_buffer.committed":
        addSystemMessage("📤 音频已提交，等待处理...");
        break;
      case "input_audio_buffer.speech_started":
        addSystemMessage("🎤 检测到语音开始...");
        break;
      case "input_audio_buffer.speech_stopped":
        addSystemMessage("🎤 检测到语音结束");
        break;
      case "session.init.ack":
        break;
      case "error":
        addSystemMessage(`错误: ${JSON.stringify(data.error)}`);
        break;
    }
  }, [addSystemMessage, playAudio, clearAudioQueue]);

  // Connect WebSocket
  const startConversation = useCallback(async () => {
    if (isConnecting || (wsRef.current && wsRef.current.readyState === WebSocket.OPEN)) return;

    // Check remaining time
    try {
      const rem = await api.xiaoai.getRemainingTime() as unknown as number;
      setRemainingSeconds(rem ?? 0);
      remainingSecondsRef.current = rem ?? 0;
      if ((rem ?? 0) <= 0) {
        setIsTimeExpired(true);
        isTimeExpiredRef.current = true;
        addSystemMessage("⏰ 时长已耗尽，无法开始通话");
        return;
      }
    } catch { /* continue */ }

    setIsConnecting(true);
    isTimeExpiredRef.current = false;
    setIsTimeExpired(false);

    const wsBase = resolveWsBase();
    const wsUrl = `${wsBase}${WS_PATH}`;
    const user = getStoredUser<UserProfile>();

    try {
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = () => {
        setIsConnected(true);
        setIsConnecting(false);
        reconnectCountRef.current = 0;
        addSystemMessage("连接成功，已初始化会话");

        if (user?.id) {
          ws.send(JSON.stringify({ type: "session.init", userId: user.id }));
        }

        // Countdown timer
        stopTimers();
        countdownTimerRef.current = setInterval(() => {
          const prev = remainingSecondsRef.current;
          if (prev > 0) {
            const next = prev - 1;
            remainingSecondsRef.current = next;
            setRemainingSeconds(next);
            if (next <= 0) handleTimeExpired();
          }
        }, 1000);

        // Usage report timer
        usageTimerRef.current = setInterval(async () => {
          if (remainingSecondsRef.current > 0) await reportUsage();
        }, 5000);

        // Session config
        ws.send(JSON.stringify({
          event_id: `sess${Date.now()}`,
          type: "session.update",
          session: {
            modalities: ["text", "audio"],
            voice: currentVoice.voice,
            input_audio_format: "pcm",
            output_audio_format: "pcm",
            input_audio_transcription: { model: "gummy-realtime-v1" },
            instructions: "你的名字是【小爱倾听师】，你隶属于【智能青少年健康心理系统】，你的服务对象是青少年。你是一名专业的青少年心理倾听陪伴者，不是心理医生，不做任何心理疾病诊断、不开药方、不提供医疗治疗，只做情绪倾听、接纳与疏导，帮助青少年缓解不开心、孤独、委屈、低落、抑郁等负面情绪，让他们感受到被理解、被陪伴，重拾轻松愉悦的心情。",
            turn_detection: { type: "server_vad" },
          },
        }));

        addSystemMessage("当前模式: 文本+语音（VAD自动检测）");
        setTimeout(() => {
          if (remainingSecondsRef.current > 0 && isTimeExpiredRef.current === false) {
            startRecordInternal(ws);
          }
        }, 500);
      };

      ws.onmessage = (event) => {
        try {
          handleWsEvent(JSON.parse(event.data as string));
        } catch { /* ignore */ }
      };

      ws.onclose = (e) => {
        setIsConnected(false);
        setIsConnecting(false);
        stopTimers();
        addSystemMessage(`连接已关闭 (代码: ${e.code})`);
        if (e.code !== 1000 && reconnectCountRef.current < 3) {
          reconnectCountRef.current++;
          addSystemMessage(`正在第${reconnectCountRef.current}次重连...`);
          setTimeout(() => startConversation(), 2000);
        }
      };

      ws.onerror = () => {
        setIsConnecting(false);
        addSystemMessage("连接出错，请检查后端服务是否启动");
      };
    } catch (err) {
      setIsConnecting(false);
      addSystemMessage(`创建连接失败: ${(err as Error).message}`);
    }
  }, [isConnecting, addSystemMessage, stopTimers, reportUsage, handleTimeExpired, handleWsEvent, currentVoice.voice]);

  // Start recording
  const startRecordInternal = useCallback(async (ws: WebSocket) => {
    if (!navigator.mediaDevices?.getUserMedia) {
      addSystemMessage("❌ 您的浏览器不支持录音功能");
      return;
    }
    try {
      addSystemMessage("🎤 正在请求麦克风权限...");
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { sampleRate: AUDIO_SAMPLE_RATE, channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
      mediaStreamRef.current = stream;

      const ctx = initAudioContext();
      if (!ctx) return;

      const source = ctx.createMediaStreamSource(stream);
      mediaStreamSourceRef.current = source;

      const processor = ctx.createScriptProcessor(4096, 1, 1);
      scriptProcessorRef.current = processor;

      processor.onaudioprocess = (e) => {
        if (!isRecordingRef.current) return;
        const inputData = e.inputBuffer.getChannelData(0);
        const int16 = new Int16Array(inputData.length);
        for (let i = 0; i < inputData.length; i++) {
          const s = Math.max(-1, Math.min(1, inputData[i]));
          int16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
        }
        if (ws.readyState !== WebSocket.OPEN) return;

        if (!wavHeaderSentRef.current) {
          const wavB64 = pcmToWavBase64(int16, AUDIO_SAMPLE_RATE);
          ws.send(JSON.stringify({
            event_id: `evt_${Date.now()}_audio`,
            type: "input_audio_buffer.append",
            audio: wavB64,
          }));
          wavHeaderSentRef.current = true;
        } else {
          const b64 = arrayBufferToBase64(int16.buffer);
          ws.send(JSON.stringify({
            event_id: `evt_${Date.now()}_audio`,
            type: "input_audio_buffer.append",
            audio: b64,
          }));
        }
      };

      source.connect(processor);
      processor.connect(ctx.destination);

      setIsRecording(true);
      addSystemMessage("🎤 开始说话，服务端VAD将自动检测语音结束...");
    } catch (err: unknown) {
      const error = err as DOMException;
      if (error.name === "NotAllowedError") {
        addSystemMessage("❌ 麦克风权限被拒绝");
      } else if (error.name === "NotFoundError") {
        addSystemMessage("❌ 未检测到麦克风设备");
      } else {
        addSystemMessage(`❌ 录音启动失败: ${error.message}`);
      }
    }
  }, [addSystemMessage, initAudioContext]);

  // Stop conversation
  const stopConversation = useCallback(() => {
    if (wsRef.current) {
      wsRef.current.close(1000, "User stop");
      wsRef.current = null;
    }
    cleanupAudioNodes();
    stopTimers();
    setIsConnected(false);
    setIsRecording(false);
    setPreviewImage("");
    reconnectCountRef.current = 0;
  }, [cleanupAudioNodes, stopTimers]);

  // Toggle mute
  const toggleMute = useCallback(() => {
    setIsMuted((prev) => {
      const next = !prev;
      addSystemMessage(next ? "已开启静音" : "已关闭静音");
      return next;
    });
  }, [addSystemMessage]);

  // Video mode
  const toggleVideoMode = useCallback(async () => {
    if (isVideoMode) {
      if (videoStreamRef.current) {
        videoStreamRef.current.getTracks().forEach((t) => t.stop());
        videoStreamRef.current = null;
      }
      setIsVideoMode(false);
      addSystemMessage("📹 视频模式已关闭");
    } else {
      try {
        addSystemMessage("正在请求摄像头权限...");
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { width: 640, height: 480 },
          audio: false,
        });
        videoStreamRef.current = stream;
        setIsVideoMode(true);
        setTimeout(() => {
          if (videoRef.current) videoRef.current.srcObject = stream;
        }, 100);
        addSystemMessage("✅ 视频模式已开启");
      } catch (err: unknown) {
        const error = err as DOMException;
        if (error.name === "NotAllowedError") {
          addSystemMessage("❌ 摄像头权限被拒绝");
        } else if (error.name === "NotFoundError") {
          addSystemMessage("❌ 未找到摄像头设备");
        } else {
          addSystemMessage(`❌ 视频模式开启失败: ${error.message}`);
        }
      }
    }
  }, [isVideoMode, addSystemMessage]);

  // Image upload
  const handleImageUpload = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      addSystemMessage("❌ 请先连接通话");
      return;
    }
    if (file.size > MAX_IMAGE_SIZE) {
      addSystemMessage(`⚠️ 图片大小${(file.size / 1024 / 1024).toFixed(1)}MB，处理可能需要时间...`);
    }
    const reader = new FileReader();
    reader.onload = (ev) => {
      const result = ev.target?.result as string;
      setPreviewImage(result);
      const base64 = result.split(",")[1];
      addSystemMessage(`图片读取完成，长度: ${base64.length} 字符`);
      const ws = wsRef.current!;
      const chunkSize = 16 * 1024;
      const sendChunk = (offset: number, index: number) => {
        if (offset >= base64.length) {
          addSystemMessage("图片发送完毕");
          return;
        }
        const end = Math.min(offset + chunkSize, base64.length);
        ws.send(JSON.stringify({
          event_id: `event_${Date.now()}_${index}`,
          type: "input_image_buffer.append",
          image: base64.substring(offset, end),
          index,
        }));
        setTimeout(() => sendChunk(end, index + 1), 0);
      };
      sendChunk(0, 0);
    };
    reader.readAsDataURL(file);
  }, [addSystemMessage]);

  // Drag video window
  const handleVideoMouseDown = useCallback((e: React.MouseEvent) => {
    dragRef.current = { dragging: true, ox: e.clientX - videoPosition.x, oy: e.clientY - videoPosition.y };
    const onMove = (ev: MouseEvent) => {
      if (!dragRef.current.dragging) return;
      setVideoPosition({ x: ev.clientX - dragRef.current.ox, y: ev.clientY - dragRef.current.oy });
    };
    const onUp = () => { dragRef.current.dragging = false; document.removeEventListener("mousemove", onMove); document.removeEventListener("mouseup", onUp); };
    document.addEventListener("mousemove", onMove);
    document.addEventListener("mouseup", onUp);
  }, [videoPosition]);

  // Init
  useEffect(() => {
    loadTimeInfo();
    loadTodayMessages();
  }, [loadTimeInfo, loadTodayMessages]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopConversation();
      if (audioContextRef.current) { audioContextRef.current.close(); audioContextRef.current = null; }
    };
  }, [stopConversation]);

  const memberLabel = memberType === 2 ? "SVIP" : memberType === 1 ? "VIP" : "";

  return (
    <div className="mx-auto flex h-[calc(100vh-80px)] max-w-[900px] flex-col px-4">
      {/* Header */}
      <header className="flex flex-wrap items-center justify-between gap-2 rounded-t-2xl px-4 py-3 sm:px-5 border border-[rgba(201,168,255,0.3)] bg-surface/90 backdrop-blur-xl">
        <div className="flex items-center gap-2 text-ink font-bold">
          <span className="text-lg">🪐</span>
          <span>小爱倾听 心理陪伴</span>
        </div>

        {/* Voice selector button */}
        <button
          onClick={() => setShowVoiceSelector(true)}
          className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm text-muted hover:bg-purple/10 transition-colors"
        >
          <span>{currentVoice.gender === "female" ? "👩" : "👨"}</span>
          <span>{currentVoice.name}</span>
          <span className="text-xs opacity-50">▼</span>
        </button>

        <div className="flex items-center gap-3">
          {dailyLimit > 0 && (
            <div className="text-sm">
              <span className="text-muted/70">剩余: </span>
              <span className={remainingSeconds < 10 ? "text-pink" : remainingSeconds < 60 ? "text-yellow" : "text-ink"}>
                {formatRemaining(remainingSeconds)}
              </span>
            </div>
          )}
          {memberLabel && <span className="text-xs px-1.5 py-0.5 rounded bg-yellow/20 text-yellow">{memberLabel}</span>}
          <button onClick={stopConversation} className="rounded-lg px-3 py-2 text-sm text-muted hover:bg-purple/10 transition-colors">
            退出
          </button>
        </div>
      </header>

      {/* Main chat area */}
      <main className="flex-1 overflow-hidden border-x border-[rgba(201,168,255,0.3)] bg-surface/70 backdrop-blur-md">
        <div className="flex items-center gap-2 px-4 py-2 border-b border-[rgba(201,168,255,0.15)]">
          <span className={`size-2 rounded-full ${isConnected ? "bg-mint" : "bg-purple/20"}`} />
          <span className="text-xs text-muted/70">{isConnecting ? "连接中..." : isConnected ? "已连接" : "未连接"}</span>
          {isRecording && <><span className="size-2 rounded-full bg-pink animate-pulse" /><span className="text-xs text-muted/70">通话中</span></>}
        </div>

        <div ref={chatBoxRef} className="h-full overflow-y-auto px-4 py-4 space-y-3">
          {chatMessages.length === 0 && (
            <div className="flex flex-col items-center justify-center h-full text-muted">
              <span className="text-3xl mb-3">✨</span>
              <span>点击下方按钮开始与 AI 对话</span>
            </div>
          )}
          {chatMessages.map((msg) => (
            <div key={msg.id} className={`flex items-start gap-2 ${msg.role === "AI" ? "" : msg.role === "用户" ? "flex-row-reverse" : "justify-center"}`}>
              <span className="text-lg shrink-0 mt-1">
                {msg.role === "AI" ? "🤖" : msg.role === "用户" ? "👤" : "📢"}
              </span>
              <div className={`max-w-[75%] rounded-xl px-4 py-2.5 text-sm ${
                msg.role === "AI" ? "cushion-card border border-[rgba(201,168,255,0.3)] bg-surface text-ink" :
                msg.role === "用户" ? "bg-purple text-[var(--on-accent)]" :
                "bg-transparent text-muted/70 text-xs"
              }`}>
                <p>{msg.content}</p>
                {msg.role !== "系统" && <span className="text-[10px] text-muted/70 mt-1 block">{formatTime(msg.timestamp)}</span>}
              </div>
            </div>
          ))}
        </div>
      </main>

      {/* Control bar */}
      <footer className="rounded-b-2xl border border-[rgba(201,168,255,0.3)] bg-surface/90 backdrop-blur-xl px-5 py-4 space-y-3">
        {/* Connect button */}
        <div className="flex justify-center">
          <button
            onClick={() => isConnected ? stopConversation() : startConversation()}
            disabled={isConnecting}
            className={`flex items-center gap-2 rounded-full px-8 py-3 font-bold text-sm transition-all ${
              isConnected
                ? "bg-pink/20 text-pink hover:bg-pink/30 border border-pink/30"
                : "bg-purple/20 text-blue hover:bg-purple/30 border border-purple/30"
            } disabled:opacity-50`}
          >
            <span>{isConnected ? "📞" : "🎤"}</span>
            <span>{isConnected ? "挂断通话" : "开始通话"}</span>
          </button>
        </div>

        {/* Controls when connected */}
        {isConnected && (
          <>
            <div className="flex justify-center gap-2 flex-wrap">
              <button onClick={toggleMute} className={`flex items-center gap-1.5 rounded-lg px-4 py-3 text-xs transition-colors ${isMuted ? "bg-pink/20 text-pink" : "bg-purple/10 text-muted hover:bg-purple/20"}`}>
                <span>{isMuted ? "🔇" : "🔊"}</span>
                <span>静音</span>
              </button>
              <button onClick={toggleVideoMode} className={`flex items-center gap-1.5 rounded-lg px-4 py-3 text-xs transition-colors ${isVideoMode ? "bg-purple/20 text-blue" : "bg-purple/10 text-muted hover:bg-purple/20"}`}>
                <span>{isVideoMode ? "📹" : "📺"}</span>
                <span>{isVideoMode ? "关闭视频" : "视频模式"}</span>
              </button>
              <label className="flex items-center gap-1.5 rounded-lg px-4 py-3 text-xs bg-purple/10 text-muted hover:bg-purple/20 cursor-pointer transition-colors">
                <span>🖼️</span>
                <span>上传图片</span>
                <input type="file" accept="image/*" onChange={handleImageUpload} className="hidden" />
              </label>
            </div>
            {previewImage && (
              <div className="relative inline-block">
                <img src={previewImage} alt="preview" className="h-16 rounded-lg" />
                <button onClick={() => setPreviewImage("")} className="absolute -top-1 -right-1 size-4 rounded-full bg-pink text-[var(--on-accent)] text-[10px] flex items-center justify-center">✕</button>
              </div>
            )}
          </>
        )}
      </footer>

      {/* Voice selector modal */}
      {showVoiceSelector && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-[rgba(58,46,92,0.5)] backdrop-blur-[2px]" onClick={() => setShowVoiceSelector(false)}>
          <div className="cushion-card rounded-card bg-surface/95 max-h-[70vh] w-[420px] max-w-[90vw] overflow-y-auto p-4" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-ink font-bold">选择 AI 音色</h3>
              <button onClick={() => setShowVoiceSelector(false)} className="text-muted hover:text-ink text-lg">✕</button>
            </div>
            <div className="grid gap-2">
              {VOICE_LIST.map((v, i) => (
                <button
                  key={v.voice}
                  onClick={() => { setVoiceIndex(i); setShowVoiceSelector(false); addSystemMessage(`已切换音色：${VOICE_LIST[i].name}`); }}
                  className={`flex items-center gap-3 rounded-lg p-2.5 text-left transition-colors ${i === voiceIndex ? "bg-purple/20 border border-purple/40" : "hover:bg-purple/10"}`}
                >
                  <span className="text-xl">{v.gender === "female" ? "👩" : "👨"}</span>
                  <div className="flex-1">
                    <div className="text-sm text-ink">{v.name}</div>
                    <div className="text-xs text-muted/70">{v.desc}</div>
                  </div>
                  {i === voiceIndex && <span className="text-blue">✓</span>}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Video PiP window */}
      {isVideoMode && (
        <div
          className="fixed z-40 w-[200px] rounded-xl overflow-hidden border border-[rgba(201,168,255,0.3)] bg-surface/90 shadow-lg"
          style={{ left: videoPosition.x, top: videoPosition.y }}
          onMouseDown={handleVideoMouseDown}
        >
          <div className="flex items-center justify-between px-2 py-1 bg-purple/10 text-[10px] text-muted cursor-move">
            <span>📹 摄像头预览</span>
            <button onClick={() => toggleVideoMode()} className="text-muted hover:text-ink">✕</button>
          </div>
          <div className="aspect-[4/3] bg-ink/10">
            <video ref={videoRef} autoPlay playsInline muted className="size-full object-cover" />
            {!videoStreamRef.current && (
              <div className="absolute inset-0 flex flex-col items-center justify-center text-muted/50 text-xs">
                <span>📷</span>
                <span>等待摄像头...</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
