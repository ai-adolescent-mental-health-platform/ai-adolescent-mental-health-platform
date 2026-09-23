"use client";

import { useEffect, useState } from "react";
import type { HTMLAttributes, ComponentType } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  Bot, Calendar, ClipboardCheck, BookOpen, Heart, Sparkles,
  ChevronRight, MessageCircle, ArrowRight, Sprout, CalendarCheck,
} from "lucide-react";
import { Card, CardContent } from "@/components/pouf/Card";
import { Button } from "@/components/pouf/Button";
import { Progress } from "@/components/pouf/Progress";
import { cn } from "@/lib/utils";
import { api } from "@/lib/api";
import type { Appointment, AssessmentRecord, LibraryItem, CheckinToday } from "@/lib/types";
import { CheckinDialog } from "@/components/checkin/checkin-dialog";

const DEFAULT_QUOTES = [
  { content: "每一种情绪都值得被看见，每一个你都值得被温柔以待。", author: "心愈智联" },
  { content: "你不必独自面对一切，我们在这里陪伴你。", author: "心愈智联" },
  { content: "成长是一场旅行，心理健康是最好的行囊。", author: "心愈智联" },
  { content: "倾听内心的声音，那是你最真实的力量。", author: "心愈智联" },
];

const SHORTCUTS = [
  { href: "/ai", label: "AI 咨询室", desc: "24 小时陪伴对话", icon: Bot, blob: "bg-mint tone-mint" },
  { href: "/consultation", label: "心理咨询预约", desc: "与专业咨询师对话", icon: Calendar, blob: "bg-yellow tone-yellow" },
  { href: "/assessment", label: "心理评估", desc: "了解你的心理状态", icon: ClipboardCheck, blob: "bg-blue tone-blue" },
  { href: "/library", label: "内容馆", desc: "文章 · 课程 · 书籍", icon: BookOpen, blob: "bg-purple tone-purple" },
  { href: "/me", label: "我的照护计划", desc: "定制成长方案", icon: Heart, blob: "bg-pink tone-pink" },
];

const CARE_PLAN = [
  { title: "继续 AI 对话", status: "进行中", blob: "bg-mint tone-mint", pill: "bg-mint/30 text-ink" },
  { title: "预约专业咨询", status: "待开始", blob: "bg-purple tone-purple", pill: "bg-purple/30 text-ink" },
  { title: "完成一次测评", status: "待开始", blob: "bg-yellow tone-yellow", pill: "bg-yellow/30 text-ink" },
  { title: "阅读支持内容", status: "进行中", blob: "bg-pink tone-pink", pill: "bg-pink/30 text-ink" },
];

function formatToday() {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "long",
    day: "numeric",
    weekday: "long",
  }).format(new Date());
}

/** Pouf skeleton — a soft pulsing clay slab instead of the cosmic white band. */
function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("animate-pulse bg-surface/70", className)} {...props} />;
}

function SectionIcon({ blob, icon: Icon }: { blob: string; icon: ComponentType<{ className?: string }> }) {
  return (
    <span className={`inline-grid size-9 shrink-0 place-items-center rounded-pill ${blob} cushion-blob`}>
      <Icon className="size-4 text-ink" />
    </span>
  );
}

export function HomePage() {
  const router = useRouter();

  // Daily quote
  const [quoteIndex, setQuoteIndex] = useState(0);
  const [fade, setFade] = useState(true);
  const quotes = DEFAULT_QUOTES;

  // Data
  const [nickname, setNickname] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [latestAppointment, setLatestAppointment] = useState<Appointment | null>(null);
  const [latestAssessment, setLatestAssessment] = useState<AssessmentRecord | null>(null);
  const [latestAiReply, setLatestAiReply] = useState<string>("");
  const [recommendations, setRecommendations] = useState<LibraryItem[]>([]);
  const [checkinToday, setCheckinToday] = useState<CheckinToday | null>(null);
  const [checkinOpen, setCheckinOpen] = useState(false);

  // Quote rotation
  useEffect(() => {
    const timer = setInterval(() => {
      setFade(false);
      setTimeout(() => {
        setQuoteIndex((prev) => (prev + 1) % quotes.length);
        setFade(true);
      }, 500);
    }, 6000);
    return () => clearInterval(timer);
  }, [quotes.length]);

  // Data fetching
  useEffect(() => {
    const fetchData = async () => {
      try {
        const [profile, appointmentRes, assessmentRes, sessions] = await Promise.all([
          api.user.getUserInfo(),
          api.appointment.my().catch(() => ({ records: [] })),
          api.assessment.records().catch(() => ({ records: [] as AssessmentRecord[] })),
          api.ai.sessions().catch(() => []),
        ]);

        setNickname(profile.nickname || profile.username || "用户");
        setLatestAppointment(appointmentRes.records[0] ?? null);
        setLatestAssessment(assessmentRes.records[0] ?? null);

        // Latest AI reply
        const firstSession = sessions[0];
        if (firstSession) {
          try {
            const msgs = await api.ai.messages(firstSession.id);
            const lastReply = [...msgs].reverse().find((m) => m.role === "assistant");
            if (lastReply) setLatestAiReply(lastReply.content);
          } catch { /* ignore */ }
        }

        // Recommendations
        const contentResults = await Promise.allSettled([
          api.content.articles({ page: 1, size: 4 }),
          api.content.courses({ page: 1, size: 4 }),
          api.content.books({ page: 1, size: 4 }),
          api.content.communityArticles({ page: 1, size: 4 }),
        ]);
        const merged = contentResults.flatMap((r) =>
          r.status === "fulfilled" ? r.value.records : [],
        );
        setRecommendations(merged.slice(0, 3));
      } catch { /* use defaults */ } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  // Checkin status
  useEffect(() => {
    api.checkin.today().then(setCheckinToday).catch(() => {});
  }, []);

  const currentQuote = quotes[quoteIndex];

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 md:py-12">

      {/* Row 1: Greeting + Daily Quote */}
      <section className="mb-10 grid gap-6 lg:grid-cols-[1fr_400px] lg:items-center">
        <div>
          <h1 className="text-3xl font-black text-ink md:text-4xl tracking-tight">
            {loading ? (
              <Skeleton className="inline-block h-9 w-48 rounded-pill" />
            ) : (
              <>
                你好，{nickname}
                <span className="ml-2 inline-grid h-9 w-9 translate-y-1 place-items-center rounded-pill bg-mint tone-mint cushion-blob align-middle">
                  <Sprout className="size-5 text-ink" />
                </span>
              </>
            )}
          </h1>
          <div className="mt-3 flex flex-wrap items-center gap-3 text-sm font-bold text-muted">
            <span>今天是 {formatToday()}</span>
            <span className="h-4 w-px bg-ink/15" />
            <span>关注自己，从一个小行动开始</span>
          </div>
        </div>

        <Card className="relative overflow-hidden">
          <CardContent className="py-6 text-center">
            <div className="mb-3 inline-flex items-center gap-1.5 rounded-pill bg-purple/25 px-3 py-1 text-xs font-black text-ink">
              <Sparkles className="size-3" /> 每日一语
            </div>
            <blockquote
              className={`mx-auto max-w-sm text-lg italic font-bold text-ink transition-all duration-500 ${
                fade ? "opacity-100 translate-y-0" : "opacity-0 -translate-y-1"
              }`}
            >
              “{currentQuote.content}”
            </blockquote>
            {currentQuote.author && (
              <cite className="mt-2 block text-xs font-bold text-muted not-italic">
                · {currentQuote.author}
              </cite>
            )}
          </CardContent>
        </Card>
      </section>

      {/* Checkin card — 三态：未签到 / 已签待补日记 / 已完成 */}
      <section className="mb-10">
        <Card className="overflow-hidden">
          <CardContent className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-4">
              <span className="inline-grid size-12 shrink-0 place-items-center rounded-pill bg-mint tone-mint cushion-blob">
                <CalendarCheck className="size-6 text-ink" />
              </span>
              <div>
                <h3 className="font-black text-ink">
                  {checkinToday?.checkin ? "今天已签到" : "今日签到"}
                </h3>
                <p className="mt-1 text-xs font-bold text-muted">
                  {checkinToday?.checkin
                    ? checkinToday.checkin.diaryContent
                      ? "已完成记录与 AI 反馈"
                      : "已打卡，还可以补写今天的事"
                    : "花十几秒记录此刻的心情与状态"}
                </p>
              </div>
            </div>
            <Button tone="mint" variant="solid" size="sm" onClick={() => setCheckinOpen(true)}>
              {checkinToday?.checkin
                ? checkinToday.checkin.diaryContent
                  ? "修改今天的内容"
                  : "补写今天的事"
                : "去签到"}
            </Button>
          </CardContent>
        </Card>
      </section>

      {/* Row 2: Quick Actions */}
      <section className="mb-10">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5 min-w-0">
          {SHORTCUTS.map((item) => (
            <Link key={item.href} href={item.href} className="block min-w-0">
              <Card className="group flex h-full flex-col p-5 transition-transform duration-200 hover:-translate-y-1">
                <span className={`mb-3 inline-grid size-12 place-items-center rounded-pill ${item.blob} cushion-blob transition-transform duration-200 group-hover:scale-105`}>
                  <item.icon className="size-6 text-ink" />
                </span>
                <h3 className="font-black text-ink">{item.label}</h3>
                <p className="mt-1 text-xs font-bold text-muted">{item.desc}</p>
              </Card>
            </Link>
          ))}
        </div>
      </section>

      {/* Row 3: Three-column content */}
      <section className="mb-10 grid gap-5 min-w-0 lg:grid-cols-[1fr_300px] xl:grid-cols-[1fr_280px_300px]">
        {/* Left: AI 咨询室 */}
        <Card className="flex min-h-[320px] flex-col min-w-0">
          <CardContent className="flex flex-1 flex-col">
            <div className="mb-4 flex items-center gap-2.5">
              <SectionIcon blob="bg-blue tone-blue" icon={MessageCircle} />
              <h3 className="text-base font-black text-ink">AI 咨询室</h3>
              <span className="text-xs font-bold text-muted">最新对话</span>
              <Link href="/ai" className="ml-auto">
                <Button variant="quiet" tone="blue" size="sm">
                  进入 <ChevronRight className="size-3" />
                </Button>
              </Link>
            </div>

            <div className="flex flex-1 flex-col justify-between">
              {loading ? (
                <Skeleton className="h-24 w-full rounded-card" />
              ) : latestAiReply ? (
                <div className="flex-1 rounded-card bg-bg/80 p-4">
                  <p className="text-sm leading-relaxed text-muted line-clamp-4">
                    {latestAiReply}
                  </p>
                </div>
              ) : (
                <div className="flex flex-1 items-center justify-center text-sm font-bold text-muted">
                  还没有 AI 会话记录
                </div>
              )}

              <button
                type="button"
                onClick={() => router.push("/ai")}
                className="mt-4 w-full cursor-pointer rounded-control bg-bg/80 px-4 py-3 text-left text-sm font-bold text-ink transition-colors hover:bg-bg"
              >
                把现在最困扰你的事写下来...
              </button>
            </div>
          </CardContent>
        </Card>

        {/* Middle: Appointments + Assessments */}
        <div className="flex flex-col gap-5 lg:col-span-1 xl:col-span-1 min-w-0">
          {/* Recent Appointment */}
          <Card>
            <CardContent>
              <div className="mb-3 flex items-center justify-between">
                <h3 className="flex items-center gap-2 text-base font-black text-ink">
                  <SectionIcon blob="bg-purple tone-purple" icon={Calendar} />
                  近期预约
                </h3>
                <Link href="/me/psychology">
                  <Button variant="quiet" tone="purple" size="sm">
                    全部 <ArrowRight className="ml-1 size-3" />
                  </Button>
                </Link>
              </div>

              {loading ? (
                <Skeleton className="h-16 w-full rounded-control" />
              ) : latestAppointment ? (
                <div className="rounded-control bg-bg/80 p-3">
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-black text-ink">{latestAppointment.psychologistName}</p>
                    <span className="rounded-pill bg-purple/30 px-2 py-0.5 text-xs font-black text-ink">
                      {latestAppointment.status}
                    </span>
                  </div>
                  <p className="mt-1 text-xs font-bold text-muted">
                    {latestAppointment.date} {latestAppointment.time} · {latestAppointment.type}
                  </p>
                </div>
              ) : (
                <div className="py-4 text-center text-xs font-bold text-muted">暂无近期预约</div>
              )}
            </CardContent>
          </Card>

          {/* Latest Assessment */}
          <Card>
            <CardContent>
              <div className="mb-3 flex items-center justify-between">
                <h3 className="flex items-center gap-2 text-base font-black text-ink">
                  <SectionIcon blob="bg-blue tone-blue" icon={ClipboardCheck} />
                  心理评估记录
                </h3>
                <Link href="/me/assessments">
                  <Button variant="quiet" tone="blue" size="sm">
                    全部 <ArrowRight className="ml-1 size-3" />
                  </Button>
                </Link>
              </div>

              {loading ? (
                <Skeleton className="h-20 w-full rounded-control" />
              ) : latestAssessment ? (
                <div>
                  <p className="text-sm font-black text-ink">{latestAssessment.title}</p>
                  <div className="mt-3 flex items-center gap-3">
                    <Progress value={Math.min(latestAssessment.score, 100)} tone="mint" className="h-2 flex-1" />
                    <span className="text-sm font-black text-ink">{latestAssessment.score} 分</span>
                  </div>
                  <p className="mt-3 text-xs leading-5 text-muted line-clamp-2">
                    {latestAssessment.result}
                  </p>
                </div>
              ) : (
                <div className="py-4 text-center text-xs font-bold text-muted">暂无测评记录</div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Right: Recommendations */}
        <Card className="lg:col-span-2 xl:col-span-1 min-w-0">
          <CardContent>
            <div className="mb-4 flex items-center gap-2.5">
              <SectionIcon blob="bg-yellow tone-yellow" icon={Sparkles} />
              <h3 className="text-base font-black text-ink">为你推荐</h3>
            </div>

            {loading ? (
              <div className="space-y-3">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-14 w-full rounded-control" />
                ))}
              </div>
            ) : recommendations.length ? (
              <div className="space-y-3">
                {recommendations.map((item) => {
                  const isCourse = item.type === "课程";
                  const isExternal = isCourse && !!item.linkUrl;
                  const href = item.type === "书籍"
                    ? `/library/book/${item.id}`
                    : isExternal
                      ? item.linkUrl!
                      : item.type === "社区" && item.authorId
                        ? `/user/${item.authorId}/article/${item.id}`
                        : `/library/article/${item.id}`;

                  const inner = (
                    <div className="group rounded-control bg-bg/80 p-3 transition-colors hover:bg-surface">
                      <div className="flex items-center gap-2">
                        <span className="shrink-0 rounded-pill bg-purple/30 px-2 py-0.5 text-xs font-black text-ink">{item.type}</span>
                        <p className="text-sm font-black text-ink line-clamp-1 transition-colors group-hover:text-purple">
                          {item.title}
                        </p>
                      </div>
                      <p className="mt-1 text-xs font-bold text-muted line-clamp-2">{item.summary}</p>
                    </div>
                  );

                  if (isExternal) {
                    return (
                      <a key={`${item.type}-${item.id}`} href={item.linkUrl!} target="_blank" rel="noopener noreferrer">
                        {inner}
                      </a>
                    );
                  }
                  return (
                    <Link key={`${item.type}-${item.id}`} href={href}>
                      {inner}
                    </Link>
                  );
                })}
              </div>
            ) : (
              <div className="py-8 text-center text-xs font-bold text-muted">暂无推荐内容</div>
            )}

            <Link href="/library" className="mt-3 block">
              <Button variant="quiet" tone="purple" size="sm" block>
                查看全部推荐 <ArrowRight className="ml-1 size-3" />
              </Button>
            </Link>
          </CardContent>
        </Card>
      </section>

      {/* Row 4: Care Plan */}
      <section>
        <div className="mb-5 flex items-center gap-3">
          <SectionIcon blob="bg-mint tone-mint" icon={Sprout} />
          <div>
            <h2 className="text-lg font-black text-ink">你的照护计划</h2>
            <p className="text-xs font-bold text-muted">从一个小行动开始关注自己</p>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 min-w-0">
          {CARE_PLAN.map((item) => (
            <Card key={item.title} className="p-4">
              <div className="flex items-center gap-3">
                <SectionIcon blob={item.blob} icon={Sparkles} />
                <div>
                  <p className="text-sm font-black text-ink">{item.title}</p>
                  <span className={`mt-1 inline-block rounded-pill ${item.pill} px-2 py-0.5 text-xs font-black`}>
                    {item.status}
                  </span>
                </div>
              </div>
            </Card>
          ))}
        </div>
      </section>

      <CheckinDialog
        open={checkinOpen}
        onOpenChange={setCheckinOpen}
        existingId={checkinToday?.checkin?.id}
        onDone={() => api.checkin.today().then(setCheckinToday).catch(() => {})}
      />
    </div>
  );
}
