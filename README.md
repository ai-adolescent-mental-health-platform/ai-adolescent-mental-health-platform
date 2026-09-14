# 青少年心理健康 AI 平台（ai-adolescent-mental-health-platform）

本仓库是一套面向青少年心理健康场景的 AI 辅助平台，包含 AI 问诊助手「小艾」、真人心理咨询、心理量表评估、心理内容库、心理咨询师目录等业务域，多端覆盖 Web 用户端、Web 管理端、Android 客户端与微信小程序。

仓库采用 `pnpm + Turborepo` 组织成 monorepo，不同技术栈（Java / Next.js / Vue / Kotlin / WeChat MiniProgram）共存于 `apps/*` 下，由根目录统一编排与依赖锁定。

## 架构总览

| 端 | 技术栈 | 工作区 | 对外角色 |
| --- | --- | --- | --- |
| 后端 API | Spring Boot 3.5.9 / Java 17 | `apps/backend` | 所有客户端共享的核心服务 |
| Web 用户端（当前） | Next.js 16 / React 19 / Tailwind CSS 4 | `apps/web-client` | 面向青少年及家长用户 |
| Web 管理端（当前） | Next.js 16 / React 19 / TypeScript | `apps/admin-portal` | 运营/心理咨询师/管理员使用 |
| 手机端（规划中） | 待填充（拟 uniapp） | `apps/mobile` | 面向青少年及家长用户（规划中） |
| 家长端（规划中） | 待填充 | `apps/parent-portal` | 面向家长用户（规划中） |

数据库脚本分两处：**建库脚本**在 `apps/backend/sql/`（`schema.sql` 全量表结构 + 补丁文件 + 字典种子数据），**增量与运维脚本**在 `infra/sql/`。

## 前置环境

- Node.js `>=24.0.0`
- pnpm `>=10.33.0`（仓库已通过 `packageManager` 字段固定为 `pnpm@10.33.0`）
- Java 17 JDK（后端；`JAVA_HOME` 指向 Java 17 兼容版本）
- MySQL 8+（库名 `xinyuzhilian`）
- Redis 6+
- RabbitMQ（可选；部分消息相关业务依赖）

## 仓库结构

```
.
├── apps
│   ├── backend          # Spring Boot 后端服务
│   ├── web-client       # 当前 Web 用户端（Next.js）
│   └── admin-portal     # 当前 Web 管理端（Next.js）
├── infra
│   └── sql              # 数据库增量/运维脚本（建库脚本见 apps/backend/sql）
├── scripts              # Turbo/pnpm 调用 mvnw 的桥接脚本
├── package.json         # 根工作区脚本
├── pnpm-workspace.yaml  # 工作区通配：apps/*
└── turbo.json           # Turbo 任务管线
```

工作区包名命名空间统一为 `@ai-adolescent-mental-health/<app>`。

## 快速开始

```bash
# 在仓库根目录
pnpm install
```

启动所有工作区的开发进程（并行）：

```bash
pnpm dev
```

只启动某个 app：

```bash
pnpm dev:backend      # 等价：pnpm --filter @ai-adolescent-mental-health/backend dev
pnpm dev:web-client   # 等价：pnpm --filter @ai-adolescent-mental-health/web-client dev
pnpm --filter @ai-adolescent-mental-health/admin-portal dev
```

对应各 app 的命令约定（由 `scripts/run-workspace-bin.cjs` 桥接）：

- 后端：`pnpm dev` → `mvnw spring-boot:run`，`pnpm build` → `mvnw clean package -DskipTests`
- Web 用户端：`pnpm dev` → `next dev --port 3300`，`pnpm build` → `next build`
- Web 管理端：`pnpm dev` → `next dev --port 3101`，`pnpm build` → `next build`

## 常用脚本

| 脚本 | 作用 |
| --- | --- |
| `pnpm dev` | 并行启动所有工作区的 `dev` 任务 |
| `pnpm build` | 依拓扑顺序构建所有工作区（`^build`） |
| `pnpm test` | 先构建依赖再运行测试 |
| `pnpm typecheck` | 对支持的工作区执行类型检查 |
| `pnpm clean` | 清理各工作区产物及根目录 `.turbo` |
| `pnpm dev:backend` / `pnpm dev:web-client` | 单独启动后端 / 当前 Web 用户端开发 |
| `pnpm --filter @ai-adolescent-mental-health/admin-portal dev` | 单独启动当前 Web 管理端开发 |
| `pnpm test:backend` | 只跑后端测试 |
| `pnpm --filter @ai-adolescent-mental-health/admin-portal typecheck` | 只跑当前 Web 管理端类型检查 |

通过 `--filter` 还可以精确到任何工作区：

```bash
pnpm --filter @ai-adolescent-mental-health/web-client build
pnpm --filter @ai-adolescent-mental-health/admin-portal build
pnpm --filter @ai-adolescent-mental-health/backend test
```

Turbo 的 `build` 与 `test` 任务均依赖 `^build`（上游工作区先构建），`dev` 配置为 `persistent: true` 且不缓存，`clean` 亦不缓存。详见 [turbo.json](turbo.json)。

## 后端配置与环境变量

后端配置主文件：[apps/backend/src/main/resources/application.yml](apps/backend/src/main/resources/application.yml)。仓库不再提交 `application-dev.yml` / `application-test.yml` 等环境配置文件；CI/CD 通过环境变量注入，本地开发可复制 [apps/backend/.env.example](apps/backend/.env.example) 为 `apps/backend/.env`。缺少必需变量时应让应用启动失败，避免使用无效默认值。

需要在本地或部署环境设置的常见变量：

| 变量 | 用途 |
| --- | --- |
| `DB_MYSQL_HOST` / `DB_MYSQL_PORT` / `DB_MYSQL_DATABASE` | MySQL 地址、端口与数据库名 |
| `DB_MYSQL_USERNAME` / `DB_MYSQL_PASSWORD` | MySQL 账号与密码 |
| `DB_REDIS_HOST` / `DB_REDIS_PORT` | Redis 地址与端口 |
| `ALIYUN_OSS_ENDPOINT` / `ALIYUN_OSS_BUCKET_NAME` | 阿里云 OSS endpoint 与 bucket |
| `ALIYUN_OSS_ACCESS_KEY_ID` / `ALIYUN_OSS_ACCESS_KEY_SECRET` | 阿里云 OSS 凭据 |
| `JWT_SECRET` | JWT 签名密钥（HS256，至少 256 位） |
| `DASHSCOPE_API_KEY` | 阿里云百炼 DashScope API Key（小艾 AI 对话） |
| `WX_APP_ID` / `WX_APP_SECRET` | 微信小程序 AppID 与 AppSecret |
| `WX_GZH_APP_ID` / `WX_GZH_SECRET` | 微信公众号 AppID 与 AppSecret |
| `WX_GZH_CALLBACK_BASE_URL` | 微信公众号授权回调基础地址 |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 服务配置 |

> **CORS**：后端已放开跨源限制（`allowedOriginPatterns("*")`），任意浏览器来源均可访问；认证仍由 JWT 校验拦截保护，前端走 `Authorization` 头而非 cookie，故不开启 CORS credentials。已无 `APP_CORS_ALLOWED_ORIGINS` 环境变量。

本地起服前请确保：

1. MySQL 中已有数据库 `xinyuzhilian`，并按 `apps/backend/sql/` 下的脚本完成初始化（见 [SETUP.md](SETUP.md) 第三节）；
2. Redis 在 `localhost:6379` 可用；
3. 如需 RabbitMQ 相关功能，本地 `guest:guest@localhost:5672` 可达。

## 常见问题

- **首次 `pnpm dev` 报端口占用**：后端默认 `server.port=8080`，当前 Web 用户端默认 `3300`，当前 Web 管理端默认 `3101`，请先确认占用情况。

## 分支与协作

- 主干：`main`
- 提交前建议至少执行：

  ```bash
  pnpm typecheck
  pnpm --filter <受影响的工作区> test
  ```

- 涉及数据库 schema 变更：改表结构同步更新 `apps/backend/sql/schema.sql`，一次性增量补丁放 `infra/sql/`，并在 PR 中说明。

## 进一步阅读

- 面向 AI 编码助手（Claude Code / Codex / Cursor 等）的上下文速查：[AGENTS.md](AGENTS.md)
- 各 app 单独的 AI 指引：
  - [apps/backend/AGENTS.md](apps/backend/AGENTS.md)
  - [apps/web-client/AGENTS.md](apps/web-client/AGENTS.md) — 用户端 pouf 页面设计规范见 [apps/web-client/design.md](apps/web-client/design.md)
  - [apps/admin-portal/AGENTS.md](apps/admin-portal/AGENTS.md)
