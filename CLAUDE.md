# 心愈智联 — 青少年心理健康 AI 平台

`pnpm + Turborepo` monorepo。AI 问诊「小艾」、真人咨询、量表评估、内容库、心理咨询师目录。多端共存于 `apps/*`，面向 AI 的完整约束见 [AGENTS.md](AGENTS.md)。

## 工作区与端口

| 包 | 技术栈 | 端口 | 说明 |
| --- | --- | --- | --- |
| `apps/backend` | Spring Boot 3.5.9 / Java 17 | 8080 | 后端 API，所有端共享 |
| `apps/web-client` | Next.js 16 / React 19 / Tailwind 4 | **3300** | 青少年/家长用户端，**pouf 黏土设计** |
| `apps/admin-portal` | Next.js 16 / React 19 | **3101** | 运营/心理咨询师管理端 |

建库 SQL 在 `apps/backend/sql/`（`schema.sql` 全量表结构 + 补丁文件 + 字典种子数据），增量与运维脚本在 `infra/sql/`。

## 最短命令

```bash
pnpm dev                # 全部 app 并行
pnpm dev:backend        # 只起后端
pnpm dev:web-client     # 只起用户端
pnpm --filter @ai-adolescent-mental-health/admin-portal dev   # 管理端
pnpm build / test / typecheck / clean
pnpm test:backend
```

## AI 黄金规则（改代码前务必核对 AGENTS.md）

1. 不改 `pnpm-lock.yaml`，除非用户明确要求升级/新增依赖。仓库固定 `pnpm 12.3.4`（corepack），锁文件为 pnpm 12 格式；构建脚本白名单走 `pnpm-workspace.yaml` 的 `allowBuilds`。
2. 不加平行 AI 规约文件（本仓库统一 `AGENTS.md`）。
3. 不跨 app 复制源文件；建库 SQL 放 `apps/backend/sql/`，跨工作区的增量脚本/常量放 `infra/`。
4. 改根级配置（`turbo.json`、`pnpm-workspace.yaml`、根 `package.json`、`application*.yml`）先说明理由。
5. 不提交真实密钥/`.env`/`application-dev.yml` 等 profile 配置；敏感项走 `${ENV_VAR}`。
6. 工作区依赖放对应子包 `package.json` / `pom.xml`，不塞根 `package.json`。
7. 文档默认不写 emoji；先改代码再同步文档。

## 深入

- 用户端 pouf 页面设计规范：[apps/web-client/design.md](apps/web-client/design.md)
- 各 app 详细指引：`apps/<name>/AGENTS.md`（进入前先读）
