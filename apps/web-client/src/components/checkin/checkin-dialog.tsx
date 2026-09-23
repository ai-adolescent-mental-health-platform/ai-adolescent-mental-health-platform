"use client";

import { useEffect, useState } from "react";
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
  DialogFooter, DialogClose,
} from "@/components/pouf/Dialog";
import { Button } from "@/components/pouf/Button";
import { Textarea } from "@/components/pouf/Textarea";
import { toast } from "sonner";
import { api } from "@/lib/api";
import type { CheckinMoodTag } from "@/lib/types";

const TONE_STYLE: Record<string, string> = {
  pink: "bg-pink/20 text-pink",
  purple: "bg-purple/20 text-purple",
  blue: "bg-blue/20 text-blue",
  mint: "bg-mint/20 text-mint",
  yellow: "bg-yellow/20 text-yellow",
  orange: "bg-orange/20 text-orange",
};

export function CheckinDialog({
  open, onOpenChange, onDone, existingId,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onDone?: () => void;
  existingId?: number;
}) {
  const [tags, setTags] = useState<CheckinMoodTag[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([]);
  const [diary, setDiary] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      api.checkin.moodTags().then(setTags).catch(() => {});
    }
  }, [open]);

  const toggleTag = (id: number) => {
    setSelectedTagIds((prev) => (prev.includes(id) ? prev.filter((t) => t !== id) : [...prev, id]));
  };

  const submit = async () => {
    if (selectedTagIds.length === 0) {
      toast.warning("请至少选择一个情绪标签");
      return;
    }
    setSubmitting(true);
    try {
      const body = { tagIds: selectedTagIds, diaryContent: diary.trim() || undefined };
      if (existingId) {
        await api.checkin.update(existingId, body);
        toast.success("已更新今日签到");
      } else {
        await api.checkin.submit(body);
        toast.success("签到成功");
      }
      onOpenChange(false);
      onDone?.();
    } catch {
      toast.error("操作失败，请重试");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{existingId ? "修改今日签到" : "今日签到"}</DialogTitle>
          <DialogDescription>记录此刻的心情，从一个小觉察开始</DialogDescription>
        </DialogHeader>

        <div className="mt-4">
          <p className="mb-2 text-sm font-bold text-ink">今天的心情（可多选）</p>
          <div className="flex flex-wrap gap-2">
            {tags.map((tag) => {
              const active = selectedTagIds.includes(tag.id);
              const style = TONE_STYLE[tag.tone] ?? "bg-purple/20 text-purple";
              return (
                <button
                  key={tag.id}
                  type="button"
                  onClick={() => toggleTag(tag.id)}
                  className={`rounded-pill px-3 py-1.5 text-sm font-bold transition-all ${active ? style : "bg-bg text-muted"}`}
                >
                  {tag.name}
                </button>
              );
            })}
          </div>
        </div>

        <div className="mt-4">
          <p className="mb-2 text-sm font-bold text-ink">想说的话（可选）</p>
          <Textarea
            value={diary}
            onChange={(e) => setDiary(e.target.value)}
            placeholder="记录今天的困惑、难处或开心事..."
            rows={4}
          />
        </div>

        <DialogFooter>
          <DialogClose>取消</DialogClose>
          <Button tone="mint" variant="solid" size="sm" onClick={submit} loading={submitting}>
            提交
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
