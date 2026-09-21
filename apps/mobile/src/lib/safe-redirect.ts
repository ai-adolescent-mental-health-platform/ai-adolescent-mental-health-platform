/**
 * 登录回跳目标的校验。
 *
 * 规则与 web-client 的 `lib/safe-redirect.ts` **保持一致**（设计文档 5.3 要求两端行为不漂移）：
 * 只接受以单个 `/` 开头的应用内路径，拒绝协议相对地址（`//evil.com`）与任何带 `:` 的
 * 串（`javascript:` / `data:` / `https:` 等），以防开放重定向钓鱼。
 *
 * 本文件按手机端的路由形态（`/pages/xxx/index`）重写，不是对 web-client 源文件的复制。
 */

/** 未指定回跳目标时的落地页，与 web-client 登录后默认回到 /home 同义。 */
export const DEFAULT_AUTH_TARGET = "/pages/home/index";

export function safeRedirect(raw: string | null | undefined, fallback: string = DEFAULT_AUTH_TARGET): string {
  if (!raw || typeof raw !== "string") return fallback;
  const trimmed = raw.trim();
  if (!trimmed) return fallback;
  if (!trimmed.startsWith("/")) return fallback;
  if (trimmed.startsWith("//")) return fallback;
  if (trimmed.includes(":")) return fallback;
  return trimmed;
}
