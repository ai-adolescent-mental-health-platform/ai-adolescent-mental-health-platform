# 每日签到与情绪预警模块 设计文档

> 状态：待评审
> 日期：2026-09-16
> 范围：`apps/backend`、`apps/web-client`、`apps/admin-portal`
> 本文档是交给实施同学的需求与设计说明，力求自包含：只读本文档即可开工。

---

## 1. 背景与目标

### 1.1 背景

平台面向青少年提供心理健康服务。当前用户的情绪状态只在两个离散场景被采集：量表测评（低频、一次性）和小爱倾听（会话式、不结构化）。缺少**连续的、低门槛的、每日的**情绪数据。

每日签到模块用于填补这个空白：用户每天花十几秒记录当天状态与情绪标签，可选写下日记、困惑或当天的事；系统异步调用大模型分析当天内容，产出状态反馈与结构化预警等级。

### 1.2 目标与优先级

经与产品确认，本模块同时服务三个目标，优先级如下：

1. **风险发现（最高）**：尽早识别情绪恶化与危机信号，并触发人工处置闭环。
2. **陪伴与留存**：帮助用户养成每日自我觉察的习惯。
3. **为咨询师提供纵向数据**：沉淀结构化情绪曲线，供后续接诊的咨询师参考。

### 1.3 非目标（本期明确不做）

| 事项 | 理由 |
| --- | --- |
| 家长端（监护人登录、查看） | 产品规划为后续独立立项 |
| 「监护人-子女」账号绑定表 | 与家长端同期开发；本期的数据模型已为其预留扩展点 |
| 补签 / 修改历史日期 | 本模块定位是每日自我觉察，补签会污染「当日真实状态」的语义 |
| 用户自建情绪标签 | 会污染聚合数据（如「玉玉」与「抑郁」分裂为两个标签），且需要额外审核机制 |
| 量表评估、小爱倾听接入风险判定 | 需要在 `risk_alert` 基础上做多源融合，本期只预留接口 |
| 连续签到积分 / 商城 / 徽章体系 | 本期只展示连续天数，不做物质激励（原因见 3.1） |

---

## 2. 现状与约束（实现前必读）

### 2.1 仓库既有能力（可直接复用）

| 能力 | 位置 |
| --- | --- |
| 统一返回包装 `Result<T>` | `domain/common/Result.java` |
| 分页包装 `PageResult<T>` | `domain/common/PageResult.java`，配 `PageResult.build(IPage)` |
| 当前用户注入 | 方法签名加 `@CurrentUserId Long userId`（`resolver/CurrentUserIdResolver.java`） |
| ORM | MyBatis-Plus 3.5.7，`BaseMapper` + `LambdaQueryWrapper`，`@MapperScan` 已配 |
| 字典加载范式 | `service/impl/DictDataServiceImpl.java`（`ApplicationRunner`，启动时载入 Redis） |
| 定时任务 | `@EnableScheduling` 已在启动类开启；范例见 `websocket/HotSessionManager.heartbeat()` |
| 异步支持 | `@EnableAsync` 已在 `config/SecurityConfig.java` 开启 |
| 站内信 | `sys_message` 表 + `MessageController`（`/user/messages`）+ 前端 `/me/messages` |
| 全局异常 | `exception/GlobalExceptionHandler.java`，业务异常抛 `ServiceException` |
| LLM 接入 | `dashscope.api.key` / `dashscope.api.workspace` 已在 `application.yml` |
| 前端 API 封装 | `packages/api-client/src/index.ts` 的 `createApiClient`，按业务域挂命名空间 |

### 2.2 关键缺口（本次需要新建）

1. **没有非流式的 LLM 调用封装**。现有唯一链路 `AiConsultationServiceImpl.chat()` 是裸 `HttpURLConnection` + 手写 SSE 行解析，走通义千问 OpenAI 兼容端点 `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`。需要新写一个一次性读完整 body 的客户端。
2. **没有可用的异步执行设施**。`@EnableAsync` 虽已开启，但**没有配置任何 `ThreadPoolTaskExecutor`**，默认走 `SimpleAsyncTaskExecutor`（每次新建线程）。全仓 `CompletableFuture` 零使用。
3. **现有 AI 并发用的静态池不可复用**。`AiConsultationServiceImpl` 里的 `Executors.newCachedThreadPool()` 是无界、无拒绝策略、无关闭钩子的静态池，接入分析任务会拖垮现有 AI 对话。
4. **没有任务状态机表**，也没有失败重试的既有范式。
5. **模型名硬编码重复，且既有统计漏了一处**。已知重复的是 `qwen-audio-3.0-realtime-plus`（`OmniRealtimeWebSocketProxy`、`HotSessionManager`、`XiaoaiConstants` 三处），但还有**第四处不在原统计内**：文字咨询模型 `qwen3-max` 硬编码在 `domain/consultation/dto/DashScopeRequest.java:9` 的字段默认值里，而 `application.yml` 的 `dashscope` 段（L54-59）目前只有 `key` 与 `workspace`，**没有任何 model 配置**。本模块复用文字咨询模型，因此这处必须先软编码，见 6.3。
6. **数据库里没有任何签到/心情/情绪/日记/风险相关的表**。本模块是全新领域，无历史兼容负担。

### 2.3 必须遵守的仓库约定

摘自根 `CLAUDE.md` 与各 app 的 `AGENTS.md`，违反会导致 CI 或评审不通过：

- **不改 `pnpm-lock.yaml`**，本模块不需要任何新依赖（前后端均无）。
- **建库 SQL 放 `apps/backend/sql/`**，命名沿用 `schema_<模块>.sql` 的既有形式。
- **不跨 app 复制源文件**。
- **文档不写 emoji**。
- 工作区依赖放对应子包，不塞根 `package.json`。
- 敏感项走 `${ENV_VAR}`，不提交真实密钥。
- 前端**禁止**在组件内直接 `fetch` / `axios.create`，一律走 `api.*`。
- 前端**不做权限闭合**，敏感数据可见性由后端控制。
- 三种实时通道（`streamAiChat` / `lib/sse.ts` / WebSocket `/ws/omni-realtime`）不得混用。

> 注：`apps/backend/AGENTS.md` 中「认证统一使用 Sa-Token」「按业务域划分包结构」两处描述与实际代码不符——实际使用 **Spring Security + JWT**，且现有包结构是**横向分层**（`controller` / `service` / `mapper` / `domain/pojo`）。本模块遵循**实际代码**的组织方式。

---

## 3. 核心设计原则

### 3.1 奖励行为，不奖励情绪

这是本模块最重要的一条产品约束，实施时不得违反。

「风险发现」与「陪伴留存」两个目标存在内在冲突：连续签到奖励会诱导用户为了不断签而隐瞒负面情绪。因此：

- 连续签到奖励**只挂钩「今天是否记录」**，绝不挂钩情绪内容。
- **禁止**任何形式的「心情越好、积分越多」。
- 断签**不做惩罚性提示**。禁止出现「你已断签 3 天」这类文案（对抑郁倾向用户构成伤害），改为中性邀请，如「回来啦，随时可以继续」。
- 连续天数的展示在断签后重置，但**不显示「你失去了 X 天」**。

### 3.2 用户看到反馈，平台看到等级

展示给用户的文案与平台侧的结构化判定**必须分离存放**：

- 用户看到的是共情反馈与具体建议，**不出现「预警等级：2」这类评级表达**。
- 平台与家长侧使用结构化的 `risk_level` 与 `risk_reason`。

理由：把「危机」标签直接展示给青少年，可能造成惊扰、标签化，甚至强化绝望感。

### 3.3 失败不得退化为「安全」

分析失败时 `risk_level` **必须保持 `NULL`**，**绝不允许兜底为 0（平稳）**。

把「分析没跑成功」当成「这个人没事」，是本类系统最危险的失效模式——它会静默吃掉真实的危机信号。前端对未完成的分析显示「分析生成中」或「暂不可用」，而不是当作正常状态展示。

---

## 4. 数据模型

### 4.1 表清单

建库 SQL 写入新文件 `apps/backend/sql/schema_checkin.sql`。

| 表名 | 分组 | 用途 |
| --- | --- | --- |
| `checkin_mood_tag` | 签到域 | 情绪标签字典，平台预置 |
| `user_checkin` | 签到域 | 每日签到记录，一人一天一条 |
| `user_checkin_mood_tag` | 签到域 | 签到与标签的关联 |
| `checkin_analysis` | 签到域 | AI 分析任务状态与分析结果 |
| `risk_alert` | 预警域 | **通用**风险预警记录，多源共用 |

### 4.2 DDL

```sql
-- 表名统一带 xinyuzhilian. 前缀，与 schema.sql 保持一致（不写 USE 语句）

-- 情绪标签字典表
-- 仿 consultation_field 的字典表范式：name/code/icon/sort_order/status
create table if not exists xinyuzhilian.checkin_mood_tag
(
    id          int auto_increment comment '主键ID' primary key,
    name        varchar(20)  not null comment '标签名称',
    code        varchar(30)  not null comment '标签代码',
    icon        varchar(100) null comment 'lucide图标名(前端静态映射,不存emoji)',
    tone        varchar(20)  null comment 'pouf色调(pink/purple/blue/mint/yellow/orange)',
    polarity    tinyint      not null default 0 comment '情绪极性(-2-很负面,-1-负面,0-中性,1-正面,2-很正面)',
    status      tinyint          default 1 null comment '状态(0-禁用,1-启用)',
    sort_order  int              default 0 null comment '排序',
    create_time datetime         default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_code unique (code)
) comment '签到情绪标签表';

-- 每日签到记录表
create table if not exists xinyuzhilian.user_checkin
(
    id             bigint auto_increment comment '主键ID' primary key,
    user_id        bigint       not null comment '用户ID',
    checkin_date   date         not null comment '签到日期',
    mood_polarity  tinyint      null comment '当日情绪极性(所选标签polarity均值,写入时冗余)',
    diary_content  text         null comment '日记/困惑/难处/开心事原文(可空,允许跳过)',
    content_length int          default 0 null comment '正文字数(冗余,列表页免count)',
    create_time    datetime     default CURRENT_TIMESTAMP null comment '创建时间',
    update_time    datetime     default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP null comment '更新时间',
    constraint uk_user_checkin_date unique (user_id, checkin_date)
) comment '每日签到记录表';

create index idx_checkin_date on xinyuzhilian.user_checkin (checkin_date);

-- 签到-情绪标签关联表
create table if not exists xinyuzhilian.user_checkin_mood_tag
(
    id          bigint auto_increment comment '主键ID' primary key,
    checkin_id  bigint not null comment '签到记录ID',
    tag_id      int    not null comment '情绪标签ID',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_checkin_tag unique (checkin_id, tag_id)
) comment '签到-情绪标签关联表';

create index idx_tag_id on xinyuzhilian.user_checkin_mood_tag (tag_id);

-- 签到AI分析结果表(任务状态机与结果合并)
create table if not exists xinyuzhilian.checkin_analysis
(
    id                bigint auto_increment comment '主键ID' primary key,
    checkin_id        bigint       not null comment '签到记录ID',
    user_id           bigint       not null comment '用户ID(冗余,供家长端与运营按用户查询)',
    status            tinyint      not null default 0 comment '状态(0-待处理,1-处理中,2-成功,3-失败)',
    risk_level        tinyint      null comment '预警等级(0-平稳,1-关注,2-危机);失败时为NULL,禁止默认0',
    user_feedback     text         null comment '面向用户的共情反馈全文',
    suggestion        varchar(500) null comment '面向用户的建议',
    risk_reason       varchar(500) null comment '判定理由(平台侧专用,不向用户展示)',
    model             varchar(50)  null comment '实际使用的模型名',
    prompt_tokens     int          default 0 null comment '输入token数',
    completion_tokens int          default 0 null comment '输出token数',
    retry_count       int          default 0 null comment '已重试次数',
    error_msg         varchar(500) null comment '失败原因(截断存储)',
    started_at        datetime     null comment '开始处理时间',
    finished_at       datetime     null comment '处理完成时间',
    create_time       datetime     default CURRENT_TIMESTAMP null comment '创建时间',
    update_time       datetime     default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP null comment '更新时间',
    constraint uk_checkin_id unique (checkin_id)
) comment '签到AI分析结果表';

create index idx_user_create on xinyuzhilian.checkin_analysis (user_id, create_time);
create index idx_status_create on xinyuzhilian.checkin_analysis (status, create_time);

-- 风险预警记录表(通用,多源共用)
create table if not exists xinyuzhilian.risk_alert
(
    id           bigint auto_increment comment '主键ID' primary key,
    user_id      bigint       not null comment '用户ID',
    source_type  varchar(20)  not null comment '来源类型(CHECKIN-签到,ASSESSMENT-量表,XIAOAI-小爱倾听)',
    source_id    bigint       not null comment '来源记录ID',
    level        tinyint      not null comment '预警等级(2-危机,当前仅危机级入表,保留该列以兼容后续多源刻度)',
    trigger_type varchar(20)  not null default 'MODEL' comment '触发方式(MODEL-模型判定,KEYWORD-关键词兜底,MANUAL-人工)',
    reason       varchar(500) null comment '判定理由(平台侧)',
    evidence     varchar(500) null comment '触发该预警的原文摘录(供平台与家长端展示)',
    status       tinyint      default 0 null comment '处置状态(0-待处置,1-处置中,2-已处置,3-已忽略)',
    handler_id   bigint       null comment '处置人ID',
    handle_note  varchar(500) null comment '处置备注',
    handled_at   datetime     null comment '处置时间',
    create_time  datetime     default CURRENT_TIMESTAMP null comment '创建时间',
    update_time  datetime     default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP null comment '更新时间',
    constraint uk_source unique (source_type, source_id)
) comment '风险预警记录表';

create index idx_user_level on xinyuzhilian.risk_alert (user_id, level);
create index idx_status_create on xinyuzhilian.risk_alert (status, create_time);
```

### 4.3 设计决策说明

**为什么标签用关联表而不是 JSON 列。**
仓库里确有 JSON 列先例（`assessment_record.answers_json`），但趋势分析要回答的是「孤独标签与高风险的相关性」「近 30 天焦虑出现频次」这类问题。JSON 列做这个需要全表扫加应用层解析，关联表一次 JOIN 即可，且 `idx_tag_id` 让反查成立。

**为什么 `user_checkin.mood_polarity` 要冗余。**
两个原因：(1) 趋势图查询免去 JOIN 与聚合；(2) **历史准确性**——若半年后运营把某标签的 `polarity` 从 -2 改为 -1，冗余值能保住当时的历史判定不被追溯篡改。这是有意为之的反范式。

**为什么 `checkin_analysis` 把任务状态与分析结果合并。**
两者严格一对一，拆成 `task` + `result` 只会让最高频的「查询我的分析」多一次 JOIN；执行元数据（token 数、重试次数）体积很小，保留无妨。`uk_checkin_id` 保证一个签到只有一条分析，重跑是覆盖而非追加。

**为什么 `risk_alert` 独立于签到表。**
这是本模块唯一为未来留的抽象，必须保留。收益：
- 家长端开工时，查「某孩子近 90 天的预警与趋势」只需一条 SQL，不需要动签到表。
- 量表评估接入时（`assessment_record` 目前只有 `result_score`，无结构化等级），直接往 `risk_alert` 写一条即可。
- 运营的「待处置列表」天然成立——签到流水账里放不下处置状态。

**`uk_source(source_type, source_id)` 的作用。**
保证分析重跑不会重复产生预警。这是幂等性的关键约束，不要去掉。

### 4.4 标签种子数据

写入 `apps/backend/sql/seed_dict_data.sql`（追加，沿用其 `ON DUPLICATE KEY UPDATE` 幂等写法），并同步加入 `DictDataServiceImpl` 的加载列表。

| name | code | icon(lucide) | tone | polarity |
| --- | --- | --- | --- | --- |
| 开心 | HAPPY | smile | yellow | 2 |
| 有动力 | MOTIVATED | zap | mint | 2 |
| 平静 | CALM | moon | mint | 1 |
| 期待 | LOOKING_FORWARD | sparkles | blue | 1 |
| 一般 | NEUTRAL | meh | purple | 0 |
| 疲惫 | TIRED | battery-low | purple | -1 |
| 焦虑 | ANXIOUS | wind | orange | -1 |
| 烦躁 | IRRITABLE | flame | orange | -1 |
| 低落 | DOWN | cloud-rain | blue | -2 |
| 孤独 | LONELY | user-round | purple | -2 |

> `tone` 只允许取 pouf 六色之一。前端必须**静态映射**到类名，禁止 `bg-${tone}` 动态拼接（Tailwind 不会生成该类）。

---

## 5. 风险分级与处置

### 5.1 分级定义与动作映射

| 等级 | 含义 | 用户侧展示 | 平台侧 | 家长侧（本期不做，仅预留） |
| --- | --- | --- | --- | --- |
| 0 平稳 | 无明显风险信号 | 温和反馈 + 小建议 | 仅统计 | 趋势 |
| 1 关注 | 持续低落或焦虑，未达危机 | 共情反馈 + 自助建议 | 仅落 `checkin_analysis.risk_level`，不打扰 | 趋势 |
| 2 危机 | 明显痛苦、建议求助，或出现自伤、轻生等信号 | 共情 + **必须包含求助渠道** | 落 `risk_alert`（待处置），自动触达管理员，留处置记录 | **展示 `evidence` 原文片段** |

**刻度定稿：三级 `0-平稳 / 1-关注 / 2-危机`（已评审确认）。** 原四级设计中的「预警」级并入「危机」，理由：「明显痛苦、建议求助」与「自伤、轻生」在处置动作上必须同样即时，中间再加一级只会让待处置队列产生优先级之争而无实际动作差异。

**该合并的代价已知并接受**：等级 2 会同时容纳强度不同的两类信号，运营需要靠 `reason` 与 `evidence` 自行区分轻重。**因此等级 2 的 `reason` 与 `evidence` 必须写足，不能只给一句概括**——这是补偿合并损失的唯一手段。

**注意「关注」级不写入 `risk_alert`。** 等级 1 只留在 `checkin_analysis.risk_level`，原因见 5.3。趋势分析走 `checkin_analysis`（已有 `idx_user_create` 索引），不受影响。

### 5.2 判定流程：模型为主 + 关键词硬兜底

```
签到正文
  ├─ 关键词规则扫描（确定性）
  │    └─ 命中 → 无论模型判定为什么，强制提升到 level = 2（危机）
  │         并置 trigger_type = 'KEYWORD'，进入人工复核队列
  └─ LLM 判定
       └─ 输出 risk_level + risk_reason + evidence
最终等级 = max(模型等级, 关键词等级)
```

关键词兜底命中时，`risk_alert.evidence` 填写**命中的那句话**（而非整篇正文），`reason` 写明命中的词。这样运营与家长看到的是最小必要片段。

**为什么要加确定性兜底。** 仅靠 LLM 单点判定会漏判，而漏判在危机场景下代价不可逆。这里**有意接受假阳性**：把「我看了部电影，主角自杀了」误判为需复核，代价是运营多看一眼；漏掉真实危机信号，代价不可逆。

**词表放配置文件**（如 `application.yml` 的 `checkin.risk.keywords`），不要硬编码在 Java 里——词表需要由心理专业同事持续迭代。

### 5.3 处置闭环

`risk_level = 2`（危机）时写入 `risk_alert`（`status = 0` 待处置）。

**等级 1（关注）不写入 `risk_alert`。** `risk_alert` 的语义是「待处置队列」，不是全量评分流水；把关注级也塞进去，队列就失去「这里每一条都需要动作」的含义，运营会开始按数字挑着看，这正是队列失效的开始。关注级的历史留在 `checkin_analysis.risk_level`（已有 `idx_user_create`），趋势与统计查那张表即可。

**处置动作为必需，不是可选。** 如果只上后端与用户端、不做运营后台，结果是系统能发现危机但没有任何人被告知、没有任何人处理。**这比不做更糟**——你获得了「知道有人在危机中」的信息却没有任何行动，只是把责任显性化了。

因此分期上强制要求：**后端与运营后台预警列表同期上线**，用户端页面可与之并行。运营后台可以极简（一个列表 + 「标记已处置」+「标记已忽略」+ 备注），但必须存在。

### 5.4 用户侧文案的硬性约束

**等级 2 的文案生成规则（已与产品确认的折中方案）：**

- 允许 LLM 自由生成措辞，保留个性化。
- system prompt **硬性要求必须包含求助渠道**。
- 出参后做**存在性校验**：若生成内容中不含求助渠道，则追加一段固定热线文案。
- 所有等级 2 的生成结果**落库并进入运营复核队列**，供心理专业同事抽查，据此迭代 prompt。

同时需要产品与心理专业同事补充：**求助渠道的具体号码与措辞**。在确定之前，等级 2 文案不得上线。

---

## 6. 异步 AI 分析流水线

### 6.1 提交流程

```
POST /checkin
  |
  +-- @Transactional
  |     +-- 写 user_checkin（uk_user_checkin_date 保证幂等）
  |     +-- 写 user_checkin_mood_tag
  |     +-- 若 diary_content 非空 且 今日分析次数未超限
  |           +-- 写 checkin_analysis(status = 0)
  |
  +-- 事务提交后（afterCommit）投递线程池   <-- 关键
  |
  +-- 立即返回 Result.success（不等待 LLM）
```

**`afterCommit` 投递不能省。** 事务未提交时投递，worker 线程读不到那行记录，会直接判失败。实现方式：`TransactionSynchronizationManager.registerSynchronization(...)` 的 `afterCommit` 回调。

**分析对用户是可跳过的。** 只选了标签、没写正文时不创建分析任务，`user_checkin` 正常保存，`checkin_analysis` 无记录。

### 6.2 线程池

新增 `config/CheckinAnalysisConfig.java`，定义名为 `checkinAnalysisExecutor` 的 `ThreadPoolTaskExecutor`。

**池参数走配置，不硬编码**（`application.yml` 的 `checkin.analysis.executor.*`）。这几个值上线后要按实测负载调，改一次代码发一次版不合理：

| 参数 | 配置键 | 默认值 | 说明 |
| --- | --- | --- | --- |
| corePoolSize | `checkin.analysis.executor.core-pool-size` | 2 | |
| maxPoolSize | `checkin.analysis.executor.max-pool-size` | 4 | |
| queueCapacity | `checkin.analysis.executor.queue-capacity` | 200 | |
| rejectedExecutionHandler | 不配置 | `AbortPolicy` | 见下方说明。这是**行为约束不是调优参数**，写死不外露 |
| threadNamePrefix | 不配置 | `checkin-analysis-` | 同上 |

**为什么是 `AbortPolicy` 而不是 `CallerRunsPolicy`。** `CallerRuns` 会让 HTTP 请求线程去跑 LLM，用户提交签到要等几十秒，直接违背「异步」的初衷。`AbortPolicy` 抛出 `RejectedExecutionException` 后，任务**在数据库里仍是 `PENDING`**，由 6.4 的扫尾任务重新投递——用户立刻拿到响应，任务也不丢。

**明确禁止**复用 `AiConsultationServiceImpl` 里的静态 `Executors.newCachedThreadPool()`。

### 6.3 LLM 客户端

新增非流式客户端（仓库当前没有）。实现要点：

- 复用 `AiConsultationServiceImpl` 的 `HttpURLConnection` 骨架，走同一个 OpenAI 兼容端点 `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`，把 SSE 逐行解析循环换成**一次性读完整 body**。
- 请求体复用现有 DTO `domain/consultation/dto/DashScopeRequest.java`，显式设置 `stream = false`。

**模型：与文字咨询共用同一个，不新开配置项。** 文字咨询当前用 `qwen3-max`，但这个值硬编码在 `DashScopeRequest.java:9` 的字段默认值里。实施时先把这处硬编码提出来：

```java
// DashScopeRequest.java —— 去掉字段默认值，改由调用方注入
private String model;
```

```yaml
# application.yml，补进现有的 dashscope 段（当前只有 key 与 workspace）
dashscope:
  api:
    chat-model: ${DASHSCOPE_CHAT_MODEL:qwen3-max}
```

`AiConsultationServiceImpl` 与签到分析客户端**都从这一个配置取值**（`@Value("${dashscope.api.chat-model}")`）。这才是「统一复用」的字面含义——一个配置项、一个模型，而不是两个模块各写各的。

- **超时走配置**：`checkin.analysis.connect-timeout` / `checkin.analysis.read-timeout`，默认各 30 秒（不同于现有 SSE 的 180 秒）。

**`enable_thinking` 的取舍。** `qwen3-max` 支持思维链。风险判定**默认开启**——判定质量优先于成本，且 6.5 的每日次数上限已按 token 兜住单用户成本。开关由 `checkin.analysis.enable-thinking` 控制（默认 `true`），便于上线后按实测成本回退。

- **token 计量落库**：读取响应中的 `usage.prompt_tokens` / `usage.completion_tokens` 写入 `checkin_analysis`，供运营查看日消耗。**开启思维链后这一步更重要**——思维链的 token 计入 `completion_tokens`，不看这两个字段就不知道成本花在哪。

**输出格式要求。** 需要模型返回严格的 JSON，字段至少包含 `risk_level`、`risk_reason`、`evidence`、`user_feedback`、`suggestion`。

> 待实测确认：通义千问兼容模式是否支持 `response_format: {"type": "json_object"}`。若支持则优先使用；若不支持，退化为 prompt 强约束 + 容错解析（需剥离可能的 markdown 代码围栏）。两种实现的解析层应统一封装，便于切换。

### 6.4 定时扫尾任务

`@Scheduled(fixedDelay = 300000)`（5 分钟）。这是流水线的可靠性骨干，覆盖三类丢单。

| 捞取条件 | 处理 |
| --- | --- |
| `status = 0` 且 `create_time` 早于 2 分钟前 | 投递线程池（覆盖进程重启、线程池拒绝两种丢单） |
| `status = 3` 且 `retry_count < 3` 且 `update_time` 早于 10 分钟前 | `retry_count++` 后重投（覆盖 API 瞬时故障） |
| `status = 1` 且 `started_at` 早于 10 分钟前 | 判为僵死，置为 `status = 3` 交给上一条重试 |

**多实例部署的幂等性。** 若后端部署多个实例，扫尾任务会并发执行。必须用**条件更新**抢占，而不是「先查再改」：

```sql
-- 抢占式更新，仅当状态仍为待处理时才生效
UPDATE checkin_analysis
SET status = 1, started_at = NOW(), update_time = NOW()
WHERE id = #{id} AND status = 0;
-- affectedRows = 0 表示已被其他实例抢走，本次跳过
```

线程池投递同理，worker 取到任务后先做抢占式更新，`affectedRows = 0` 则直接返回。

### 6.5 成本控制

同一天重复修改日记会触发重复分析，用配置 `checkin.analysis.max-per-day`（默认 3）兜住（含首次）。超限后只保存内容、不重复分析，前端提示「今日分析次数已用完，内容已保存」。

**计数口径：按「实际发起的分析次数」计，不是「提交次数」。** 只选标签、不写正文的提交不创建分析任务（见 6.1），因此**不计数**。这条必须写进实现——若按提交次数计，用户早上只选标签、中午回来补写日记时，会用自己早上的空提交扣掉额度，直接违背 3.1「不惩罚」的原则。

### 6.6 失败处理与安全约束

- 解析失败或调用失败：`status = 3`，`error_msg` 截断存储（参考 `XiaoaiRecordServiceImpl.truncateEndReason` 的防御写法，避免 `MysqlDataTruncation`）。
- **`risk_level` 保持 `NULL`，绝不默认 0。** 见 3.3。
- 重试 3 次仍失败：不再重试，保留 `status = 3` 供运营排查。

---

## 7. 接口契约

统一返回 `Result<T>`，分页返回 `Result<PageResult<T>>`。鉴权参数用 `@CurrentUserId Long userId`。

### 7.1 用户端 API

新增 `controller/CheckinController.java`，`@RequestMapping("/checkin")`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/checkin/today` | 今日签到状态：是否已签、已选标签、正文、分析状态与结果 |
| POST | `/checkin` | 提交签到。重复提交返回已有记录而非报错 |
| PUT | `/checkin/{id}` | 修改今日内容，触发重新分析（受每日次数上限约束，且校验记录属于当前用户） |
| GET | `/checkin/mood-tags` | 情绪标签列表（仅 `status = 1`，按 `sort_order`） |
| GET | `/checkin/analysis/{checkinId}` | 分析结果（前端轮询用），校验记录归属 |
| GET | `/checkin/history` | 分页历史，含分析摘要 |
| GET | `/checkin/stats` | 连续签到天数、本月签到数、近 30 天 `mood_polarity` 趋势数组 |

**`/checkin/stats` 的连续天数算法。** 取该用户 `checkin_date` 倒序，从今天（或昨天，若今天尚未签到）起逐日回溯，遇到断点停止。注意跨月、跨年边界。

**归属校验。** `PUT /checkin/{id}` 与 `/checkin/analysis/{checkinId}` 必须校验记录的 `user_id` 等于当前用户，否则抛 `ServiceException`。参照 `AssessmentController.recordDetail` 的既有写法。

### 7.2 管理端 API

新增 `controller/AdminCheckinController.java`，`@RequestMapping("/admin/checkin")`，需管理员角色。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/checkin/alerts` | 分页查询预警列表，支持按 `level` / `status` / 时间范围筛选 |
| GET | `/admin/checkin/alerts/{id}` | 预警详情（含 `evidence`） |
| PUT | `/admin/checkin/alerts/{id}/handle` | 处置：置 `status` / `handler_id` / `handle_note` / `handled_at` |
| GET | `/admin/checkin/analysis/crisis-review` | 等级 2 文案复核队列（5.4 节的人工抽查列表） |
| GET | `/admin/checkin/stats/daily` | 每日签到量、分析量、token 消耗、各等级分布 |

---

## 8. 前端设计（`apps/web-client`）

### 8.1 文件清单

| 文件 | 操作 |
| --- | --- |
| `src/app/(main)/home/page.tsx` | **改**：加签到入口（理由见 8.2 末段） |
| `src/components/home/home-page.tsx` | **改**：加签到卡片（三态，见 8.2） |
| `src/components/checkin/checkin-dialog.tsx` | 新建，签到弹窗（选标签 + 可选日记 + 提交），首页与 `/me/checkin` 共用 |
| `src/app/(main)/me/checkin/page.tsx` | 新建，5 行 wrapper，仅渲染 `<CheckinPage />` |
| `src/components/me/checkin-page.tsx` | 新建，主体组件 |
| `src/components/me/me-layout.tsx` | 改：`MENU_ITEMS` 加 `{ href: "/me/checkin", icon: CalendarCheck, label: "我的签到" }` |
| `src/components/me/me-page.tsx` | 改：快捷卡 grid 从 `lg:grid-cols-5` 调整以容纳新卡片 |
| `packages/api-client/src/index.ts` | 改：`createApiClient` 加 `checkin` 命名空间 |

### 8.2 页面结构

**`CheckinPage`（`/me/checkin`）** —— 页面主体是**回顾**场景：

- 顶部：连续签到天数、本月签到数
- 中部：当日/选中日的详情卡片（标签、正文、AI 反馈）
- 底部：历史列表或日历视图 + 分页

参照模板：多视图切换抄 `components/me/favorites-page.tsx`（`Tabs` 结构），列表 + 分页 + 详情弹窗抄 `components/me/assessments-page.tsx`。加载态统一用 `Skeleton`，空态用 `py-20 text-center text-muted`。

**签到必须在首页完成，且是就地签完，不是跳转。** 签到是每日高频动作，藏在 `/me/checkin` 里等于没有——用户不会每天主动点进个人中心。首页卡片点击直接展开签到弹窗（选标签 + 可选日记 + 提交），**不跳页**。个人中心的「我的签到」只在回顾历史时用，不是发起签到的地方。

**首页卡片是三态，不是两态。** 因为签到不强制写日记、允许稍后补写（见 6.1 与 6.5），「已签到」本身不是一个单态：

| 状态 | 触发条件 | 卡片文案与主行动 |
| --- | --- | --- |
| 未签到 | 今日无 `user_checkin` 记录 | 邀请签到，点击展开弹窗 |
| 已签到待补日记 | 有记录，但 `diary_content` 为空（未触发过分析） | 显示「今天已签到」，主行动改为**「补写今天的事」**，不给二次签到入口 |
| 已完成 | 有记录且已触发分析 | 显示已签到与连续天数，可点击查看今日反馈；主行动降级为「修改今天的内容」 |

**中间态必须独立存在。** 若把它并进「已签到」，用户中午回来想补写时会以为今天的额度已用完，而实际上他一次分析都还没用掉——这会直接劝退用户写日记，而这个模块的全部价值都建立在日记内容上。

### 8.3 API 客户端

在 `packages/api-client/src/index.ts` 的 `createApiClient` 返回对象中新增命名空间：

```ts
checkin: {
  today:      () => http.get<CheckinToday>("/checkin/today"),
  submit:     (body: CheckinSubmitBody) => http.post<CheckinToday>("/checkin", body),
  update:     (id: number, body: CheckinSubmitBody) => http.put<CheckinToday>(`/checkin/${id}`, body),
  moodTags:   () => http.get<MoodTag[]>("/checkin/mood-tags"),
  analysis:   (checkinId: number) => http.get<CheckinAnalysis>(`/checkin/analysis/${checkinId}`),
  history:    (params?: { page?: number; size?: number }) => /* 分页封装 */,
  stats:      () => http.get<CheckinStats>("/checkin/stats"),
},
```

类型定义放 `packages/domain/src/index.ts`，经 `lib/types.ts` 重导出。**组件里只调 `api.checkin.*`，禁止直接 `fetch`。**

### 8.4 异步结果的展示

分析是异步的，提交后前端需要拿到结果：

- **轮询**：提交成功后 `setInterval` 轮询 `/checkin/analysis/{checkinId}`，间隔 3 秒，最多 6 次。轮询期间显示「分析生成中」的 `Skeleton` 态。**不引入新库**，用原生 `setInterval` + 清理函数。
- **站内信兜底**：分析完成时后端写一条 `sys_message`（`type = 4`，需在 `MessageController` 与 `/me/messages` 的类型映射中同步扩展），覆盖用户提前离开页面的情况。

**不要引入 WebSocket 或 SSE。** 仓库的三条实时通道已有明确分工，签到分析用轮询加站内信足够，不要增加第四条。

### 8.5 pouf 设计约束

摘自 `apps/web-client/design.md` 与 `AGENTS.md`，实施时必须遵守：

- **配色**只用 `bg` / `ink` / `muted` / `surface` 与六粉彩（`pink` / `purple` / `blue` / `mint` / `yellow` / `orange`）。写法如 `text-ink`、`text-muted/70`、`bg-purple/15`、`border-purple/10`。
- **粉彩填充上禁止白字**，一律 `text-[var(--on-accent)]`。白字压粉彩实测对比度 1.25:1 至 1.99:1，不达 WCAG AA。
- **`tone` 必须静态映射**到类名。禁止 `bg-${tone}` 这类动态拼接，Tailwind 不会生成对应类。参考 `components/pouf/Progress.tsx` 的 `toneBg` 写法。
- **黏土深度只用 `cushion-*`**（`cushion-card` / `cushion-control` / `cushion-field` 等），**不要用 Tailwind `shadow-*` 拼装**——`shadow-*` 经 `--tw-shadow` 变量栈合成，会破坏快照基准。
- 圆角用 `rounded-card` / `rounded-control` / `rounded-blob` / `rounded-pill`。
- 组件从 `@/components/pouf/` 取（`Button` / `Card` / `Dialog` / `Input` / `Textarea` / `Tabs` / `Skeleton` / `Progress` / `Badge` / `Avatar`）。注意 `components/ui/` **已删除，不存在**。
- 图标用 `lucide-react`。
- 不引入 Framer Motion。不要写 `cosmic-*` 类。不要往 `globals.css` 加新 token。
- 组件默认 `"use client"`；`page.tsx` 只做薄包装，逻辑全放组件。
- 提交前跑 `pnpm typecheck`。

---

## 9. 运营后台（`apps/admin-portal`）

**必须与后端同期上线**（理由见 5.3）。最小可用范围：

| 页面 | 路由 | 功能 | 期次 |
| --- | --- | --- | --- |
| 预警列表 | `/admin/alerts` | 分页表格，筛选 `status` / `source_type` / 时间范围；`status = 0`（待处置）置顶并有明显标识 | 第一期 |
| 预警详情 | `/admin/alerts/{id}` | 只读展示 `level` / `trigger_type` / `reason` / `evidence` / 关联签到正文 | 第一期 |
| 处置操作 | 同上 | 「标记已处置」「标记已忽略」+ 备注输入 | 第二期 |
| 等级 2 复核队列 | 待定 | 列出所有等级 2 的生成文案，供心理专业同事抽查 | 第二期 |

**列表没有 `level` 筛选项，这是刻意的。** 5.3 定稿后 `risk_alert` 只接收等级 2（危机），表内等级是常量，做成筛选项就是个死控件。这个列表要区分的是**处置状态**与**来源**（`source_type`：签到 / 量表 / 小爱），不是等级。

**「关注」级不出现在运营后台。** 它是「落库不打扰」，只留在 `checkin_analysis.risk_level`。若后续需要运营侧观察轻度信号趋势，应另开一个基于 `checkin_analysis` 的统计视图，**不要塞进这个队列**——理由见 5.3。

**分期不解除 5.3 的硬约束。** 第一期只有查询，前提是后端 P0-6 尚未上线、`risk_alert` 里还没有数据；处置必须在 P0-6 上线前跟上。若排期上做不到，宁可推迟 P0-6。

**访问控制。** 该页面处理未成年人的危机数据，必须做角色校验（仅超级管理员，`role = 4`），且**建议记录访问日志**。最小实现可先只做角色校验，访问日志作为待办项。

`role = 4` 不是新决定——`apps/admin-portal/src/lib/role-utils.ts` L2-3 已定义 `ROLE_SUPER_ADMIN = 4`，`app/admin/layout.tsx` L8 的 `<AuthGuard allowedRoles={[4]}>` 覆盖 `admin/` 整个子树，后端 `AdminController` 用 `@PreAuthorize("hasRole('4')")` 对齐。落在 `admin/` 下的新页面自动继承守卫，**不需要新增权限设施**。注意前端守卫只是交互层，敏感数据的可见性最终由后端控制——这条是仓库既有约定（见根 `CLAUDE.md`）。

---

## 10. 分期实施计划

| 阶段 | 内容 | 说明 |
| --- | --- | --- |
| P0-1 | 建表 SQL + 标签种子数据 | `schema_checkin.sql` + `seed_dict_data.sql` 追加 |
| P0-2 | 实体 / Mapper / 标签字典加载 | 照 `consultation_field` 范式 |
| P0-3 | 签到 CRUD 接口 + 连续天数统计 | `POST` / `GET /today` / `GET /stats` 等 |
| P0-4 | 非流式 LLM 客户端 + 线程池配置 | 6.3 与 6.2 |
| P0-5 | 分析流水线 + 扫尾任务 | 6.1 至 6.6 |
| P0-6 | 关键词兜底 + 预警落库 | 5.2 与 5.3 |
| **P0-7** | **运营后台预警列表与处置** | **不得晚于 P0-6 上线** |
| P0-8 | 用户端签到页 + 首页入口 | 第 8 节 |
| P1 | 站内信通知、等级 2 复核队列、运营统计 | |
| P2 | 家长端（含监护人绑定表）、量表与签到多源融合 | 独立立项 |

**关键路径约束**：P0-6 一旦上线（开始产生 `risk_alert`），P0-7 必须已经就绪。若资源不足，宁可推迟 P0-6，也不要让预警只落库无人处置。

---

## 11. 测试要求

### 必须有测试的逻辑

| 对象 | 用例要点 |
| --- | --- |
| 连续天数计算 | 跨月、跨年、中间断签、今天未签到、首次签到 |
| `mood_polarity` 计算 | 多标签均值、单标签、无标签 |
| 关键词兜底规则 | 命中词 + 模型判 0 → 最终为 2；未命中 + 模型判 2 → 最终为 2 |
| 等级 1 不写 `risk_alert` | 模型判 1 时 `risk_alert` 无记录，`checkin_analysis.risk_level = 1` |
| **解析失败时 `risk_level` 保持 `NULL`** | **这是安全属性，必须有断言** |
| `POST /checkin` 幂等性 | 同日重复提交返回已有记录，不产生第二条 |
| 分析抢占式更新 | 并发下只有一个 worker 处理成功（`affectedRows` 语义） |
| 归属校验 | 他人 `checkinId` 与 `analysisId` 必须被拒 |
| 扫尾任务 | 三种捞取条件各覆盖一次 |

### 不测的内容

LLM 输出的具体质量。不可测且不稳定，改为把等级 2 的生成结果纳入人工复核队列（5.4）。

### 其它

- 后端跑 `pnpm test:backend`。
- 前端至少跑 `pnpm typecheck`；E2E 覆盖「签到 → 分析生成中 → 分析完成」主流程。
- 覆盖率目标参照仓库既有标准。

---

## 12. 与既有规划文档的关系（重要，实施前必读）

`apps/backend/docs/TECHNICAL_DESIGN.md` 是本仓库既有的**规划**文档（注意：其中的危机相关设计**至今未实现**，见下）。它与本设计有三处交叉，实施前必须理清，否则会读到两份不一致的文档。

### 12.1 `crisis_alert` 与 `risk_alert` 的关系

`TECHNICAL_DESIGN.md` 第 2.1.7 节已规划：

```
crisis_alert: id, user_id, trigger_msg_id, risk_level,
              status (PENDING / PROCESSING / RESOLVED), handler_id, create_time
```

本设计的 `risk_alert` **是它的超集**，对应关系：

| `crisis_alert`（既有规划） | `risk_alert`（本设计） | 差异说明 |
| --- | --- | --- |
| `trigger_msg_id` | `source_type` + `source_id` | 泛化为多源，签到是第一个生产者 |
| `risk_level` | `level` | 同义 |
| `status` 三态 | `status` 四态 | 增加「已忽略」，区分「处理完」与「评估后无需处理」 |
| `handler_id` | `handler_id` | 一致 |
| 无 | `trigger_type` | 新增，区分模型判定与关键词兜底 |
| 无 | `evidence` | 新增，承载家长端分层展示所需的原文片段 |
| 无 | `handle_note` / `handled_at` | 新增，处置留痕 |

**建议**：保留表名 `risk_alert`，因为该表不止存危机（也存「关注」「预警」两级，那不是 crisis）。但**必须在实现时于 `TECHNICAL_DESIGN.md` 对应章节加一行说明**，指明 `crisis_alert` 的设计已被 `risk_alert` 取代，避免后续同学按旧文档建表。

### 12.2 分级刻度不一致

| 来源 | 刻度 |
| --- | --- |
| `TECHNICAL_DESIGN.md` 第 2.1.7 节 | `risk_level`：0-低 / 1-中 / 2-高（三级） |
| 本设计初稿 | 0-平稳 / 1-关注 / 2-预警 / 3-危机（四级） |
| **本设计定稿（已评审确认）** | **0-平稳 / 1-关注 / 2-危机（三级）** |

**定稿采用三级 `0-平稳 / 1-关注 / 2-危机`**：保留「关注」这一级，把初稿的「预警」并入「危机」。理由与代价见 5.1。

需要一并处理的是 `TECHNICAL_DESIGN.md`：其 §2.1.7 的三级命名是 `0-低 / 1-中 / 2-高`，与本定稿**级数一致但语义边界不同**——本定稿的 `1-关注` 对应「落库不打扰」的明确动作，旧刻度没有定义动作映射。两份文档不能各留一套刻度，实施前应把 `TECHNICAL_DESIGN.md` 的对应章节改为指向本文档 5.1。

### 12.3 情绪标签词表的潜在合并

`TECHNICAL_DESIGN.md` 在 `chat_message` 上规划了 `emotion_tag` 字段（悲伤、愤怒等自然语言标签），本设计另建了 `checkin_mood_tag` 字典表。两者当前**互不影响**（各自独立），但如果将来要做「签到与 AI 对话的多源情绪融合」，需要统一词表。届时建议把 `checkin_mood_tag` 提升为全局情绪标签字典，`chat_message.emotion_tag` 改存其 `code`。

**本期不做**，仅记录该收敛方向。

---

## 13. 已知风险与待定项

### 待定项（开工前需确认）

| 项 | 负责方 | 影响 | 状态 |
| --- | --- | --- | --- |
| **分级刻度三级还是四级** | 评审 | 影响 5.1 / 5.3 / 7.2 / 8 | **已定：三级 `0-平稳 / 1-关注 / 2-危机`**，见 5.1 与 12.2 |
| **运营后台的「管理员」角色定义（`role = 3` 还是 `4`）** | 产品 | 影响 9 的权限校验 | **已定：`4`**。`role-utils.ts` L2-3 已定义 `ROLE_SUPER_ADMIN = 4`，与后端 `@PreAuthorize("hasRole('4')")` 一致，无需新增 |
| 等级 2 用户侧求助渠道的具体号码与措辞 | 产品 + 心理专业同事 | **未确定前等级 2 文案不得上线** | 待定 |
| 关键词词表初稿 | 心理专业同事 | 5.2 的兜底规则依赖它 | 待定 |
| 通义千问兼容模式是否支持 `response_format: json_object` | 实施同学实测 | 影响 6.3 的解析层实现 | 待定 |
| `sys_message.type = 4` 的文案与前端类型映射 | 实施同学 | 影响 8.4 | 待定 |

### 已知风险

1. **LLM 判定存在漏判可能。** 已通过关键词硬兜底缓解（5.2），但词表覆盖度决定实际效果，需要持续迭代。
2. **单用户每日分析成本不可精确预估。** 已在 `checkin_analysis` 记录 token 数（6.3），且 6.3 默认开启了思维链，成本会比纯回答高。上线后需观察一周再决定是否下调 `checkin.analysis.max-per-day`（默认 3）或关闭 `checkin.analysis.enable-thinking`。
3. **本期预警数据无家长侧出口。** 这是与产品确认过的分期安排，家长端立项时 `risk_alert` 与 `checkin_analysis` 已按 `user_id` 组织，可直接查询。
4. **`apps/backend/AGENTS.md` 与实际代码有三处描述不符**（Sa-Token、包结构、schema 路径）。本模块按实际代码实现，但建议另行修正该文档，避免后续同学被误导。
5. **运营后台访问未成年危机数据缺少审计日志。** 最小实现先做角色校验，审计日志列为 P1 待办。
6. **前端 `packages/api-client/src/index.ts` 中 `mapAssessmentRisk` 存在缺陷**：它拿 `assessment_template.type` 去匹配 `SLEEP` / `PARENT` / `STRESS` / `MOOD`，而该字段实际取值是 `TRADITIONAL` / `QUICK` / `DYNAMIC`，导致 `riskLevel` 恒为「日常筛查」。与本模块无直接依赖，但同属风险分级语义，建议一并修复。
