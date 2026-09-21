/**
 * 预约 / 订单取数层（可复用）。
 *
 * 【待 issue #38（菜单 2：心理咨询）复用此处，不要另写一套】
 * 菜单 2 会用到同一批数据：咨询师目录 → 预约下单 → 我的预约/订单。
 * 那一步的列表、详情、状态文案都应当从本文件取，避免同一份「预约/订单」语义在手机端出现第二套实现
 * （web-client 侧就存在 orders-page 与 psychology-page 各自过滤同一份数据的情况）。
 *
 * 为什么单独成文件而不是写在页面里：
 * 1. 取数、字段收窄、状态文案是「订单」这一领域的三件事，与页面布局无关；
 * 2. 菜单 2 与个人中心的订单入口必须共用同一套字段映射，否则两端对同一个 payStatus 的解读会漂移。
 *
 * 关于分页：后端 `GET /psychologist/appointment/my` 接受 `page`/`size`/`status`，
 * 但共享层的 `api.appointment.my()` 把 `page`/`size` 写死为 `1/20` 且未暴露参数，
 * 因此这里用统一 api 层导出的 `httpClient` 调同一路径并传参——仍是走 api 层
 * （不是页面内直接 uni.request / fetch），只是绕开了共享层未暴露参数的缺口。
 * 待办：`packages/api-client` 给该方法补上 `params` 后改回 `api.appointment.my({ page, size, status })`。
 */

import { httpClient } from "./api";
import type { PageResultLike } from "./use-paged-list";

/** 后端 `PsychologistAppointmentServiceImpl.convertToVO` 返回的字段，按需收窄。 */
export type AppointmentOrder = {
  id: number;
  orderNo: string;
  psychologistName: string;
  serviceType: string;
  serviceTypeText: string;
  appointmentTime: string;
  fee: number;
  /** 支付状态：0 未支付 / 1 已支付 / 2 已退款（后端字段名为 camelCase 的 payStatus） */
  payStatus: number;
  /** 后端同时下发的文案，仅在本地表未收录该取值时兜底使用 */
  payStatusText: string;
  /** 预约状态，与支付状态是两个独立维度 */
  status: number;
  statusText: string;
};

/**
 * 支付状态文案（本表为主）。
 *
 * 口径说明：任务与数据库约定为 0-未支付 / 1-已支付 / 2-已退款；
 * 而后端 `getPayStatusText` 对 0 返回的是「待支付」。这里按**约定口径**显示「未支付」，
 * 未收录的取值才回退到后端下发的 `payStatusText`，避免出现「未知」这类无信息量的展示。
 */
export const PAY_STATUS_LABELS: Record<number, string> = {
  0: "未支付",
  1: "已支付",
  2: "已退款",
};

/**
 * 预约状态文案。取值来自后端 `getStatusText`（0-8），用于筛选器与条目展示的兜底。
 * 与支付状态相互独立：一笔订单可以是「已支付 + 已完成」，也可以是「已退款 + 已取消」。
 */
export const APPOINTMENT_STATUS_LABELS: Record<number, string> = {
  0: "待审核",
  1: "已确认",
  2: "已拒绝",
  3: "进行中",
  4: "已完成",
  5: "已取消",
  6: "已爽约",
  7: "待进行",
  8: "已评价",
};

export type AppointmentStatusFilter = {
  /** 显示名 */
  label: string;
  /** 传给后端的 status 参数；不传表示全部 */
  value?: number;
};

/** 预约记录页的状态筛选项（服务端支持按 status 过滤）。 */
export const APPOINTMENT_STATUS_FILTERS: AppointmentStatusFilter[] = [
  { label: "全部" },
  { label: "待审核", value: 0 },
  { label: "已确认", value: 1 },
  { label: "已拒绝", value: 2 },
  { label: "进行中", value: 3 },
  { label: "已完成", value: 4 },
  { label: "已取消", value: 5 },
];

export function payStatusLabel(item: Pick<AppointmentOrder, "payStatus" | "payStatusText">): string {
  const mapped = PAY_STATUS_LABELS[item.payStatus];
  if (mapped) return mapped;
  return item.payStatusText || `状态 ${item.payStatus}`;
}

export function appointmentStatusLabel(item: Pick<AppointmentOrder, "status" | "statusText">): string {
  const mapped = APPOINTMENT_STATUS_LABELS[item.status];
  if (mapped) return mapped;
  return item.statusText || `状态 ${item.status}`;
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" && value ? value : fallback;
}

function asNumber(value: unknown, fallback = 0): number {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim() !== "" && Number.isFinite(Number(value))) return Number(value);
  return fallback;
}

function formatDateTime(value: unknown): string {
  const raw = typeof value === "string" ? value : "";
  return raw ? raw.replace("T", " ").slice(0, 16) : "";
}

/** 把后端 VO 收窄成本地类型；缺失字段给出安全默认值，不抛错。 */
export function mapAppointment(raw: unknown): AppointmentOrder {
  const d = asRecord(raw);
  return {
    id: asNumber(d.id),
    orderNo: asString(d.orderNo),
    psychologistName: asString(d.psychologistName, "未命名咨询师"),
    serviceType: asString(d.serviceType),
    serviceTypeText: asString(d.serviceTypeText, asString(d.serviceType, "未知类型")),
    appointmentTime: formatDateTime(d.appointmentTime),
    fee: asNumber(d.fee),
    payStatus: asNumber(d.payStatus),
    payStatusText: asString(d.payStatusText),
    status: asNumber(d.status),
    statusText: asString(d.statusText),
  };
}

/**
 * 我的预约 / 订单分页查询。**这是手机端唯一一处调用该接口的地方**（菜单 2 也应复用本函数）。
 */
export async function fetchMyAppointments(params: {
  page: number;
  size: number;
  status?: number;
}): Promise<PageResultLike<AppointmentOrder>> {
  const query: Record<string, number> = { page: params.page, size: params.size };
  if (typeof params.status === "number") query.status = params.status;

  const result = await httpClient.get<PageResultLike<unknown>>("/psychologist/appointment/my", { query });
  return { ...result, records: (result.records ?? []).map(mapAppointment) };
}
