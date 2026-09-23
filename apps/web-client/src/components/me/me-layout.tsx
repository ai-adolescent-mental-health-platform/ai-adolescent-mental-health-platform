"use client";

import { useEffect, useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import Link from "next/link";
import {
  User, FileText, Lock, Star, ClipboardCheck, MessageSquare,
  LogOut, Bell, Bookmark, CalendarCheck
} from "lucide-react";
import { Avatar, AvatarFallback } from "@/components/pouf/Avatar";
import { Button } from "@/components/pouf/Button";
import { Skeleton } from "@/components/pouf/Skeleton";
import { Card } from "@/components/pouf/Card";
import { api } from "@/lib/api";
import { clearSession, getStoredUser } from "@/lib/session";
import type { UserProfile } from "@/lib/types";

const MENU_ITEMS = [
  { href: "/me/info", icon: User, label: "个人信息中心" },
  { href: "/me/checkin", icon: CalendarCheck, label: "我的签到" },
  { href: "/me/privacy", icon: Lock, label: "隐私设置" },
  { href: "/me/articles", icon: FileText, label: "我的发布" },
  { href: "/me/messages", icon: Bell, label: "我的消息" },
  { href: "/me/assessments", icon: ClipboardCheck, label: "我的测评记录" },
  { href: "/me/favorites", icon: Bookmark, label: "收藏与点赞" },
  { href: "/me/feedback", icon: MessageSquare, label: "我的反馈" },
];

export function MeLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({ followCount: 0, fanCount: 0, likeCount: 0 });

  useEffect(() => {
    const fetchData = async () => {
      try {
        const p = await api.user.getUserInfo();
        setProfile(p);
        const home = await api.user.getUserHome(p.id);
        const homeStats = (home as Record<string, unknown>).stats as Record<string, unknown> | undefined;
        setStats({
          followCount: (homeStats?.followCount as number) ?? 0,
          fanCount: (homeStats?.fanCount as number) ?? 0,
          likeCount: (homeStats?.likeCount as number) ?? 0,
        });
      } catch {
        // keep defaults
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const displayName = profile?.nickname || profile?.username || "用户";

  const handleLogout = () => {
    clearSession();
    router.push("/login");
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 md:py-12">
      <div className="flex gap-6">
        {/* Sidebar */}
        <aside className="hidden md:block w-[240px] shrink-0">
          <Card className="sticky top-24 overflow-hidden">
            {/* User info header */}
            <div className="px-4 pt-6 pb-4 text-center border-b border-[rgba(201,168,255,0.3)]">
              <Link href="/me/info" className="inline-block">
                <Avatar className="size-16 mx-auto ring-2 ring-yellow/40">
                  {profile?.headPath ? (
                    <img src={profile.headPath} alt="" className="size-16 rounded-full object-cover" />
                  ) : (
                    <AvatarFallback className="bg-purple/20 text-purple text-2xl">
                      {loading ? <Skeleton className="size-16 rounded-full" /> : displayName[0]}
                    </AvatarFallback>
                  )}
                </Avatar>
              </Link>
              <Link href="/me/info" className="mt-3 block text-sm font-semibold text-ink hover:text-purple transition-colors">
                {loading ? <Skeleton className="h-5 w-20 mx-auto" /> : displayName}
              </Link>

              {/* Stats row */}
              <div className="mt-3 flex items-center justify-center gap-0 text-xs">
                <Link href="/me/followings" className="flex flex-col items-center px-2 hover:text-purple transition-colors text-muted">
                  <span className="font-bold text-ink text-sm">{stats.followCount}</span>
                  <span>关注</span>
                </Link>
                <span className="text-muted/70 mx-1">|</span>
                <Link href="/me/fans" className="flex flex-col items-center px-2 hover:text-purple transition-colors text-muted">
                  <span className="font-bold text-ink text-sm">{stats.fanCount}</span>
                  <span>粉丝</span>
                </Link>
                <span className="text-muted/70 mx-1">|</span>
                <span className="flex flex-col items-center px-2 text-muted">
                  <span className="font-bold text-ink text-sm">{stats.likeCount}</span>
                  <span>获赞</span>
                </span>
              </div>
            </div>

            {/* Menu */}
            <nav className="py-2">
              {MENU_ITEMS.map((item) => {
                const isActive = pathname === item.href;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={`flex items-center gap-3 px-4 py-3 text-sm transition-colors ${
                      isActive
                        ? "bg-purple/15 text-purple border-r-2 border-purple"
                        : "text-muted hover:bg-purple/10 hover:text-ink"
                    }`}
                  >
                    <item.icon className="size-4 shrink-0" />
                    <span>{item.label}</span>
                  </Link>
                );
              })}
            </nav>

            {/* Logout */}
            <div className="border-t border-[rgba(201,168,255,0.3)] p-3">
              <Button variant="quiet" size="sm" block onClick={handleLogout}>
                <LogOut className="mr-2 size-3" />
                退出登录
              </Button>
            </div>
          </Card>
        </aside>

        {/* Content */}
        <main className="flex-1 min-w-0">
          {children}
        </main>
      </div>
    </div>
  );
}
