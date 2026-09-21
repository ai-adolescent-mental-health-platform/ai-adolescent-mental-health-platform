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
- 当前进度：工程骨架 + 会话存储 + 路由守卫 + 基础 UI 组件最小集。
  登录/注册/个人中心的真实业务在 issue #33 起接入（`packages/api-client`）

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
    │   ├── session.ts          # 会话四个操作（uni 存储）
    │   └── safe-redirect.ts    # 登录回跳目标校验
    ├── router/
    │   ├── routes.ts           # 路由表（决定哪些页面需要登录）
    │   └── guard.ts            # 全局导航拦截 + 页面级兜底
    ├── components/             # MButton / MInput / MCard / MNavBar
    └── pages/                  # home / consultation / me / login / register
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
7. **不跨 app 复制源文件**（根 AGENTS.md 规则 3）。
8. **文档不写 emoji**。

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

## 六、待接入（issue #33 起）

`packages/api-client` + `createUniAdapter`。接入前需注意：适配器当前的入参类型与真实 uni 类型存在两处不兼容
（`method: string` 对 uni 的字面量联合、`data?: unknown` 对 `string | AnyObject | ArrayBuffer`），
直接 `createUniAdapter(uni.request)` 无法通过类型检查；`uni.request` 的启用 promisify 重载后返回 `Promise`，
需显式转换。详见 issue #32 的类型假设验证结论。
