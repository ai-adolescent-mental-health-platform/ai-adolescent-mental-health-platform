# AGENTS.md — `apps/mobile`

> 面向 AI 编码助手的手机端工作区速查。动手前请同时参阅根 [AGENTS.md](../../AGENTS.md) 与设计文档
> [docs/superpowers/specs/2026-09-20-mobile-app-design.md](../../docs/superpowers/specs/2026-09-20-mobile-app-design.md)。

## 一、工作区基本事实

- 包名：`@ai-adolescent-mental-health/mobile`
- 技术栈：uni-app CLI(vite) 形态 + Vue 3 + TypeScript；编译器版本 5.26（vue3）
- 端口：**3200**（`vite.config.ts` 的 `server.port`，避免与 backend 8080 / web-client 3300 / admin-portal 3101 冲突）
- 项目形态取舍见设计文档 11.1：选 CLI 形态是为了保留 pnpm workspace 插槽、直接 import `packages/domain`，
  代价是多端编译配置的学习成本
- 交付目标端：**App（Android / iOS）**，H5 仅用于本地预览
- 当前进度：工程骨架 + 会话存储 + 路由守卫 + 基础 UI 组件最小集 + 统一 api 层（`src/lib/api.ts`）
  + 登录 / 注册 / 找回密码（#33）+ 个人信息与设置（#34）+ 我的消息与互动（#35）。
  **真实后端联调与 App 端真机验证均未进行**

## 二、目录结构

```
apps/mobile/
├── index.html                  # H5 端入口模板
├── vite.config.ts              # uni 插件 + 端口；不声明 "@/" 别名（插件已注入）
├── tsconfig.json
└── src/
    ├── main.ts                 # createApp 工厂；安装路由守卫
    ├── App.vue                 # 全局钩子 + 引入 theme.css / layout.css
    ├── manifest.json           # 多端打包配置
    ├── pages.json              # 页面与 tabBar 登记（与 router/routes.ts 必须同步）
    ├── env.d.ts                # 手写 ImportMetaEnv（不引 vite/client，原因见文件内注释）
    ├── styles/
    │   ├── theme.css           # 主题变量层：唯一色值来源，含实测对比度
    │   └── layout.css          # 跨页共享布局类，不含色值
    ├── lib/
    │   ├── api.ts              # 统一 api 层：页面唯一的请求出口（含 adapter 两处断言与上传封装）
    │   ├── session.ts          # 会话四个操作 + updateStoredUser（uni 存储）
    │   ├── validation.ts       # 表单校验（正则对齐后端 RegexConstant）+ 密码强度
    │   ├── use-paged-list.ts   # 分页列表状态（所有列表页共用，见第四节第 8 条）
    │   └── safe-redirect.ts    # 登录回跳目标校验
    ├── router/
    │   ├── routes.ts           # 路由表（决定哪些页面需要登录）
    │   └── guard.ts            # 全局导航拦截 + 页面级兜底
    ├── config/
    │   └── legal.ts            # 协议与政策正文（后端无对应接口，故用配置文件承载）
    ├── components/             # MButton / MInput / MCard / MNavBar / MListState / MInteractionList
    └── pages/                  # home / consultation / me / login / register / forgot-password / legal
                                # me 下另有：info security privacy messages favorites likes follow articles publish feedback
```

## 三、命令

```bash
pnpm --filter @ai-adolescent-mental-health/mobile dev:h5      # H5 预览（127.0.0.1:3200）
pnpm --filter @ai-adolescent-mental-health/mobile dev:app     # App 端调试（需 HBuilderX/真机）
pnpm --filter @ai-adolescent-mental-health/mobile build:h5
pnpm --filter @ai-adolescent-mental-health/mobile build:app
pnpm --filter @ai-adolescent-mental-health/mobile typecheck   # vue-tsc --noEmit
```

`dev:app` 只是让 uni 编译出 App 资源，**真机运行与打包仍需 HBuilderX 或原生工具链**；
H5 预览跑通不代表 App 端可用（设计文档 6.1）。

## 四、AI 约束

1. **不引 `packages/ui`**：那里的 pouf 组件是 React + Tailwind，uni-app 渲染不了。青少年风格组件长在
   `src/components/` 内，等出现第二个消费端再考虑提取（设计文档第 3 节）。
2. **色值唯一来源是 `src/styles/theme.css`**。组件内禁止写死色值，一律 `var(--color-*)`。
   唯一例外：`src/pages.json` 的 `tabBar` 颜色只能是字面量（静态 JSON 无法引用 CSS 变量），
   改主题色时必须同步该处，两处不一致会导致 tab 颜色与页面主题漂移。
3. **无障碍硬约束**：正文文本与背景对比度不低于 4.5:1；可交互控件边界不低于 3:1（用 `--color-border-control`）；
   **粉彩填充（`--color-surface-muted`）上禁止白字**，只允许配深色字。改色后必须重算并在 `theme.css` 更新实测值。
4. **会话键名**：`aiamh.mobile.token` / `aiamh.mobile.user`，与 web-client（`aiamh.webClient.*`）、
   admin-portal（`aiamh.adminPortal.*`）刻意区分，三端共存时不得复用键名。
5. **明文存储是已接受的取舍**（设计文档 5.2）：不得擅自引入 `plus.storage` 等 App 端专有加密 API，
   那会把会话层绑死单一平台。安全评审时重新评估。
6. **路由守卫是两层**：`uni.addInterceptor` 全局拦截 + 受保护页面 `onLoad` 兜底。
   新增页面必须同时在 `src/router/routes.ts` 与 `src/pages.json` 登记；
   routes.ts 对**未登记路径按「需要登录」处理**，忘记登记不会导致页面裸奔。
7. **tab 页取数用 `onShow`，不用 `onLoad`**（本工作区实测踩过）。
   原因：uni-app 会保留并复用 tabBar 页面的实例，**再次切回该 tab 时 `onLoad` 不会重新触发**。
   若把「读会话」「拉资料」这类逻辑只写在 `onLoad`，就会出现**已登录但页面仍显示上次甚至初始占位值**
   的陈旧状态（实测现象：登录后切回个人中心仍显示「未记录 / token 无」）。
   适用范围：**全部 tabBar 页面**，即 `src/pages.json` 的 `tabBar.list` 里的
   `pages/home/index`、`pages/consultation/index`、`pages/me/index` 三个页面。
   非 tab 页面按普通页面处理，`onLoad` 取数即可。新增 tab 页时必须遵守本条。
8. **列表页一律用 `src/lib/use-paged-list.ts` + `src/components/MListState.vue`，不要另写一套分页**。
   原因：分页语义（首屏加载、加载更多、`current`/`pages` 游标、末尾判定、失败时清空列表并复位游标、
   防重复追加）一旦分散到各页面，就会变成多份各自为政的实现——改一处漏一处，而「分页是否正确」
   恰恰是最难靠肉眼发现的问题。收在一处后，页面只负责渲染条目与注入取数函数。
   适用范围：**所有带分页的列表页**（含「加载更多」形态的滚动列表）。
   - 条目结构相同、仅取数不同的列表，把渲染也收进共用组件（参考 `MInteractionList.vue` 收收藏与点赞）；
   - 静态内容的多段渲染（如 `pages/legal/index.vue` 按章节 v-for）不属于分页列表，不适用本条；
   - 单个对象取数（如个人中心的 `getUserInfo`）不是列表，不适用本条。
   - **当前状态：全部列表页均已遵循**（`messages` / `favorites` / `likes` / `follow` / `articles` / `feedback`）。
     无遗留未遵循的列表页。新增列表页时必须遵守本条。
9. **不跨 app 复制源文件**（根 AGENTS.md 规则 3）。
10. **文档不写 emoji**。

## 五、已知坑（均在本工作区实测）

- **`pnpm install` 报 `ERR_PNPM_IGNORED_BUILDS`**：uni-app 依赖链引入 `core-js` / `core-js-pure` / `esbuild`
  的构建脚本，根 `pnpm-workspace.yaml` 的 `allowBuilds` 白名单尚未登记它们。pnpm 会自行往该文件写入
  `set this to true or false` 占位项并仍然报错，需要人工决策后才会消失。**该文件不在手机端工作区的修改范围**。
- **不要给本包加 `"type": "module"`**：`@dcloudio/vite-plugin-uni` 是 CJS 包，插件函数挂在
  `module.exports.default` 上；本包若标为 ESM，Node 的原生互操作会把默认导入解析成命名空间对象，
  启动即报 `uni is not a function`。这是唯一一个刻意偏离 monorepo 其他工作区（均为 ESM）的地方。
- **`vite` 必须是本包的显式 devDependency**：插件把 vite 声明为 peer，pnpm 不会把它链接到应用内，
  缺声明时 dev server 报 `Cannot find package 'vite' imported from .../vite.config.ts`。
- **根 `pnpm-workspace.yaml` 的 `overrides.vite` 固定在 8.0.16**，而插件 peer 要求 `vite: 5.2.8`（精确版本）。
  实测 H5 dev server 能启动，但 vite 会输出 `esbuild` / `optimizeDeps.esbuildOptions` / `customResolver`
  三条弃用警告；**App 端未验证**。若后续出现编译期异常，优先怀疑此处版本错配。
- **不要声明自定义 `@/` 别名**：插件已注入 `/^(~@|@)\//` → `<inputDir>` 的别名（`vite-plugin-uni/dist/config/resolve.js`），
  重复声明只会引入 `import.meta.url` 这类在 CJS 配置下不可用的写法。
- `vue-tsc` 会把 `<input>` 解析成 DOM 的 input 元素类型而非 uni 组件，事件回调参数不要写 DOM 形状的类型，
  用 `unknown` 收窄（见 `MInput.vue`）。
- **回跳参数不要自己 `encodeURIComponent`**：uni-app 的 H5 路由在序列化 hash 时会再编码一次，
  先编码会得到 `redirect=%252Fpages%252Fme%252Findex` 这样的双重编码。会话内仍能解回正确路径，
  但**用户在该页刷新后回跳目标会丢失**（`safeRedirect` 判定 `%2Fpages/...` 不是合法应用内路径而退回首页）。
  见 `router/guard.ts` 的 `buildLoginUrl`。

## 六、共享层接入现状与遗留

统一 api 层已在 `src/lib/api.ts` 落地（`createHttpClient` + `createUniAdapter` + `createApiClient`），
页面一律经 `api` 调用，**禁止**在页面内直接 `uni.request` / `fetch`；401 统一由
`createHttpClient` 的 `onUnauthorized` 处理，页面层不得自行判断状态码。

该文件里的两处类型断言是接线层面的权宜处理，成因写在文件注释中：适配器入参
（`method: string` / `data?: unknown`）与 uni 真实类型逆变不兼容；`uni.request` 的 promisify 重载
被排在前面，导致类型层面返回 `Promise`（缺 `abort`）。根治应在 `packages/api-client` 侧收窄
`UniRequestOptions`，不属于手机端工作区。

**遗留（issue #32 记录，仍未修）**：适配器丢弃了 `uni.request` 返回的 `RequestTask` 句柄，
`abort()` 从未被调用，因此 axios 的取消语义在手机端不生效。登录 / 注册 / 找回密码已走该链路，
但请求都很短，当前影响有限。
