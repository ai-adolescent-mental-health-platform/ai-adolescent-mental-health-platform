# AGENTS.md — 根目录 AI 协作速查

> 面向 AI 编码助手（Claude Code / Codex / Cursor 等）的上下文速查。先读这里再动手。人类开发者请优先读 [README.md](README.md)。
> 全局最简短版见根 [CLAUDE.md](CLAUDE.md)。

## 一、仓库定位

- 项目：`ai-adolescent-mental-health-platform`；形态：`pnpm + Turborepo` monorepo。
- 业务：青少年心理健康 AI 平台（AI 问诊「小艾」、真人咨询、量表评估、内容库、心理咨询师目录）。
- 主干：`main`。

## 二、工作区拓扑

| 路径 | 包名 | 技术栈 | 端口 |
| --- | --- | --- | --- |
| `apps/backend` | `@ai-adolescent-mental-health/backend` | Spring Boot 3.5.9 / Java 17 / Maven Wrapper | 8080 |
| `apps/web-client` | `@ai-adolescent-mental-health/web-client` | Next.js 16 / React 19 / Tailwind CSS 4 | 3300 |
| `apps/admin-portal` | `@ai-adolescent-mental-health/admin-portal` | Next.js 16 / React 19 / TypeScript | 3101 |
| `apps/mobile` | `@ai-adolescent-mental-health/mobile` | uni-app CLI(vite) / Vue 3 / TypeScript | 3200（H5 预览；App 端需 HBuilderX） |
| `apps/parent-portal` | `@ai-adolescent-mental-health/parent-portal` | 规划中 | — |

工作区通配见 [pnpm-workspace.yaml](pnpm-workspace.yaml)（`apps/*`）；共享 SQL 在 `infra/sql/`。

## 三、命令速查

根目录脚本（[package.json](package.json)）：

```bash
pnpm dev            # turbo run dev --parallel
pnpm build          # turbo run build
pnpm test           # turbo run test
pnpm typecheck      # turbo run typecheck
pnpm clean          # turbo run clean + 清理 .turbo

pnpm dev:backend        # 只起后端 (8080)
pnpm dev:web-client     # 只起用户端 (3300)
pnpm --filter @ai-adolescent-mental-health/admin-portal dev   # 管理端 (3101)
pnpm --filter @ai-adolescent-mental-health/mobile dev:h5      # 手机端 H5 预览 (3200)
pnpm test:backend       # 只跑后端测试
```

精确过滤：`pnpm --filter @ai-adolescent-mental-health/<app> <script>`。

Turbo 管线（[turbo.json](turbo.json)）：`build` 依赖 `^build`；`test` 依赖 `^build`；`dev` `persistent: true`、`cache: false`；`typecheck` 依赖 `^typecheck`；`clean` 不缓存。

## 四、变更约束（AI 严格遵守）

1. **不要修改 `pnpm-lock.yaml`**，除非用户明确要求升级/新增依赖。仓库由 corepack 固定 `pnpm 12.3.4`（根 `package.json` `packageManager` 已升级），锁文件为 pnpm 12 格式；pnpm 12 的构建脚本白名单走 `pnpm-workspace.yaml` 的 `allowBuilds`（未批准会拦截安装，报 approval 提示）。
2. **不要擅自新增平行 AI 规约文件**（`CLAUDE.md`、`.cursorrules`、`.github/copilot-instructions.md` 等）；统一用 `AGENTS.md`。
3. **不要跨 app 复制源文件**；各端独立演进，共享 SQL/常量放 `infra/`。
4. **修改根级配置先说明理由**：`turbo.json`、`pnpm-workspace.yaml`、根 `package.json`、`apps/backend/src/main/resources/application*.yml`。
5. **不提交真实密钥或环境配置文件**；敏感项经 `${ENV_VAR}` 读取，本地可用 `apps/backend/.env`；不提交真实 `.env` 或 `application-dev/test/local.yml`。
6. **工作区新增依赖**走对应子目录 `package.json` / `pom.xml` / `build.gradle.kts`，不塞根 `package.json`。
7. **文档默认不写 emoji**，与仓库风格一致。

## 五、文档自更新

- **鼓励**主动更新 `README.md` 与任意 `AGENTS.md`，当：文档与代码现状不符（脚本/路径/版本/端口/依赖/业务域漂移）；新增或删除工作区/脚本/环境变量/外部服务；引入新约定或常见坑；用户给出应长期遵循的规则。
- 遵循：**先改代码/配置，再同步文档**；**就近原则**（全局约束写根 `AGENTS.md`，仅某 app 的约束写 `apps/<name>/AGENTS.md`）；**保持锚点准确**（版本/包名/脚本名可核对）；**在提交信息说明文档变更**。
- **不要**未经同意：新增平行规约文件；大规模重写文档风格；删除他人添加且你不理解的条目（先询问）。

## 六、外部服务清单（后端）

后端直接依赖的三方能力（对应变量见 `apps/backend/src/main/resources/application.yml`）：

- 阿里云百炼 **DashScope**（`DASHSCOPE_API_KEY`）——小艾 AI 对话
- **阿里云 OSS**（`ALIYUN_OSS_*`）——文件存储
- **QQ 邮箱 SMTP**（`MAIL_*`）——验证码/通知邮件
- **MySQL / Redis / RabbitMQ**（`DB_MYSQL_*`/`DB_REDIS_*`）——数据层

本地启动后端最低运行期条件：MySQL（`xinyuzhilian`）+ Redis；RabbitMQ 视所触发业务而定。

## 七、提交前自检清单

| 改动范围 | 建议执行 |
| --- | --- |
| `apps/web-client` | `pnpm --filter @ai-adolescent-mental-health/web-client typecheck` |
| `apps/admin-portal` | `pnpm --filter @ai-adolescent-mental-health/admin-portal typecheck` |
| 后端代码或 SQL | `pnpm test:backend` |
| 跨工作区 | `pnpm typecheck && pnpm test` |
| 数据库 schema | 同步更新 `infra/sql/` 并在 PR 说明 |

## 八、子工作区深入

进入任何 `apps/<name>` 修改前，先读该目录下的 `AGENTS.md`：

- [apps/backend/AGENTS.md](apps/backend/AGENTS.md)
- [apps/web-client/AGENTS.md](apps/web-client/AGENTS.md) — 用户端 pouf 页面设计规范见 [apps/web-client/design.md](apps/web-client/design.md)
- [apps/admin-portal/AGENTS.md](apps/admin-portal/AGENTS.md)
- [apps/mobile/AGENTS.md](apps/mobile/AGENTS.md) — 手机端 uni-app 工程约束与已知坑
