"use client";

import { useEffect, useState, useRef, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Send, Image, User, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { IconButton } from "@/components/pouf/Button";
import { Avatar, AvatarFallback } from "@/components/pouf/Avatar";
import { Skeleton } from "@/components/pouf/Skeleton";
import { api } from "@/lib/api";
import { getToken } from "@/lib/session";
import { sseSubscribe } from "@/lib/sse";

interface ChatMessage {
  id: number;
  content: string;
  contentType: number; // 0=text, 1=image, 2=system
  senderId: number;
  senderName: string;
  senderAvatar?: string;
  createTime: string;
}

interface AppointmentDetail {
  id: number;
  psychologistName?: string;
  psychologistId?: number;
  psychologistAvatar?: string;
  status?: number;
}

export function ConsultationChatPage() {
  const { appointmentId } = useParams<{ appointmentId: string }>();
  const router = useRouter();
  const [detail, setDetail] = useState<AppointmentDetail | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [uploading, setUploading] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  // ponytail: reconnectRef removed — sseSubscribe handles reconnect internally
  // ponytail: maxReconnect removed — unused after reconnectRef cleanup

  const scrollToBottom = useCallback(() => {
    setTimeout(() => {
      scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
    }, 100);
  }, []);

  // Load appointment detail and message history
  useEffect(() => {
    const id = Number(appointmentId);
    if (!id) return;
    (async () => {
      try {
        const d = (await api.appointment.detail(id)) as unknown as AppointmentDetail;
        setDetail(d);
      } catch {
        setDetail(null);
      }
      try {
        const msgs = (await api.chat.history(id)) as unknown as ChatMessage[];
        setMessages(msgs);
      } catch { /* ignore */ }
      setLoading(false);
      scrollToBottom();
    })();
  }, [appointmentId, scrollToBottom]);

  // SSE connection：用 fetch + ReadableStream 携带 Authorization header，避免 JWT 进 URL
  useEffect(() => {
    const id = Number(appointmentId);
    if (!id) return;
    const token = getToken();
    const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api";
    const url = `${baseUrl.replace(/\/$/, "")}/psychologist/message/stream/${id}`;

    const sse = sseSubscribe({
      url,
      token,
      onMessage: (data) => {
        try {
          const msg = JSON.parse(data) as ChatMessage;
          if (msg.contentType === 2) {
            setMessages((prev) => [...prev, { ...msg, content: `[系统消息] ${msg.content}` }]);
          } else {
            setMessages((prev) => {
              if (prev.some((m) => m.id === msg.id)) return prev;
              return [...prev, msg];
            });
          }
          scrollToBottom();
        } catch { /* ignore */ }
      },
      onError: () => {
        // sseSubscribe handles reconnect internally
      },
    });

    return () => sse.close();
  }, [appointmentId, scrollToBottom]);

  const handleSend = async () => {
    const text = input.trim();
    if (!text || sending) return;
    const id = Number(appointmentId);
    if (!id || !detail?.psychologistId) return;
    setSending(true);
    try {
      await api.chat.send(id, detail.psychologistId, text);
      setInput("");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "发送失败");
    }
    setSending(false);
  };

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) { toast.error("图片不能超过 5MB"); return; }
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append("file", file);
      const token = getToken();
      const baseUrl = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api").replace(/\/$/, "");
      const res = await fetch(`${baseUrl}/common/upload`, {
        method: "POST",
        headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: formData,
      });
      const data = await res.json() as { code: number; data: string; message?: string };
      if (data.code === 200 && data.data) {
        const id = Number(appointmentId);
        if (id && detail?.psychologistId) {
          await api.chat.sendImage(id, detail.psychologistId, data.data);
        }
      } else {
        toast.error("图片上传失败");
      }
    } catch {
      toast.error("图片上传失败");
    }
    setUploading(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  if (loading) {
    return (
      <div className="mx-auto flex h-full max-w-3xl flex-col px-4">
        <Skeleton className="mb-4 h-5 w-24" />
        <Skeleton className="mb-4 h-16 w-full rounded-control" />
        <div className="flex-1 space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className={`h-12 w-3/4 rounded-control ${i % 2 === 0 ? "ml-auto" : ""}`} />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto flex h-[calc(100dvh-9rem)] max-w-3xl flex-col px-4">
      {/* Header */}
      <div className="flex items-center gap-4 border-b border-[rgba(201,168,255,0.3)] py-3 shrink-0">
        <Link href="/me/orders" className="text-muted transition-colors hover:text-purple">
          <ArrowLeft className="size-5" />
        </Link>
        <div className="flex min-w-0 flex-1 items-center gap-3">
          <Avatar className="size-9 shrink-0">
            {detail?.psychologistAvatar ? (
              <img src={detail.psychologistAvatar} alt="" className="size-9 rounded-full object-cover" />
            ) : (
              <AvatarFallback className="bg-purple/20 text-purple"><User className="size-4" /></AvatarFallback>
            )}
          </Avatar>
          <span className="text-sm font-bold text-ink">
            {detail?.psychologistName || "心理咨询师"}
          </span>
        </div>
      </div>

      {/* Messages */}
      <div ref={scrollRef} className="flex-1 space-y-3 overflow-y-auto py-4">
        {messages.length === 0 && (
          <div className="py-12 text-center text-sm font-bold text-muted">
            暂无消息，发送第一条消息开始沟通
          </div>
        )}
        {messages.map((msg) => {
          const isSystem = msg.contentType === 2;
          if (isSystem) {
            return (
              <div key={msg.id} className="flex justify-center">
                <span className="rounded-full bg-purple/10 px-3 py-1 text-xs font-bold text-muted">{msg.content}</span>
              </div>
            );
          }
          const isMine = msg.senderId !== detail?.psychologistId;
          return (
            <div key={msg.id} className={`flex gap-2 ${isMine ? "flex-row-reverse" : ""}`}>
              <Avatar className="mt-0.5 size-7 shrink-0">
                {msg.senderAvatar ? (
                  <img src={msg.senderAvatar} alt="" className="size-7 rounded-full object-cover" />
                ) : (
                  <AvatarFallback className="bg-purple/20 text-xs text-purple"><User className="size-3" /></AvatarFallback>
                )}
              </Avatar>
              <div className={`max-w-[70%] rounded-control px-3 py-2 text-sm ${
                isMine ? "bg-purple text-[var(--on-accent)]" : "cushion-card border border-[rgba(201,168,255,0.3)] bg-surface text-ink"
              }`}>
                {msg.contentType === 1 ? (
                  <img src={msg.content} alt="" className="max-w-full rounded-control" />
                ) : (
                  <p className="whitespace-pre-wrap break-words">{msg.content}</p>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Input */}
      <div className="flex items-end gap-2 border-t border-[rgba(201,168,255,0.3)] py-3 shrink-0">
        <label className="flex size-9 cursor-pointer items-center justify-center rounded-control text-muted transition-colors hover:bg-purple/10 hover:text-purple">
          {uploading ? <Loader2 className="size-5 animate-spin" /> : <Image className="size-5" />}
          <input type="file" accept="image/*" onChange={handleImageUpload} disabled={uploading} className="hidden" />
        </label>
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入消息..."
          rows={1}
          className="cushion-field min-h-0 flex-1 resize-none rounded-control bg-surface px-3 py-2 text-sm font-bold text-ink placeholder:text-muted"
        />
        <IconButton
          icon={<Send className="size-4" />}
          label="发送"
          tone="purple"
          size="sm"
          onClick={handleSend}
          disabled={!input.trim() || sending}
        />
      </div>
    </div>
  );
}
