/**
 * 手机端路由表。
 *
 * uni-app 没有布局（layout）概念，无法像 web-client / admin-portal 那样用
 * `<AuthGuard>` 包裹一个路由组，因此把「哪些页面需要登录」显式登记在这里，
 * 由 router/guard.ts 在全局导航拦截与页面 onLoad 两处消费（设计文档 5.3）。
 *
 * 路径必须与 src/pages.json 的 pages[].path 严格一致（含前导斜杠）。
 */

export type NavApi = "navigateTo" | "redirectTo" | "switchTab" | "reLaunch";

export type RouteRule = {
  /** pages.json 中的页面路径 */
  path: string;
  /** 页面标题 */
  title: string;
  /** 是否需要登录态 */
  requiresAuth: boolean;
  /** 是否为 tabBar 页面（决定回跳用 switchTab 还是 reLaunch） */
  isTab?: boolean;
};

export const LOGIN_PAGE = "/pages/login/index";
export const REGISTER_PAGE = "/pages/register/index";
export const FORGOT_PASSWORD_PAGE = "/pages/forgot-password/index";
export const HOME_PAGE = "/pages/home/index";
export const ME_INFO_PAGE = "/pages/me/info/index";
export const ME_SECURITY_PAGE = "/pages/me/security/index";
export const ME_PRIVACY_PAGE = "/pages/me/privacy/index";
export const ME_MESSAGES_PAGE = "/pages/me/messages/index";
export const ME_FAVORITES_PAGE = "/pages/me/favorites/index";
export const ME_LIKES_PAGE = "/pages/me/likes/index";
export const ME_FOLLOW_PAGE = "/pages/me/follow/index";
export const ME_ARTICLES_PAGE = "/pages/me/articles/index";
export const ME_PUBLISH_PAGE = "/pages/me/publish/index";
export const ME_FEEDBACK_PAGE = "/pages/me/feedback/index";
export const LEGAL_PAGE = "/pages/legal/index";

export const ROUTES: RouteRule[] = [
  { path: HOME_PAGE, title: "问答", requiresAuth: true, isTab: true },
  { path: "/pages/consultation/index", title: "心理咨询", requiresAuth: true, isTab: true },
  { path: "/pages/me/index", title: "个人中心", requiresAuth: true, isTab: true },
  { path: ME_INFO_PAGE, title: "个人信息", requiresAuth: true },
  { path: ME_SECURITY_PAGE, title: "账号安全", requiresAuth: true },
  { path: ME_PRIVACY_PAGE, title: "隐私设置", requiresAuth: true },
  { path: ME_MESSAGES_PAGE, title: "我的消息", requiresAuth: true },
  { path: ME_FAVORITES_PAGE, title: "我的收藏", requiresAuth: true },
  { path: ME_LIKES_PAGE, title: "我的点赞", requiresAuth: true },
  { path: ME_FOLLOW_PAGE, title: "我的关注", requiresAuth: true },
  { path: ME_ARTICLES_PAGE, title: "我的文章", requiresAuth: true },
  { path: ME_PUBLISH_PAGE, title: "发布文章", requiresAuth: true },
  { path: ME_FEEDBACK_PAGE, title: "意见反馈", requiresAuth: true },
  { path: LOGIN_PAGE, title: "登录", requiresAuth: false },
  { path: REGISTER_PAGE, title: "注册", requiresAuth: false },
  { path: FORGOT_PASSWORD_PAGE, title: "忘记密码", requiresAuth: false },
  // 协议与政策需在注册前（未登录）即可查阅，故不要求登录态
  { path: LEGAL_PAGE, title: "协议与政策", requiresAuth: false },
];

/** 受登录保护的页面路径集合。 */
export const PROTECTED_PATHS: readonly string[] = ROUTES.filter((route) => route.requiresAuth).map(
  (route) => route.path,
);

/** tabBar 页面路径集合。 */
export const TAB_PATHS: readonly string[] = ROUTES.filter((route) => route.isTab).map((route) => route.path);

/**
 * 判断某个页面是否需要登录。
 *
 * 未登记的路径按「需要登录」处理：新增页面若忘记登记路由表，应被拦到登录页，
 * 而不是默认放行——放行的默认值会让受保护页面在遗漏登记时静默裸奔。
 */
export function requiresAuth(path: string): boolean {
  const normalized = normalizePath(path);
  const rule = ROUTES.find((route) => route.path === normalized);
  return rule ? rule.requiresAuth : true;
}

/** 去掉查询串与哈希，并补齐前导斜杠。 */
export function normalizePath(path: string): string {
  const withoutQuery = path.split("?")[0].split("#")[0];
  if (!withoutQuery) return "";
  return withoutQuery.startsWith("/") ? withoutQuery : `/${withoutQuery}`;
}
