/**
 * 路由守卫（uni-app 版）。
 *
 * 背景：uni-app 没有 layout 概念，无法像 web-client / admin-portal 那样用
 * `<AuthGuard>` 包裹一个路由组（设计文档 5.3）。这里用两层实现同一套规则：
 *
 * 1. 全局导航拦截：`uni.addInterceptor` 挂在 navigateTo / redirectTo / switchTab / reLaunch 上，
 *    在跳转发起前拦截，未登录直接取消本次导航并改跳登录页。
 * 2. 页面级兜底：受保护页面在 `onLoad` 调用 `ensureAuthenticated()`。
 *    全局拦截管不到「App 冷启动直达某页」「H5 直接输入 URL」这两类入口，必须靠这一层。
 *
 * 规则与 web-client 保持一致（设计文档 5.3）：未登录跳登录页；登录成功后回跳原目标页。
 */

import { isLoggedIn } from "@/lib/session";
import { safeRedirect } from "@/lib/safe-redirect";
import { HOME_PAGE, LOGIN_PAGE, TAB_PATHS, normalizePath, requiresAuth, type NavApi } from "./routes";

const NAV_APIS: NavApi[] = ["navigateTo", "redirectTo", "switchTab", "reLaunch"];

let guardInstalled = false;
/** 复入保护：拦截器内部发起的跳转（改跳登录页）不能再被自己拦一次。 */
let redirecting = false;

/** 构造登录页地址，并把原目标页带上，供登录成功后回跳。 */
export function buildLoginUrl(target: string): string {
  const safeTarget = safeRedirect(target, HOME_PAGE);
  /*
   * 这里**不做** encodeURIComponent：uni-app 的 H5 路由在序列化 hash 时会自行编码查询值，
   * 若此处先编码一次，地址栏会出现 %252F 这样的双重编码；会话内虽仍能解回正确路径，
   * 但用户在该页刷新后会解析成 "%2Fpages/..."，safeRedirect 判定不是合法应用内路径而退回首页，
   * 等于刷新即丢失回跳目标。所有进入本函数的 target 都先经 safeRedirect 校验且已去掉查询串
   * （见 routes.normalizePath），因此直接拼接是安全的。
   */
  return `${LOGIN_PAGE}?redirect=${safeTarget}`;
}

/** 取消当前导航并改跳登录页。 */
export function redirectToLogin(target: string): void {
  if (redirecting) return;
  redirecting = true;
  uni.reLaunch({
    url: buildLoginUrl(target),
    complete: () => {
      redirecting = false;
    },
  });
}

/**
 * 页面级守卫：受保护页面在 onLoad 中调用。
 * @returns 是否放行（false 表示已改跳登录页）
 */
export function ensureAuthenticated(path: string): boolean {
  if (!requiresAuth(path)) return true;
  if (isLoggedIn()) return true;
  redirectToLogin(path);
  return false;
}

/** 安装全局导航拦截。在 main.ts 中调用一次。 */
export function installRouteGuard(): void {
  if (guardInstalled) return;
  guardInstalled = true;

  for (const api of NAV_APIS) {
    uni.addInterceptor(api, {
      invoke(args: { url?: string }) {
        // 守卫自身发起的跳转直接放行，否则会形成「拦自己」的死循环。
        if (redirecting) return true;

        const url = typeof args?.url === "string" ? args.url : "";
        if (!url) return true;

        const path = normalizePath(url);
        if (!requiresAuth(path)) return true;
        if (isLoggedIn()) return true;

        redirectToLogin(path);
        // 返回 false 取消本次导航。
        return false;
      },
    });
  }
}

/** 登录成功后按原目标页回跳；tabBar 页面必须用 switchTab。 */
export function navigateAfterLogin(target: string): void {
  const safeTarget = safeRedirect(target, HOME_PAGE);
  if (TAB_PATHS.includes(safeTarget)) {
    uni.switchTab({ url: safeTarget });
    return;
  }
  uni.reLaunch({ url: safeTarget });
}

/** 供页面直接使用的当前页面路径（不含查询串）。 */
export function getCurrentRoute(): string {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as { route?: string } | undefined;
  return normalizePath(current?.route ?? "");
}
