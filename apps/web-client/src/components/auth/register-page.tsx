"use client";

import { useState, useRef, useEffect } from "react";
import type { FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Eye, EyeOff, KeyRound, Leaf, Mail, Send, Smartphone, User } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/pouf/Button";
import { AuthShell, Field, Input } from "./pouf-auth";
import { api } from "@/lib/api";

function getPasswordStrength(pwd: string): { score: number; label: string; color: string } {
  let score = 0;
  if (pwd.length >= 8) score++;
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score++;
  if (/\d/.test(pwd)) score++;
  if (/[^a-zA-Z\d]/.test(pwd)) score++;
  if (pwd.length >= 12) score++;

  if (score <= 1) return { score: 25, label: "弱", color: "bg-pink" };
  if (score <= 3) return { score: 60, label: "中", color: "bg-yellow" };
  return { score: 100, label: "强", color: "bg-mint" };
}

export function RegisterPage() {
  const router = useRouter();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPwd, setShowPwd] = useState(false);
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [phone, setPhone] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [loading, setLoading] = useState(false);
  const [codeCountdown, setCodeCountdown] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  const pwdStrength = getPasswordStrength(password);

  const handleSendCode = async () => {
    if (!email) {
      toast.warning("请先输入邮箱");
      return;
    }
    try {
      await api.user.sendEmailCode(email, "register");
      toast.success("验证码已发送");
      setCodeCountdown(60);
      if (timerRef.current) clearInterval(timerRef.current);
      timerRef.current = setInterval(() => {
        setCodeCountdown((prev) => {
          if (prev <= 1) {
            if (timerRef.current) clearInterval(timerRef.current);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "发送失败");
    }
  };

  const handleRegister = async (e: FormEvent) => {
    e.preventDefault();
    if (!username || !password || !email || !code) {
      toast.warning("请填写必填项");
      return;
    }
    if (!agreed) {
      toast.warning("请阅读并同意相关协议");
      return;
    }
    setLoading(true);
    try {
      await api.user.registerWithEmail({ username, password, email, code, ...(phone ? { phone } : {}) });
      toast.success("注册成功，请登录");
      router.push("/login");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "注册失败，请重试");
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell>
      <div className="w-full max-w-lg rounded-card bg-surface/75 p-7 sm:p-8 backdrop-blur-md cushion-card [animation:pouf-fade_360ms_ease]">
        <div className="mb-6 text-center">
          <div className="mx-auto mb-3 inline-grid size-14 place-items-center rounded-full bg-mint tone-mint cushion-control">
            <Leaf className="size-6 text-ink" />
          </div>
          <h1 className="text-2xl font-black text-ink">注册心愈智联</h1>
          <p className="mt-2 text-sm font-bold text-muted">加入我们，开启心理健康之旅</p>
        </div>

        <form onSubmit={handleRegister} className="flex flex-col gap-4">
          <Field label="用户名" hint={`${username.length}/20`}>
            <div className="relative">
              <User className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-muted" />
              <Input value={username} onChange={(e) => setUsername(e.target.value)} className="pl-11" placeholder="用户名 *" maxLength={20} autoComplete="username" />
            </div>
            <p className="mt-2 text-xs font-bold text-muted">4-16 位字母、数字、下划线、减号</p>
          </Field>

          <Field label="密码">
            <div className="relative">
              <KeyRound className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-muted" />
              <Input type={showPwd ? "text" : "password"} value={password} onChange={(e) => setPassword(e.target.value)} className="pl-11 pr-11" placeholder="密码 *" autoComplete="new-password" />
              <button type="button" aria-label={showPwd ? "隐藏密码" : "显示密码"} className="absolute right-3 top-1/2 -translate-y-1/2 text-muted transition-colors hover:text-ink" onClick={() => setShowPwd((v) => !v)}>
                {showPwd ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
              </button>
            </div>
            {password && (
              <div className="mt-2 flex items-center gap-2">
                <div className="h-2 flex-1 overflow-hidden rounded-full bg-bg">
                  <div className={`h-full rounded-full transition-all ${pwdStrength.color}`} style={{ width: `${pwdStrength.score}%` }} />
                </div>
                <span className="text-xs font-bold text-muted">强度：{pwdStrength.label}</span>
              </div>
            )}
            <p className="mt-2 text-xs font-bold text-muted">8-16 位，必须包含大小写字母和数字</p>
          </Field>

          <Field label="邮箱">
            <div className="flex gap-2">
              <div className="relative min-w-0 flex-1">
                <Mail className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-muted" />
                <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="pl-11" placeholder="邮箱 *" autoComplete="email" />
              </div>
              <Button type="button" variant="quiet" tone="info" onClick={handleSendCode} disabled={codeCountdown > 0}>
                {codeCountdown > 0 ? `${codeCountdown}秒` : <Send className="size-4" />}
              </Button>
            </div>
          </Field>

          <Field label="验证码">
            <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="请输入验证码 *" maxLength={6} inputMode="numeric" />
          </Field>

          <Field label="手机号（选填）">
            <div className="relative">
              <Smartphone className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-muted" />
              <Input value={phone} onChange={(e) => setPhone(e.target.value)} className="pl-11" placeholder="请输入手机号" autoComplete="tel" />
            </div>
          </Field>

          <label className="flex items-center gap-2 rounded-control bg-bg px-4 py-3 text-sm font-bold text-ink">
            <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} className="size-5 accent-mint" />
            我已阅读并同意
            <Link href="/legal?tab=terms" className="transition-colors hover:text-ink">
              《心愈智联用户服务协议》
            </Link>
            和
            <Link href="/legal?tab=privacy" className="transition-colors hover:text-ink">
              《隐私政策》
            </Link>
          </label>

          <Button tone="mint" variant="solid" size="lg" block type="submit" loading={loading}>
            注册
          </Button>
        </form>

        <p className="mt-6 text-center text-sm font-bold text-muted">
          已有账号？
          <Link href="/login" className="ml-1 transition-colors hover:text-ink">
            立即登录
          </Link>
        </p>
      </div>
    </AuthShell>
  );
}
