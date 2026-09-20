# AGENTS.md — `apps/backend`

> 面向 AI 编码助手的后端工作区速查。动手前请同时参阅根 [AGENTS.md](../../AGENTS.md)。
>
> **文档自更新**：当本工作区的脚本 / 依赖 / 业务域 / 约定发生变化，或你从用户对话中沉淀到新约束时，**可直接更新本文件或根 README / AGENTS**。就近修改、先事实后文档、保持锚点准确。详见根 [AGENTS.md](../../AGENTS.md) 第「五、文档自更新」节。

## 一、工作区基本事实

- 包名：`@ai-adolescent-mental-health/backend`
- 技术栈：Spring Boot 3.5.9、Java 17、Maven Wrapper、MyBatis-Plus、Spring Security + JWT (jjwt)、Redis、RabbitMQ、WebSocket
- 入口类：`com.xinyuzhilian.aiadolescentmentalhealthsystem.AiAdolescentMentalHealthSystemApplication`
- 默认端口：`8080`（见 `application.yml` 的 `server.port`）
- 激活 profile：`application.yml` 中无默认 profile；本地开发使用 `local`（见 `application-local.yml`），部署环境通过 `SPRING_PROFILES_ACTIVE` 环境变量指定

## 二、包结构（横向分层，模块子包在 `domain/` 下）

**顶层是横向分层，不是业务域。** 领域对象再按模块分到 `domain/` 的子包里。

```
com.xinyuzhilian.aiadolescentmentalhealthsystem
├── controller/            # 控制器；管理端命名 AdminXxxController
├── service/               # 服务接口 IXxxService
│   └── impl/              # 服务实现 XxxServiceImpl
├── mapper/                # MyBatis-Plus Mapper（@MapperScan 已配）
├── domain/                # 领域对象，按模块分子包（见下）
├── config/                # 配置类（SecurityConfig 等）
├── filter/                # 过滤器（含 JwtAuthenticationTokenFilter）
├── exception/             # 全局异常处理（GlobalExceptionHandler）
├── resolver/              # 参数解析器（含 CurrentUserIdResolver）
├── annotation/            # 自定义注解（含 @CurrentUserId）
├── constants/ constant/   # 常量
├── utils/                 # 工具
└── websocket/             # WebSocket（含小爱实时语音代理）
```

`domain/` 下按模块分子包：`book` / `common` / `consultation` / `content` / `pojo` / `search` / `stats` / `xiaoai`。

**新增功能时**：控制器、服务、Mapper 分别落在对应的横向层目录下，领域对象放 `domain/<模块>/`。不要新建平行分层（如 `service2`、`util_v2`）。

## 三、命令

通过 monorepo 根目录执行：

```bash
pnpm dev:backend                                       # mvnw spring-boot:run
pnpm --filter @ai-adolescent-mental-health/backend build   # mvnw clean package -DskipTests
pnpm --filter @ai-adolescent-mental-health/backend test    # mvnw test
pnpm --filter @ai-adolescent-mental-health/backend clean
```

或直接在 `apps/backend/` 下使用 `./mvnw`（Windows 下 `mvnw.cmd`）。

## 四、配置与环境变量

- 主配置：`src/main/resources/application.yml`
- Profile 配置：`application-local.yml`（本地开发，已加入 `.gitignore`）；`application-dev.yml`、`application-test.yml` 等不提交，由 CI/CD 环境变量替代。
- **dotenv 支持**：项目使用 `me.paulschwarz:springboot3-dotenv` 自动加载 `.env` 文件中的变量。类路径或工作目录下的 `.env` 会在 Spring 启动早期被注入为 PropertySource，无需手动 `spring.config.import`。
- **默认值策略**：基础设施变量（`DB_MYSQL_HOST`/`PORT`、`DB_REDIS_HOST`/`PORT`）在 `application.yml` 中使用 `${VAR:default}` 语法提供合理回退值；OSS 等可选服务使用空字符串回退；密钥类变量（`JWT_SECRET`、API keys）在 `application-local.yml` 中有开发用占位值，生产环境必须通过环境变量或 `.env` 注入。
- 关键变量：`DB_MYSQL_*`、`DB_REDIS_*`、`ALIYUN_OSS_*`、`JWT_SECRET`、`DASHSCOPE_API_KEY`、`MAIL_*`。
- MySQL 库名通过 `DB_MYSQL_DATABASE` 配置；schema 变更需同步 `apps/backend/sql/`。

## 五、AI 约束

1. **禁止直接改生产 DDL**：建表与表结构变更必须同步新增 / 修改 `apps/backend/sql/` 下的脚本（`schema_<模块>.sql`、`seed_dict_data.sql` 等），并在 PR 说明迁移顺序。跨工作区的增量与运维脚本才放 `infra/sql/`。
2. **依赖版本对齐**：新增 Spring / Jackson / MyBatis 等框架级依赖时，优先使用 `spring-boot-starter-*` 以便由 BOM 管理版本；不要任意指定与 Spring Boot 3.5.9 不兼容的版本。
3. **日志与异常信息保留中文习惯**：现有日志多为中文描述，新增日志保持一致风格，便于线上排查。
4. **WebSocket 缓冲区**：`spring.websocket.max-*-message-buffer-size` 当前为 5MB，改动需评估对前端实时消息/语音的影响。
5. **认证统一使用 Spring Security + JWT**：鉴权配置在 `config/SecurityConfig.java`，令牌解析在 `filter/JwtAuthenticationTokenFilter.java`，密钥与有效期走 `JwtProperties`（`JWT_SECRET` 环境变量，无公开默认值，缺失即启动失败）。管理端接口用 `@PreAuthorize("hasRole('4')")`。**不要引入 Sa-Token 或其他平行鉴权框架。**
6. **AI 能力在既有实现内扩展**：DashScope 调用目前分布在 `service/impl/AiConsultationServiceImpl`（文字咨询，SSE 流式）、`websocket/OmniRealtimeWebSocketProxy` 与 `websocket/HotSessionManager`（小爱实时语音），请求体 DTO 在 `domain/consultation/dto/DashScopeRequest.java`。新增 AI 能力复用这几处已建立的调用方式，**不要引入平行 HTTP 客户端**。
   - 已知技术债：模型名 `qwen-audio-3.0-realtime-plus` 硬编码在 `OmniRealtimeWebSocketProxy` / `HotSessionManager` / `XiaoaiConstants` 三处；`qwen3-max` 硬编码在 `DashScopeRequest.java` 的字段默认值里。**新代码走配置，不要再加第四处硬编码。**
7. **外部 HTTP 调用**：使用项目既有的客户端封装（如 OSS SDK），不要引入新 HTTP 客户端。

## 六、本地运行最低依赖

- MySQL 8+，库 `xinyuzhilian`，已导入 `apps/backend/sql/` 下的建库脚本
- Redis 6+ 运行于 `localhost:6379`
- RabbitMQ（可选）：如触及消息业务需运行于 `localhost:5672`（`guest:guest`）；本地开发默认禁用 RabbitMQ 自动连接（`application-local.yml` 中 `spring.rabbitmq.listener.simple.auto-startup: false`）

## 七、测试约束

- 测试走 `mvnw test`；不要引入 mock 替代真实数据库除非已有模式允许。
- 新增集成测试保持与既有包结构对齐，测试资源放 `src/test/resources/`。
