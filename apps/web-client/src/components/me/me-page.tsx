"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { FileText, Heart, Star, ShoppingBag, Calendar, ArrowRight, Pencil, CalendarCheck } from "lucide-react";
import { Skeleton } from "@/components/pouf/Skeleton";
import { Button } from "@/components/pouf/Button";
import { Badge } from "@/components/pouf/Badge";
import { Card } from "@/components/pouf/Card";
import { api } from "@/lib/api";
import type { UserProfile, Appointment } from "@/lib/types";

export function MePage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [recentAppointments, setRecentAppointments] = useState<Appointment[]>([]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const p = await api.user.getUserInfo();
        setProfile(p);
        const appts = await api.appointment.my();
        setRecentAppointments(appts.records.slice(0, 3));
      } catch {
        // keep defaults
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const displayName = profile?.nickname || profile?.username || "用户";

  if (loading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-64" />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
        </div>
        <Skeleton className="h-40 w-full rounded-xl" />
      </div>
    );
  }

  return (
    <div>
      <h1 className="text-2xl font-black text-ink">欢迎回来，{displayName}</h1>
      <p className="mt-1 text-sm text-muted">
        {profile?.signature || "关注你的心理健康，从这里开始"}
      </p>

      {/* Quick action cards */}
      <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-6">
        <Link href="/me/checkin">
          <Card className="group cursor-pointer p-4 transition-all hover:-translate-y-0.5">
            <div className="mb-3 inline-flex rounded-control bg-mint/20 p-2">
              <CalendarCheck className="size-5 text-mint" />
            </div>
            <div className="text-lg font-bold text-ink">我的签到</div>
            <div className="mt-1 flex items-center gap-1 text-xs text-muted/70 group-hover:text-purple">
              每日打卡 <ArrowRight className="size-3 transition-transform group-hover:translate-x-1" />
            </div>
          </Card>
        </Link>

        <Link href="/me/publish">
          <Card className="group cursor-pointer p-4 transition-all hover:-translate-y-0.5">
            <div className="mb-3 inline-flex rounded-control bg-purple/20 p-2">
              <Pencil className="size-5 text-purple" />
            </div>
            <div className="text-lg font-bold text-ink">发布文章</div>
            <div className="mt-1 flex items-center gap-1 text-xs text-muted/70 group-hover:text-purple">
              写文章 <ArrowRight className="size-3 transition-transform group-hover:translate-x-1" />
            </div>
          </Card>
        </Link>

        <Link href="/me/articles">
          <Card className="group cursor-pointer p-4 transition-all hover:-translate-y-0.5">
            <div className="mb-3 inline-flex rounded-control bg-purple/20 p-2">
              <FileText className="size-5 text-purple" />
            </div>
            <div className="text-lg font-bold text-ink">我的发布</div>
            <div className="mt-1 flex items-center gap-1 text-xs text-muted/70 group-hover:text-purple">
              管理文章 <ArrowRight className="size-3 transition-transform group-hover:translate-x-1" />
            </div>
          </Card>
        </Link>

        <Link href="/me/favorites">
          <Card className="group cursor-pointer p-4 transition-all hover:-translate-y-0.5">
            <div className="mb-3 inline-flex rounded-control bg-yellow/20 p-2">
              <Star className="size-5 text-yellow" />
            </div>
            <div className="text-lg font-bold text-ink">我的收藏</div>
            <div className="mt-1 flex items-center gap-1 text-xs text-muted/70 group-hover:text-purple">
              查看收藏 <ArrowRight className="size-3 transition-transform group-hover:translate-x-1" />
            </div>
          </Card>
        </Link>

        <Link href="/me/likes">
          <Card className="group cursor-pointer p-4 transition-all hover:-translate-y-0.5">
            <div className="mb-3 inline-flex rounded-control bg-pink/20 p-2">
              <Heart className="size-5 text-pink" />
            </div>
            <div className="text-lg font-bold text-ink">我的点赞</div>
            <div className="mt-1 flex items-center gap-1 text-xs text-muted/70 group-hover:text-purple">
              查看点赞 <ArrowRight className="size-3 transition-transform group-hover:translate-x-1" />
            </div>
          </Card>
        </Link>

        <Link href="/me/orders">
          <Card className="group cursor-pointer p-4 transition-all hover:-translate-y-0.5">
            <div className="mb-3 inline-flex rounded-control bg-mint/20 p-2">
              <ShoppingBag className="size-5 text-mint" />
            </div>
            <div className="text-lg font-bold text-ink">我的订单</div>
            <div className="mt-1 flex items-center gap-1 text-xs text-muted/70 group-hover:text-purple">
              查看订单 <ArrowRight className="size-3 transition-transform group-hover:translate-x-1" />
            </div>
          </Card>
        </Link>
      </div>

      {/* Recent appointments */}
      <div className="mt-8">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="flex items-center gap-2 text-lg font-black text-ink">
            <Calendar className="size-5 text-purple" />
            近期预约
          </h2>
          <Link href="/me/psychology">
            <Button variant="quiet" size="sm">
              查看全部 <ArrowRight className="ml-1 size-3" />
            </Button>
          </Link>
        </div>

        {recentAppointments.length === 0 ? (
          <Card className="p-8 text-center">
            <p className="mb-4 text-sm text-muted">暂无预约记录</p>
            <Link href="/consultation/psychologist">
              <Button tone="purple" variant="solid" size="sm">去预约咨询师</Button>
            </Link>
          </Card>
        ) : (
          <div className="space-y-3">
            {recentAppointments.map((a) => (
              <Card key={a.id} className="flex items-center justify-between p-4">
                <div>
                  <div className="font-bold text-ink">{a.psychologistName}</div>
                  <div className="mt-0.5 text-xs text-muted/70">
                    {a.date} {a.time} · {a.type}
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-sm font-bold text-yellow">¥{a.fee}</span>
                  <Badge variant="secondary" className="text-xs">{a.status}</Badge>
                </div>
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
