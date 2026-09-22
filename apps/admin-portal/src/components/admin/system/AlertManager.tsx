"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { httpClient } from "@/lib/api-admin";
import { s } from "@/lib/design-tokens";

type PageResult<T> = { total: number; records: T[]; current: number; size: number; pages: number };

const STATUS_LABELS: Record<number, string> = { 0: "待处置", 1: "处置中", 2: "已处置", 3: "已忽略" };
const SOURCE_LABELS: Record<string, string> = { CHECKIN: "签到", ASSESSMENT: "量表", XIAOAI: "小爱倾听" };
const TRIGGER_LABELS: Record<string, string> = { MODEL: "模型判定", KEYWORD: "关键词兜底", MANUAL: "人工" };

export function AlertManager() {
  const router = useRouter();
  const [data, setData] = useState<PageResult<Record<string, unknown>>>({ total: 0, records: [], current: 1, size: 20, pages: 0 });
  const [statusFilter, setStatusFilter] = useState("");
  const [sourceFilter, setSourceFilter] = useState("");
  const [loading, setLoading] = useState(false);

  const fetchData = useCallback(async (page = 1) => {
    setLoading(true);
    try {
      const query: Record<string, string | number | boolean | null | undefined> = { page, size: 20 };
      if (statusFilter !== "") query.status = Number(statusFilter);
      if (sourceFilter !== "") query.sourceType = sourceFilter;
      const res = await httpClient.get<PageResult<Record<string, unknown>>>("/admin/checkin/alerts", { query });
      setData(res);
    } catch {
      /* ignore */
    } finally {
      setLoading(false);
    }
  }, [statusFilter, sourceFilter]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const statusColor = (st: number) => (st === 0 ? s.danger : st === 2 ? s.success : s.text2);

  return (
    <div style={{ padding: "20px", backgroundColor: s.bg, minHeight: "100%" }}>
      <div style={{ backgroundColor: s.white, borderRadius: "8px", boxShadow: s.shadow, padding: "20px" }}>
        <h2 style={{ margin: "0 0 20px", fontSize: "18px", color: s.text }}>风险预警管理</h2>

        <div style={{ display: "flex", gap: "10px", marginBottom: "20px" }}>
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}
            style={{ height: "36px", padding: "0 8px", border: `1px solid ${s.border}`, borderRadius: s.radius, fontSize: "13px", color: s.text2 }}>
            <option value="">全部状态</option>
            <option value="0">待处置</option>
            <option value="1">处置中</option>
            <option value="2">已处置</option>
            <option value="3">已忽略</option>
          </select>
          <select value={sourceFilter} onChange={(e) => setSourceFilter(e.target.value)}
            style={{ height: "36px", padding: "0 8px", border: `1px solid ${s.border}`, borderRadius: s.radius, fontSize: "13px", color: s.text2 }}>
            <option value="">全部来源</option>
            <option value="CHECKIN">签到</option>
            <option value="ASSESSMENT">量表</option>
            <option value="XIAOAI">小爱倾听</option>
          </select>
        </div>

        {loading ? (
          <div style={{ padding: "40px", textAlign: "center", color: s.text2 }}>加载中...</div>
        ) : data.records.length === 0 ? (
          <div style={{ padding: "40px", textAlign: "center", color: s.text2 }}>暂无数据</div>
        ) : (
          <table style={{ width: "100%", borderCollapse: "collapse", border: `1px solid ${s.border}` }}>
            <thead>
              <tr style={{ backgroundColor: s.bg }}>
                {["ID", "用户", "来源", "等级", "触发方式", "状态", "创建时间", "操作"].map((h) => (
                  <th key={h} style={{ padding: "12px 8px", textAlign: "left", fontSize: "13px", color: s.text3, fontWeight: 600, borderBottom: `1px solid ${s.border}` }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.records.map((row, i) => {
                const st = row.status as number;
                return (
                  <tr key={i} style={{ backgroundColor: st === 0 ? "#FFF4F2" : i % 2 === 0 ? "#fff" : s.bg }}>
                    <td style={{ padding: "12px 8px", borderBottom: `1px solid ${s.border}`, fontSize: "13px" }}>{row.id as string}</td>
                    <td style={{ padding: "12px 8px", borderBottom: `1px solid ${s.border}`, fontSize: "13px" }}>{row.userId as string}</td>
                    <td style={{ padding: "12px 8px", borderBottom: `1px solid ${s.border}`, fontSize: "13px" }}>{SOURCE_LABELS[row.sourceType as string] ?? (row.sourceType as string)}</td>
                    <td style={{ padding: "12px 8px", borderBottom: `1px solid ${s.border}`, fontSize: "13px", color: s.danger, fontWeight: 600 }}>{row.level as string}</td>
                    <td style={{ padding: "12px 8px", borderBottom: `1px solid ${s.border}`, fontSize: "13px" }}>{TRIGGER_LABELS[row.triggerType as string] ?? (row.triggerType as string)}</td>
                    <td style={{ padding: "12px 8px", borderBottom: `1px solid ${s.border}`, fontSize: "13px" }}>
                      <span style={{ color: statusColor(st), fontWeight: st === 0 ? 700 : 400 }}>{STATUS_LABELS[st] ?? st}</span>
                    </td>
                    <td style={{ padding: "12px 8px", borderBottom: `1px solid ${s.border}`, fontSize: "13px" }}>{row.createTime as string}</td>
                    <td style={{ padding: "12px 8px", borderBottom: `1px solid ${s.border}` }}>
                      <button onClick={() => router.push(`/admin/alerts/${row.id}`)} style={{ color: s.primary, border: "none", background: "none", cursor: "pointer" }}>查看</button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}

        <div style={{ display: "flex", justifyContent: "flex-end", marginTop: "16px", gap: "8px", alignItems: "center" }}>
          <span style={{ fontSize: "13px", color: s.text2 }}>共 {data.total} 条</span>
          <button onClick={() => fetchData(data.current - 1)} disabled={data.current <= 1} style={{ padding: "6px 12px", border: `1px solid ${s.border}`, borderRadius: s.radius, background: s.white, cursor: "pointer" }}>上一页</button>
          <span style={{ fontSize: "13px", color: s.text2 }}>{data.current} / {data.pages || 1}</span>
          <button onClick={() => fetchData(data.current + 1)} disabled={data.current >= data.pages} style={{ padding: "6px 12px", border: `1px solid ${s.border}`, borderRadius: s.radius, background: s.white, cursor: "pointer" }}>下一页</button>
        </div>
      </div>
    </div>
  );
}
