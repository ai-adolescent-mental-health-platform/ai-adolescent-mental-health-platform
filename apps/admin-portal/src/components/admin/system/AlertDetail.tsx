"use client";

import { useState, useEffect, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { httpClient } from "@/lib/api-admin";
import { s } from "@/lib/design-tokens";

const STATUS_LABELS: Record<number, string> = { 0: "待处置", 1: "处置中", 2: "已处置", 3: "已忽略" };
const SOURCE_LABELS: Record<string, string> = { CHECKIN: "签到", ASSESSMENT: "量表", XIAOAI: "小爱倾听" };
const TRIGGER_LABELS: Record<string, string> = { MODEL: "模型判定", KEYWORD: "关键词兜底", MANUAL: "人工" };

export function AlertDetail() {
  const params = useParams();
  const router = useRouter();
  const id = params?.id as string;
  const [detail, setDetail] = useState<Record<string, unknown> | null>(null);
  const [handleStatus, setHandleStatus] = useState(2);
  const [note, setNote] = useState("");
  const [loading, setLoading] = useState(false);

  const fetchDetail = useCallback(async () => {
    setLoading(true);
    try {
      const res = await httpClient.get<Record<string, unknown>>(`/admin/checkin/alerts/${id}`);
      setDetail(res);
    } catch {
      /* ignore */
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { fetchDetail(); }, [fetchDetail]);

  const alert = (detail?.alert ?? {}) as Record<string, unknown>;
  const diaryContent = (detail?.diaryContent as string) ?? "";

  const handleSubmit = async () => {
    try {
      await httpClient.put(`/admin/checkin/alerts/${id}/handle`, { status: handleStatus, handleNote: note });
      router.push("/admin/alerts");
    } catch {
      /* ignore */
    }
  };

  if (loading) {
    return <div style={{ padding: "40px", textAlign: "center", color: s.text2 }}>加载中...</div>;
  }
  if (!detail) {
    return <div style={{ padding: "40px", textAlign: "center", color: s.text2 }}>暂无数据</div>;
  }

  return (
    <div style={{ padding: "20px", backgroundColor: s.bg, minHeight: "100%" }}>
      <div style={{ backgroundColor: s.white, borderRadius: "8px", boxShadow: s.shadow, padding: "20px", maxWidth: "760px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px" }}>
          <h2 style={{ margin: 0, fontSize: "18px", color: s.text }}>预警详情 #{alert.id as string}</h2>
          <button onClick={() => router.push("/admin/alerts")} style={{ height: "32px", padding: "0 16px", border: `1px solid ${s.border}`, borderRadius: s.radius, background: s.white, cursor: "pointer", fontSize: "13px" }}>返回列表</button>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "110px 1fr", rowGap: "12px", fontSize: "13px", alignItems: "start" }}>
          <div style={{ color: s.text2 }}>用户ID</div><div style={{ color: s.text }}>{alert.userId as string}</div>
          <div style={{ color: s.text2 }}>来源</div><div style={{ color: s.text }}>{SOURCE_LABELS[alert.sourceType as string] ?? (alert.sourceType as string)}</div>
          <div style={{ color: s.text2 }}>等级</div><div style={{ color: s.danger, fontWeight: 700 }}>{alert.level as string}</div>
          <div style={{ color: s.text2 }}>触发方式</div><div style={{ color: s.text }}>{TRIGGER_LABELS[alert.triggerType as string] ?? (alert.triggerType as string)}</div>
          <div style={{ color: s.text2 }}>处置状态</div><div style={{ color: s.text }}>{STATUS_LABELS[alert.status as number] ?? (alert.status as string)}</div>
          <div style={{ color: s.text2 }}>判定理由</div><div style={{ color: s.text }}>{alert.reason as string}</div>
          <div style={{ color: s.text2 }}>原文摘录</div><div style={{ color: s.text, background: s.bg, padding: "8px 12px", borderRadius: s.radius }}>{alert.evidence as string}</div>
          <div style={{ color: s.text2 }}>签到正文</div><div style={{ color: s.text, background: s.bg, padding: "8px 12px", borderRadius: s.radius, whiteSpace: "pre-wrap" }}>{diaryContent || "（无正文）"}</div>
        </div>

        <div style={{ marginTop: "24px", borderTop: `1px solid ${s.border}`, paddingTop: "20px" }}>
          <h3 style={{ margin: "0 0 16px", fontSize: "15px", color: s.text }}>处置操作</h3>
          <div style={{ marginBottom: "16px" }}>
            <label style={{ display: "block", marginBottom: "6px", fontSize: "13px", color: s.text2 }}>处置状态</label>
            <select value={handleStatus} onChange={(e) => setHandleStatus(Number(e.target.value))} style={{ width: "100%", height: "36px", padding: "0 12px", border: `1px solid ${s.border}`, borderRadius: s.radius, fontSize: "13px" }}>
              <option value={2}>已处置</option>
              <option value={3}>已忽略</option>
              <option value={1}>处置中</option>
            </select>
          </div>
          <div style={{ marginBottom: "20px" }}>
            <label style={{ display: "block", marginBottom: "6px", fontSize: "13px", color: s.text2 }}>处置备注</label>
            <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={3} style={{ width: "100%", padding: "12px", border: `1px solid ${s.border}`, borderRadius: s.radius, boxSizing: "border-box", fontSize: "13px" }} placeholder="记录处置情况..." />
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
            <button onClick={() => router.push("/admin/alerts")} style={{ height: "36px", padding: "0 20px", border: `1px solid ${s.border}`, borderRadius: s.radius, background: s.white, cursor: "pointer" }}>取消</button>
            <button onClick={handleSubmit} style={{ height: "36px", padding: "0 20px", backgroundColor: s.primary, color: "#fff", border: "none", borderRadius: s.radius, cursor: "pointer" }}>提交处置</button>
          </div>
        </div>
      </div>
    </div>
  );
}
