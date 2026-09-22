"use client";

import { useEffect, useState } from "react";
import { CalendarCheck, Flame, Plus, PencilLine } from "lucide-react";
import { Card } from "@/components/pouf/Card";
import { Skeleton } from "@/components/pouf/Skeleton";
import { Button } from "@/components/pouf/Button";
import { api } from "@/lib/api";
import type { CheckinStats, CheckinHistoryItem, CheckinToday } from "@/lib/types";
import { CheckinDialog } from "@/components/checkin/checkin-dialog";

const TONE_STYLE: Record<string, string> = {
  pink: "bg-pink/20 text-pink",
  purple: "bg-purple/20 text-purple",
  blue: "bg-blue/20 text-blue",
  mint: "bg-mint/20 text-mint",
  yellow: "bg-yellow/20 text-yellow",
  orange: "bg-orange/20 text-orange",
};

function analysisHint(status?: number) {
  if (status === 2) return "分析完成";
  if (status === 3) return "分析暂不可用";
  return "分析生成中";
}

export function CheckinPage() {
  const [stats, setStats] = useState<CheckinStats | null>(null);
  const [today, setToday] = useState<CheckinToday | null>(null);
  const [history, setHistory] = useState<CheckinHistoryItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);

  const fetchAll = () => {
    setLoading(true);
    Promise.all([
      api.checkin.stats(),
      api.checkin.today(),
      api.checkin.history({ page, size: 10 }),
    ])
      .then(([s, t, h]) => {
        setStats(s);
        setToday(t);
        setHistory(h.records);
        setTotal(h.total);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchAll(); }, [page]);

  const hasCheckedIn = !!today?.checkin;
  const hasDiary = !!today?.checkin?.diaryContent;
  const needDiary = hasCheckedIn && !hasDiary;

  const action = !hasCheckedIn
    ? { label: "去签到", icon: Plus }
    : needDiary
      ? { label: "补写今天的事", icon: PencilLine }
      : { label: "修改今天的内容", icon: PencilLine };

  return (
    <div>
      <h1 className="mb-6 flex items-center gap-2 text-xl font-black text-ink">
        <CalendarCheck className="size-6 text-purple" /> 我的签到
      </h1>

      {loading ? (
        <div className="space-y-4">
          <Skeleton className="h-24 w-full rounded-xl" />
          <Skeleton className="h-40 w-full rounded-xl" />
        </div>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-[220px_1fr]">
            <Card className="p-5 text-center">
              <div className="mb-2 inline-flex size-12 items-center justify-center rounded-pill bg-mint/20 text-mint">
                <Flame className="size-6" />
              </div>
              <div className="text-3xl font-black text-ink">{stats?.continuousDays ?? 0}</div>
              <div className="mt-1 text-xs font-bold text-muted">连续签到天数</div>
              <div className="mt-4 border-t border-ink/10 pt-3 text-sm font-bold text-muted">
                本月已签 <span className="text-ink">{stats?.monthCount ?? 0}</span> 天
              </div>
            </Card>

            <Card className="p-5">
              <div className="flex items-center justify-between">
                <h2 className="text-base font-black text-ink">今日状态</h2>
                <Button variant="quiet" size="sm" tone="mint" onClick={() => setDialogOpen(true)}>
                  <action.icon className="mr-1 size-3.5" /> {action.label}
                </Button>
              </div>

              {!hasCheckedIn ? (
                <p className="mt-4 text-sm font-bold text-muted">今天还没签到，记录一下此刻的心情吧。</p>
              ) : (
                <div className="mt-4 space-y-4">
                  {today!.tags.length > 0 && (
                    <div className="flex flex-wrap gap-2">
                      {today!.tags.map((tag) => (
                        <span key={tag.id} className={`rounded-pill px-3 py-1 text-xs font-bold ${TONE_STYLE[tag.tone] ?? "bg-purple/20 text-purple"}`}>
                          {tag.name}
                        </span>
                      ))}
                    </div>
                  )}
                  {hasDiary && (
                    <p className="rounded-control bg-bg/80 p-3 text-sm leading-relaxed text-muted">{today!.checkin!.diaryContent}</p>
                  )}

                  {today!.analysis ? (
                    <div className="rounded-control bg-mint/10 p-3">
                      {today!.analysis.status === 2 ? (
                        <>
                          <p className="text-sm leading-relaxed text-ink whitespace-pre-wrap">{today!.analysis.userFeedback}</p>
                          {today!.analysis.suggestion && (
                            <p className="mt-2 text-xs font-bold text-muted">{today!.analysis.suggestion}</p>
                          )}
                        </>
                      ) : (
                        <p className="text-sm font-bold text-muted">{analysisHint(today!.analysis.status)}</p>
                      )}
                    </div>
                  ) : hasDiary ? (
                    <p className="text-sm font-bold text-muted">分析生成中</p>
                  ) : null}
                </div>
              )}
            </Card>
          </div>

          <div className="mt-8">
            <h2 className="mb-4 text-base font-black text-ink">历史记录</h2>
            {history.length === 0 ? (
              <div className="py-20 text-center text-muted">还没有签到记录</div>
            ) : (
              <div className="space-y-3">
                {history.map((item) => (
                  <Card key={item.id} className="p-4">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-black text-ink">{item.checkinDate}</span>
                      <span className="text-xs font-bold text-muted">
                        {item.analysisStatus == null
                          ? "未分析"
                          : item.analysisStatus === 2
                            ? (item.riskLevel === 0 ? "平稳" : item.riskLevel === 1 ? "关注" : "危机")
                            : "分析中"}
                      </span>
                    </div>
                    {item.tags.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-2">
                        {item.tags.map((tag) => (
                          <span key={tag.id} className={`rounded-pill px-2 py-0.5 text-xs font-bold ${TONE_STYLE[tag.tone] ?? "bg-purple/20 text-purple"}`}>{tag.name}</span>
                        ))}
                      </div>
                    )}
                    {item.diaryContent && (
                      <p className="mt-2 text-sm text-muted line-clamp-2">{item.diaryContent}</p>
                    )}
                  </Card>
                ))}
              </div>
            )}
            {total > 10 && (
              <div className="mt-4 flex justify-center gap-2">
                <Button variant="quiet" size="sm" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>‹</Button>
                <span className="self-center text-sm text-muted/70">{page} / {Math.ceil(total / 10)}</span>
                <Button variant="quiet" size="sm" disabled={page * 10 >= total} onClick={() => setPage((p) => p + 1)}>›</Button>
              </div>
            )}
          </div>
        </>
      )}

      <CheckinDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        existingId={today?.checkin?.id}
        onDone={fetchAll}
      />
    </div>
  );
}
