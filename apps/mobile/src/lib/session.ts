/**
 * 手机端会话存储（uni-app 运行时）。
 *
 * 为什么不用 web-client 的 `lib/session.ts`：那一层基于 `localStorage`，
 * uni-app 的 App 端不提供该 API，无法复用（设计文档 2.5 / 5.2）。
 *
 * 键名与 web-client（`aiamh.webClient.*`）、admin-portal（`aiamh.adminPortal.*`）刻意区分：
 * 三端可能在同一设备或同一浏览器域下共存，共用键名会互相覆盖会话。
 * `aiamh.mobile.*` 与 plans/2026-09-20-mobile-api-adapter.md 给出的接入示例一致。
 *
 * ---------------------------------------------------------------------------
 * 已知并接受的取舍：会话凭据以明文形式落盘
 * ---------------------------------------------------------------------------
 * 本模块使用 `uni.setStorageSync` / `getStorageSync`，**不做任何加密**。
 * 这是设计文档 5.2 明确记录并接受的取舍，不是疏漏：手机端持有的是未成年人
 * 心理健康数据对应的会话凭据，设备丢失或应用沙箱被突破的后果比浏览器更重。
 *
 * 不加密的理由是工程性的：加密存储需要 `plus.storage` 一类 **App 端专有 API**，
 * 会把会话层绑死在单一平台，H5 预览与将来可能的其他端都无法复用。
 *
 * 待办：安全评审时重新评估本取舍（设计文档 11 节「已知风险」第 2 条）。
 * 在本项被重新评估并给出结论之前，不要擅自替换为平台专有加密存储。
 */

const TOKEN_KEY = "aiamh.mobile.token";
const USER_KEY = "aiamh.mobile.user";

/** 读取 token；未登录或读取失败时返回 null。 */
export function getToken(): string | null {
  try {
    const raw = uni.getStorageSync(TOKEN_KEY) as unknown;
    return typeof raw === "string" && raw.length > 0 ? raw : null;
  } catch {
    return null;
  }
}

/** 读取用户信息；未登录或内容损坏时返回 null。 */
export function getStoredUser<T = Record<string, unknown>>(): T | null {
  try {
    const raw = uni.getStorageSync(USER_KEY) as unknown;
    if (!raw) return null;
    // 部分平台会把对象原样存回，故两种形态都要能读。
    if (typeof raw === "object") return raw as T;
    return JSON.parse(String(raw)) as T;
  } catch {
    return null;
  }
}

/** 保存会话（token + user）。 */
export function saveSession(token: string, user: Record<string, unknown>): void {
  uni.setStorageSync(TOKEN_KEY, token);
  uni.setStorageSync(USER_KEY, JSON.stringify(user));
}

/** 清除会话。登出与 401 均走此处。 */
export function clearSession(): void {
  try {
    uni.removeStorageSync(TOKEN_KEY);
  } catch {
    // removeStorageSync 在键不存在时各端行为不一，忽略即可。
  }
  try {
    uni.removeStorageSync(USER_KEY);
  } catch {
    // 同上。
  }
}

/** 是否已登录。判定口径与 web-client 一致：只看 token 是否存在。 */
export function isLoggedIn(): boolean {
  return getToken() !== null;
}

/**
 * 就地更新已存会话中的用户字段（如资料保存后同步昵称/头像）。
 *
 * 只做浅合并，不触碰 token；token 不存在时不做任何事，避免在未登录状态下凭空造出会话。
 */
export function updateStoredUser(patch: Record<string, unknown>): void {
  if (!getToken()) return;
  const current = getStoredUser() ?? {};
  uni.setStorageSync(USER_KEY, JSON.stringify({ ...current, ...patch }));
}
