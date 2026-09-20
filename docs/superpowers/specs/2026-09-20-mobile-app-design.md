# 手机端 App 设计文档

> 状态：待评审
> 日期：2026-09-20
> 范围：`apps/mobile`（新建）、`packages/api-client`（改造）、`packages/domain`（复用）
> 本文档是交给实施同学的需求与设计说明，力求自包含：只读本文档即可开工。

---

## 1. 背景与目标

平台现有三个端：`apps/backend`（所有端共享的 API）、`apps/web-client`（青少年/家长用户端）、`apps/admin-portal`（运营与咨询师管理端）。用户侧目前只有 Web，缺少手机端的原生使用场景。

本模块新建手机端 App，复用现有的账号体系与后端接口，服务三类核心场景：AI 问答、心理咨询、个人中心。

### 1.1 三个一级菜单

| 菜单 | 内容 |
| --- | --- |
| 问答 | 文字问答，复用现有小艾（AI 咨询） |
| 心理咨询 | 咨询师目录与详情、预约下单、与咨询师图文咨询 |
| 个人中心 | 个人信息与设置、我的消息与互动、我的咨询与订单 |

### 1.2 第一批交付物

**登录、注册、个人中心三块。** 这是本次明确指定的优先级。

### 1.3 非目标（本版明确不做）

| 事项 | 理由 |
| --- | --- |
| 多模态（图片、语音输入） | 需求方本次收窄为纯文字问答。后端 `DashScopeRequest.DashScopeMessage` 只有 `role` + `content`(String)，图片链路需从零新增 |
| 量表评估、内容库 | 三个菜单未覆盖，本版不做 |
| 咨询师入驻申请 | 该流程面向「想成为咨询师的人」，与手机端用户群不重合。已确认移出本版范围 |
| 微信小程序 | **只交付 App 端**。小程序特有的约束（无 `fetch`、录音与推送权限另申请）本模块不处理 |
| 真实支付网关 | 见第 7.4 节。后端无任何支付对接，本版采用**虚拟支付**（下单即标记已支付） |
| 家长端 | `apps/parent-portal` 是独立空壳，与本模块无关 |

---

## 2. 现状与约束（实现前必读）

### 2.1 `apps/mobile` 是一个空壳

当前内容仅有一个 `package.json`：

```json
{
  "name": "@ai-adolescent-mental-health/mobile",
  "version": "0.1.0",
  "private": true
}
```

没有 scripts、没有依赖、没有 `tsconfig.json`、没有配置文件、没有一行代码。这个插槽是在提交 `b39c19e`（工作区收敛 + web-client 主页 pouf 化）时建立的，同一个提交还建立了 `apps/parent-portal`，也是空壳。

**注意：根 `CLAUDE.md` 的端口表里没有这两个 app。** 开工时若需要把它们纳入 `pnpm dev` / Turbo 流水线，需要同时更新该文档。

### 2.2 可直接复用的共享包

| 包 | 内容 | 手机端可用性 |
| --- | --- | --- |
| `packages/domain` | 全部 TS 类型 | **零改动可用**。纯类型、无任何 dependencies |
| `packages/api-client` | `createHttpClient` / `createApiClient` / `ApiClientError` | **需改造**，见 2.3 与第 4 节 |
| `packages/config` | 共享配置 | 需核实内容后决定 |
| `packages/ui` | pouf 组件（React + Tailwind） | **不可用**，uni-app 无法渲染 React 组件 |

### 2.3 `packages/api-client` 依赖 axios 与裸 fetch，不能原样复用

事实依据：

- `packages/api-client/src/index.ts:1` — `import axios, { AxiosError, ... } from "axios"`
- `packages/api-client/src/index.ts:400` — `axios.create({...})`
- `packages/api-client/src/index.ts:780` — 流式问答使用裸 `fetch(...)`
- `packages/api-client/package.json` — dependencies 为 `axios` 与 `@ai-adolescent-mental-health/domain`

axios 默认 adapter 基于 `XMLHttpRequest`，而 uni-app 的运行时不保证提供它——**App 端不是浏览器，`<script>` 里的代码并不跑在与浏览器一致的环境里**。`index.ts:780` 的裸 `fetch` 同理。

**因此该包不能原样复用**，这是第 4 节存在的原因。选择显式适配层，而不是「先试试默认实现能不能跑」，是为了不把整条请求链路押在 uni-app 未文档化的运行时行为上——那种方案在上线前看不出问题，出问题时又极难定位。

### 2.4 错误语义建立在 axios 的错误对象形状上

`ApiClientError` 的分类逻辑在 `packages/api-client/src/index.ts:54-66`，读取 `axiosError.response?.status` 与 `axiosError.response?.data` 来区分 401 / 403 / 网络错误。

**这是本次改造最容易出错的地方**：自定义 adapter 若不构造出 axios 认得的错误形状，会出现「请求能通，但 401 不触发重新登录、网络错误归错分支」——表面正常、出错时才暴露。第 8 节要求对此写测试。

### 2.5 会话存储在 web-client 是本地的

`apps/web-client/src/lib/session.ts:1-2`：

```ts
const TOKEN_KEY = "aiamh.webClient.token";
const USER_KEY = "aiamh.webClient.user";
```

读写走 `localStorage`（同文件 L4-33）。uni-app 没有 `localStorage`，这层**无法复用**，见第 5 节。

### 2.6 三条实时通道不得混用

仓库既有约定：`streamAiChat`(SSE) / `lib/sse.ts`(SSE) / WebSocket `/ws/omni-realtime` 三条通道分工明确。手机端涉及其中两条：

- 菜单 1 的 AI 问答走 `/ai/chat`（对应 `streamAiChat`）
- 菜单 2 的咨询师图文咨询走 `lib/sse.ts` 那一条

**不要为了「统一实现」把两条合成一条**，它们对应的后端链路与消息语义不同。

### 2.7 必须遵守的仓库约定

摘自根 `CLAUDE.md` 与各 app 的 `AGENTS.md`：

- **不改 `pnpm-lock.yaml`**，除非明确要求新增依赖。本模块**必然需要新增依赖**（uni-app 全家桶），届时需单独说明并走一次显式的锁文件更新
- **工作区依赖放对应子包**，不塞根 `package.json`
- **不跨 app 复制源文件**
- **前端禁止在组件内直接 `fetch` / `axios.create`**，一律走统一的 api 层
- **文档不写 emoji**
- 敏感项走 `${ENV_VAR}`，不提交真实密钥

---

## 3. 架构

手机端是**新增的消费端**，不改变 backend / web-client / admin-portal 的任何现有行为。分三层：

| 层 | 内容 | 归属 |
| --- | --- | --- |
| 共享契约层 | `packages/domain`（类型）+ `packages/api-client`（请求语义、错误映射） | 全端共用，**不放 UI** |
| 手机端应用层 | `apps/mobile`：页面、路由、状态、青少年风格 UI | 手机端独占 |
| 平台能力层 | `uni.request` / 存储 / 文件 / 推送 | uni-app 运行时 |

**「共享层不放 UI」是硬原则。** `packages/ui` 里的 pouf 组件是 React + Tailwind，uni-app 渲染不了。青少年风格的组件是一套新建资产，先长在 `apps/mobile` 内部；等设计稳定、且出现第二个消费端需要它时，再考虑提取为共享包。**现在提取是过早抽象。**

---

## 4. 共享层改造

这是本模块**唯一需要改动现有代码**的地方。

### 4.1 `packages/domain`：零改动

纯 TypeScript 类型，dependencies 为空。手机端直接 `import`，不做任何修改。

### 4.2 `packages/api-client`：新增 adapter 注入点

`createHttpClient` 目前已经是 options 对象工厂（`packages/api-client/src/index.ts:399`），因此改造方式是加法而非改法：

1. 给 `createHttpClient` 的 options 增加一个**可选**的 `adapter` 字段，透传给 `axios.create({ adapter })`。
2. **web-client 不传该字段**，axios 使用默认 adapter，行为与今天完全一致，**调用点一行都不用改**。
3. 新增一个面向 uni-app 的导出入口，内置 `uni.request` adapter。

### 4.3 adapter 的正确性要求（关键）

adapter 不是「把请求转发给 `uni.request` 再返回结果」就完成了。它必须：

- **在失败时抛出 axios 认得的错误对象**，且 `error.response.status` 与 `error.response.data` 正确填充，使 `index.ts:54-66` 的分类逻辑能正常工作。
- **保留 `ApiClientError` 的全部分支语义**：401 触发未授权处理、403 归权限分支、网络失败归网络分支。
- **保持鉴权头注入行为不变**：现有实现注入 `Authorization: Bearer` 与 `token` 双头（`index.ts:411-412`），且 `index.ts:68-80` 的 `unwrap()` 会自动解开后端 `{ code, message, data }` 信封。

第 8 节对此有强制测试要求。

---

## 5. 登录、注册与个人中心（第一批交付）

### 5.1 接口复用

登录、注册、邮箱绑定、找回密码等接口与 web-client **完全同源**，不做任何后端改动。

### 5.2 会话存储必须重写

web-client 的 `lib/session.ts` 基于 `localStorage`，uni-app 中不存在该 API。手机端需要基于 `uni.setStorageSync` / `getStorageSync` 重写同等的四个操作（读取 token、读取 user、保存会话、清除会话）。

**首版取舍：使用 `uni.setStorageSync`，不加密。**

这是一处**已知并接受的取舍**，不是疏忽：手机端持有的是未成年人心理健康数据对应的会话凭据，设备丢失或应用沙箱被突破的后果比浏览器更重。

不做的理由是工程性的：加密存储需要 `plus.storage` 一类 **App 端专有 API**，会把会话层绑死在单一平台，H5 预览与将来可能的其他端都无法复用；首版强行引入会同时增加实现复杂度与不稳定性。

**要求：在代码注释中标注该取舍，并在安全评审时作为待办项重新评估。**

### 5.3 路由守卫要重写，规则必须一致

web-client 用 `<AuthGuard>` 组件包裹 layout（例如 `apps/admin-portal/src/app/admin/layout.tsx:8` 的 `allowedRoles={[4]}`）。uni-app 没有等价的 layout 概念，需要在页面 `onLoad` 或全局路由拦截中实现。

实现方式变了，但**规则必须与 web-client 一致**：未登录跳登录页、登录成功后回跳原目标页。

### 5.4 个人中心三块

| 块 | 内容 | 对应 web-client 资产 |
| --- | --- | --- |
| 个人信息与设置 | 资料查看与编辑（昵称/头像/签名/生日/性别）、账号安全、隐私与协议 | `components/me/info-page.tsx`、`privacy-page.tsx` |
| 我的消息与互动 | 站内信、收藏、点赞、关注、我的文章与发布、反馈 | `messages-page.tsx`、`favorites-page.tsx`、`likes-page.tsx`、`follow-page.tsx`、`articles-page.tsx`、`publish-page.tsx`、`feedback-page.tsx` |
| 我的咨询与订单 | 预约记录、咨询订单 | `orders-page.tsx`、`psychology-page.tsx` |

**本版不做**：量表测评记录（`assessments-page.tsx`）与每日签到。签到模块目前 0% 开工（后端任务见 issue #20 起），手机端接入要等后端就绪。

参考 `apps/web-client/src/components/me/` 目录下的既有实现来对齐字段与交互，但**不要复制源文件**（仓库约定），按手机端的技术栈重写。

---

## 6. 菜单 1：问答

复用小艾的现有接口：`/ai/sessions`、`/ai/session`、`/ai/session/{id}/messages`、`/ai/chat`（SSE 流式，模型 `qwen3-max`）。

### 6.1 流式接收

**已定：本模块只交付 App 端，不做小程序。** 这消除了小程序特有的约束（无 `fetch`、只能靠 `uni.request({ enableChunked: true })` + `onChunkReceived`），但**没有消除风险**——流式接收仍是菜单 1 里最不确定的一环。

web-client 的流式实现是裸 `fetch` 读流（`packages/api-client/src/index.ts:780`）。App 端的候选路径：

| 路径 | 说明 |
| --- | --- |
| `renderjs` 内使用浏览器原生 `fetch` | uni-app 的 App 端本质是 webview，`renderjs` 可运行真正的浏览器 JS，理论上能直接复用 web-client 的读流代码。**首选验证方向** |
| `uni.request` 分块接收 | App 端底层是 `plus.net`，默认不提供分块回调，能否拿到增量需要实测 |
| 改用 WebSocket | 仓库已有 WebSocket 通道（`/ws/omni-realtime`），但那条被小爱倾听占用，**不要挪用**；若要新开一条需另行评估 |

**三条都必须在真机上实测。** 在 H5 预览里跑通**不代表 App 端可用**——H5 预览走的是浏览器 `fetch`，与 App 运行时不是同一套。

**处置：**

- **首选**：`renderjs` + 原生 `fetch` 读流，自行解析 SSE 帧。保留流式体验。
- **降级**：退化为非流式——后端增加一个一次性返回完整回复的端点，前端转圈等待。体验明显差于 Web 端，但确定性高。

> 相关：签到模块的 issue #23（P0-4）正在实现一个非流式 LLM 客户端，其**传输层**可以借鉴（同一 OpenAI 兼容端点、同样是 `HttpURLConnection` 一次性读完整 body）。但注意那是为「严格 JSON 输出」设计的场景，prompt 与解析层不同，**不能直接拿来当聊天接口使用**。

### 6.2 会话管理

`/ai/sessions`（列表）、`/ai/session`（新建）、`/ai/session/{id}/messages`（历史）、`/ai/session/{id}`（删除）全部可复用，无后端改动。

---

## 7. 菜单 2：心理咨询

四块，按风险从低到高排列，建议按此顺序实施。

### 7.1 咨询师目录与详情

纯读接口，无支付、无实时通道，风险最低。对应 web-client 的 `components/consultation/hub-page.tsx`、`psychologist-list-page.tsx`、`psychologist-detail-page.tsx`。

### 7.2 咨询师入驻申请（本版不做）

**已确认移出本版范围。** 该流程面向「想成为咨询师的人」，与手机端 App 的用户群不重合；它在 web-client 的 `components/apply/` 下已有完整实现，继续在 Web 端使用即可。

本节保留记录，是为了避免后续同学看到 web-client 有这块就顺手搬进手机端。

### 7.3 与咨询师图文咨询

对应 web-client 的 `components/consultation/chat-page.tsx`，走 SSE。

**这是第二条实时通道**，与 6.1 的 `/ai/chat` 不是同一条链路，消息语义与后端处理均不同。参见 2.6 的约定，不要合并实现。

### 7.4 预约下单（虚拟支付）

**后端现状（已核实）：**

- `apps/backend/pom.xml` 中**没有任何支付网关依赖**（无微信支付、支付宝相关 SDK）
- Java 源码中**没有统一下单、没有支付回调、没有验签逻辑**
- 但数据模型是齐的：`apps/backend/sql/schema.sql:571-582` 的 `psychologist_appointment` 表含 `order_no`、`fee`、`pay_status`（0-未支付 / 1-已支付 / 2-已退款）、`pay_time`

**本版决定：采用虚拟支付。** 用户提交预约后订单直接置为已支付（`pay_status = 1`，`pay_time` 取当前时间），不经过任何支付渠道，用户侧不出现「未支付」状态，看到的就是「预约成功」。

这样处理消除了原方案里「App 用户找不到付款入口」的体验断裂。

**约束一：置位动作必须在后端完成，不能由客户端传参决定。**

即使当前是虚拟支付，`pay_status` 也不能接受前端传入。理由与「现在有没有接真支付」无关：

- 客户端可写 = 任何人都能把任意订单改成已支付。今天无害，只因为它本来就全放行；但**这个字段将来是要接真支付的，留一个客户端可写的支付状态字段，等于给未来埋一个必须记得拆掉的雷**。
- 正确做法：由后端配置项（例如 `appointment.virtual-pay`，默认开启）决定下单时是否直接置为已支付。将来接真支付时**只需改后端配置与实现，客户端一行不动**。

**约束二：这是开发期行为，不是可上线的支付方案。**

上线前必须替换为真实支付，或退回到人工审核放行。**若带着虚拟支付上线，等价于所有心理咨询预约免费**——任何未授权用户都可以直接把咨询师的时间占满。这一条需要在文档与代码注释里同时写明。

若后续要做真实支付，那是**独立的后端工程**：商户号申请、后端统一下单接口、支付回调验签、退款流程，加上前端 `uni.requestPayment` 接入。工作量可能超过手机端其余所有模块之和，**建议单独立项，不要塞进本模块的分期**。

---

## 8. 错误处理与测试

### 8.1 错误处理

错误统一走共享的 `ApiClientError`，页面层只做呈现映射，不自行判断 HTTP 状态码。

- 401：清除本地会话 → 跳转登录页
- 403：提示无权限
- 网络错误：提示重试

**规则必须与 web-client 一致**——两端对同一个后端错误的反应应该相同，否则用户在两端的体验会分叉。

### 8.2 adapter 的强制测试

`packages/api-client` 的 uni adapter **必须**有单元测试，mock `uni.request` 的返回值，至少覆盖：

- 成功响应 → 正确解包 `{ code, message, data }` 信封
- 401 响应 → 抛出 `ApiClientError` 且落入未授权分支，`status` 正确
- 403 响应 → 落入权限分支
- 网络失败 → 落入网络错误分支，不误判为 HTTP 错误
- 鉴权头注入 → 请求确实带上了 `Authorization: Bearer` 与 `token` 双头

**这是整套设计里唯一「写错了也看不出来、等线上出事才知道」的地方**，不能省。

### 8.3 页面层测试

- `pnpm typecheck` 必须通过
- 关键流程手测：注册 → 登录 → 个人中心各页 → 退出登录
- 菜单 1 与菜单 2 的实时通道在**目标端**实测，不能只在 H5 模拟器里验证

### 8.4 不投入的部分

uni-app 的 E2E 生态较弱，首版不投入自动化 E2E，以手测 + typecheck 覆盖。

---

## 9. UI 与设计系统（青少年风格）

**具体视觉方向由负责人协商决定，本文档不预设。** 但以下硬约束先定死，协商时不能让步：

1. **无障碍对比度**：正文文本与背景对比度不低于 4.5:1。粉彩填充上禁止使用白字——web-client 的 pouf 规范里实测白字压粉彩对比度仅 1.25:1 至 1.99:1，不达 WCAG AA，手机端不要重复这个错误。
2. **AI 回复的呈现不得给用户打等级标签。** 小艾的回复是对话，不是评分。不得出现「风险等级」「情绪评分」这类表达。该约束与签到模块设计文档 3.2 节同源。
3. **负面状态不得使用惩罚性文案。** 不使用「你已连续 X 天未…」这类表述。同上，与签到模块 3.1 节同源。

pouf 是 Web 端的设计语言，手机端可以延续其配色与气质以保持品牌一致，但**组件必须新建**，不能引用 `packages/ui`。

---

## 10. 分期实施计划

| 期 | 内容 | 说明 |
| --- | --- | --- |
| **第 1 期** | monorepo 骨架 + 共享层 adapter + 登录/注册 + 个人中心三块 | **需求方指定的第一批交付物** |
| 第 2 期 | 菜单 1 问答 | 前置：流式方案在目标端验证通过 |
| 第 3 期 | 菜单 2：咨询师目录/详情 → 图文咨询 → 预约下单（虚拟支付） | 图文咨询涉及第二条实时通道，独立验证 |

第 1 期内部的顺序建议：先完成 `apps/mobile` 的工程骨架与 `packages/api-client` 的 adapter（含测试），**再**做页面。adapter 是全部页面的地基，页面做完再回头改 adapter 会推翻所有联调结果。

---

## 11. 已知风险与待定项

### 待定项（开工前需确认）

| 项 | 负责方 | 影响 |
| --- | --- | --- |
| uni-app 项目形态：CLI（vite）形态融入 monorepo，还是 HBuilderX 独立项目 | 实施同学 | 见 11.1，**决定依赖与 CI 的组织方式** |
| 青少年风格的具体视觉方向 | 负责人 | 影响全部 UI 工作，见第 9 节 |
| `packages/config` 的具体内容与可用性 | 实施同学核实 | 影响是否复用 |
| App 端打包与发布渠道：安卓与 iOS 各自怎么分发 | 负责人 | iOS 需要开发者账号与审核周期，影响实际交付时间；安卓需确认是否上架应用商店还是直接分发安装包 |

### 11.1 项目形态的取舍（留给实施同学决定）

| 形态 | 收益 | 代价 |
| --- | --- | --- |
| **CLI 形态**（`vite` + `@dcloudio/uni-app`） | 保留 pnpm workspace 插槽，与 backend / web-client 共用 Turbo 与 CI；`packages/domain` 直接 import，契约单一来源 | 多端编译配置有学习曲线；HBuilderX 的图形化调试用不上 |
| **HBuilderX 独立项目** | 上手快，真机调试与打包方便 | **脱离 monorepo**：依赖版本、TS 配置、CI 各管一套；`packages/domain` 的共享退化为手工同步，两端类型漂移几乎必然发生 |

**无论选哪种，第 4 节的共享层契约不变。**

### 已知风险

1. **流式问答在非 H5 端可能无法实现。** 见 6.1。若目标端实测不支持分块接收，菜单 1 的体验会明显弱于 Web 端，需要提前告知负责人而不是事后解释。
2. **会话凭据以明文形式落盘。** 见 5.2。已作为取舍接受，但涉及未成年人数据，安全评审时应重新评估。
3. **虚拟支付不是可上线的支付方案。** 见 7.4。下单即置为已支付，等价于预约全免费；若被误当作正式方案上线，任何未授权用户都可以把咨询师时间占满。**上线前必须替换。**
4. **adapter 的错误映射若写错，问题会在线上才暴露。** 见 8.2，已用强制测试覆盖。
5. **`apps/mobile` 与 `apps/parent-portal` 未在根 `CLAUDE.md` 中登记。** 本模块落地后需要同步更新该文档，否则后续同学不知道这两个 app 的存在。
6. **本模块必然新增依赖**（uni-app 全家桶），会改动 `pnpm-lock.yaml`。这与根 `CLAUDE.md` 规则 1 冲突，需要一次显式的、有说明的锁文件更新，不能顺手改。
