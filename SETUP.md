# 快速运行指南

面向新加入的合作者，按顺序执行即可跑起完整项目。

## 一、前置环境

| 依赖 | 最低版本 | 说明 |
| --- | --- | --- |
| Node.js | >= 24.0.0 | 前端运行时 |
| pnpm | >= 10.33.0 | `npm i -g pnpm` |
| JDK | 17 | 后端编译运行，`JAVA_HOME` 指向此版本 |
| MySQL | 8.0+ | 数据库 |
| Redis | 6.0+ | 缓存/会话 |

RabbitMQ 可选（仅消息相关业务需要，本地开发可跳过）。

## 二、克隆与安装

```bash
git clone <仓库地址>
cd ai-adolescent-mental-health-platform
pnpm install
```

## 三、数据库初始化

创建库，然后按顺序导入表结构与字典数据（与 CI 的 `Initialize backend database` 步骤一致）：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS xinyuzhilian DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -u root -p xinyuzhilian < apps/backend/sql/schema.sql
mysql -u root -p xinyuzhilian < apps/backend/sql/schema_email_bind.sql
mysql -u root -p xinyuzhilian < apps/backend/sql/schema_xiaoai_usage.sql
mysql -u root -p xinyuzhilian < apps/backend/sql/seed_dict_data.sql
```

导入的是表结构与字典数据，不含业务数据。已有库需要补齐结构时，执行 `infra/sql/` 下的增量脚本。

## 四、后端配置

项目使用 `.env` 文件管理密钥（已在 `.gitignore` 中，不会提交）。

仓库已提供一份包含团队共享密钥的 `.env` 模板，直接可用：

```bash
# 确认文件存在
ls apps/backend/.env
```

如文件缺失，向团队成员索要后放入 `apps/backend/.env`。

> Redis 默认连接 `127.0.0.1:6379`，无需额外配置。

## 五、启动

```bash
# 一键启动全部（后端 + 用户端 + 管理端，并行）
pnpm dev

# 或单独启动
pnpm dev:backend          # 后端 → http://localhost:8080
pnpm dev:web-client       # 用户端 → http://localhost:3300
pnpm --filter @ai-adolescent-mental-health/admin-portal dev   # 管理端 → http://localhost:3101
```

## 六、验证

- 后端：访问 `http://localhost:8080` 无报错即启动成功
- 用户端：浏览器打开 `http://localhost:3300`
- 管理端：浏览器打开 `http://localhost:3101`

## 常见问题

| 现象 | 解决 |
| --- | --- |
| 后端启动报 `JWT_SECRET` 相关错误 | `.env` 中 `JWT_SECRET` 必须设置且长度 >= 32 |
| 后端连接数据库失败 | 检查 MySQL 是否运行、`.env` 中数据库连接信息是否正确 |
| Redis 连接超时 | 确认 Redis 已启动：`redis-cli ping` 应返回 `PONG` |
| `pnpm install` 报权限错误 | 不要用 sudo，检查 Node/pnpm 版本是否满足要求 |
| 端口被占用 | 后端 8080、用户端 3300、管理端 3101，冲突则先停占用进程 |
