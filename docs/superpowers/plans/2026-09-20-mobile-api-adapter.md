# 手机端共享层 adapter 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `packages/api-client` 能在 uni-app 运行时下发出请求，同时 web-client 的行为零变化。

**Architecture:** 给已有的 `createHttpClient` 工厂增加一个**可选**的 axios `adapter` 注入点；另写一个把 axios 请求转成 `uni.request` 的适配器。web-client 不传这个参数，因此继续走 axios 默认 adapter，一行调用点都不用改。关键难点不在转发请求，而在于**失败时必须构造出 axios 认得的错误对象形状**——`toError`（`index.ts:54-66`）读的是 `axiosError.response?.status` 与 `.data`，形状不对会导致「请求能通，但 401 不触发重新登录」。

**Tech Stack:** TypeScript、axios 1.16、vitest 4（`packages/api-client` 已配置，无独立配置文件，走默认）。

**Spec:** `docs/superpowers/specs/2026-09-20-mobile-app-design.md`（第 2.3、2.4、4 节）

## Global Constraints

- **不新增任何依赖**，`packages/api-client/package.json` 与 `pnpm-lock.yaml` 均不改动。本计划用到的 axios、vitest、typescript 都已在 devDependencies / dependencies 中。
- **`ApiClientErrorKind` 的六个取值不得增删**：`"http" | "business" | "network" | "unauthorized" | "forbidden" | "parse"`（`index.ts:23`）。
- **web-client 行为零变化**：不传 `adapter` 时，`axios.create` 收到的 adapter 必须是 undefined，继续走 axios 默认实现。
- **已有测试必须继续通过**：`packages/api-client/src/index.test.ts` 现有 10 个用例不得改动、不得删除。
- **文档与代码注释不写 emoji**（根 `CLAUDE.md` 规则 7）。
- 每个任务结束前必须执行：`pnpm --filter @ai-adolescent-mental-health/api-client test`
- 类型检查：`pnpm --filter @ai-adolescent-mental-health/api-client typecheck`

---

### Task 1: 给 createHttpClient 加 adapter 注入点

**Files:**
- Modify: `packages/api-client/src/index.ts`（`HttpClientOptions` 在 L41-46；`axios.create` 在 L400-406）
- Test: `packages/api-client/src/index.test.ts`（追加，不改动已有用例）

**Interfaces:**
- Consumes: 无（这是第一个任务）
- Produces:
  - `HttpClientOptions` 新增字段 `adapter?: AxiosAdapter`
  - `createHttpClient(options: HttpClientOptions)` 的签名不变，返回值不变

- [ ] **Step 1: 写失败测试**

追加到 `packages/api-client/src/index.test.ts` 末尾：

```ts
describe("createHttpClient adapter injection", () => {
  it("uses the injected adapter and unwraps the response envelope", async () => {
    const adapter = vi.fn().mockResolvedValue({
      data: { code: 200, message: "ok", data: { id: 7 } },
      status: 200,
      statusText: "OK",
      headers: {},
      config: {} as never,
    });

    const http = createHttpClient({ baseURL: "http://localhost", adapter });
    const result = await http.get<{ id: number }>("/ping");

    expect(adapter).toHaveBeenCalledTimes(1);
    expect(result).toEqual({ id: 7 });
  });

  it("does not install an injected adapter when the option is omitted", () => {
    const http = createHttpClient({ baseURL: "http://localhost" });
    expect(http.raw.defaults.adapter).not.toBeUndefined();
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `pnpm --filter @ai-adolescent-mental-health/api-client test`
Expected: 第一个用例 FAIL —— TypeScript 报 `adapter` 不在 `HttpClientOptions` 类型上（vitest 经 esbuild 转译可能不报类型错，但 adapter 未被使用，断言 `toHaveBeenCalledTimes(1)` 会失败）。

- [ ] **Step 3: 最小实现**

在 `packages/api-client/src/index.ts` 第 1 行的 import 中加入 `AxiosAdapter` 类型：

```ts
import axios, { AxiosError, type AxiosAdapter, type AxiosInstance, type AxiosRequestConfig } from "axios";
```

把 `HttpClientOptions`（L41-46）改为：

```ts
export type HttpClientOptions = {
  baseURL: string;
  getToken?: () => string | undefined | null;
  onUnauthorized?: () => void;
  timeout?: number;
  /**
   * 可选的自定义传输层。不传时走 axios 默认实现（浏览器 / Node）。
   * uni-app 运行时通过它注入 uni.request 适配器，见 uni-adapter.ts。
   */
  adapter?: AxiosAdapter;
};
```

把 `axios.create`（L400-406）改为：

```ts
  const client: AxiosInstance = axios.create({
    baseURL: options.baseURL.replace(/\/$/, ""),
    timeout: options.timeout ?? 10000,
    headers: {
      "Content-Type": "application/json;charset=UTF-8",
    },
    adapter: options.adapter,
  });
```

**注意**：`adapter: undefined` 是合法的，axios 内部会回退到默认 adapter。不要写成条件展开——那会让两种路径产生分支差异，反而增加不确定性。

- [ ] **Step 4: 运行测试确认通过**

Run: `pnpm --filter @ai-adolescent-mental-health/api-client test`
Expected: 全部 PASS（含原有 10 个用例）

- [ ] **Step 5: 类型检查**

Run: `pnpm --filter @ai-adolescent-mental-health/api-client typecheck`
Expected: 无输出（通过）

- [ ] **Step 6: 提交**

```bash
git add packages/api-client/src/index.ts packages/api-client/src/index.test.ts
git commit -m "feat(api-client): createHttpClient 支持注入自定义 adapter"
```

---

### Task 2: 实现 uni.request 适配器的成功路径

**Files:**
- Create: `packages/api-client/src/uni-adapter.ts`
- Test: `packages/api-client/src/uni-adapter.test.ts`

**Interfaces:**
- Consumes: Task 1 的 `HttpClientOptions.adapter`
- Produces:
  - `type UniRequestOptions`、`type UniRequestTask`、`type UniRequestLike`
  - `function createUniAdapter(request: UniRequestLike): AxiosAdapter`

- [ ] **Step 1: 写失败测试**

创建 `packages/api-client/src/uni-adapter.test.ts`：

```ts
import { describe, it, expect, vi } from "vitest";
import { createHttpClient } from "./index.js";
import { createUniAdapter, type UniRequestLike, type UniRequestOptions } from "./uni-adapter.js";

type UniResult = { statusCode: number; data: unknown; header?: Record<string, string> };

function fakeUni(result: UniResult) {
  const calls: UniRequestOptions[] = [];
  const request: UniRequestLike = vi.fn((options: UniRequestOptions) => {
    calls.push(options);
    options.success?.({ data: result.data, statusCode: result.statusCode, header: result.header ?? {} });
    return { abort: vi.fn() };
  });
  return { request, calls };
}

describe("createUniAdapter success path", () => {
  it("sends method, url and headers, then unwraps the envelope", async () => {
    const { request, calls } = fakeUni({
      statusCode: 200,
      data: { code: 200, message: "ok", data: { id: 7, nickname: "小艾" } },
    });

    const http = createHttpClient({
      baseURL: "http://localhost:8080",
      getToken: () => "tok-123",
      adapter: createUniAdapter(request),
    });

    const result = await http.get<{ id: number; nickname: string }>("/user/profile");

    expect(result).toEqual({ id: 7, nickname: "小艾" });
    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe("http://localhost:8080/user/profile");
    expect(calls[0].method).toBe("GET");
    expect(calls[0].header?.Authorization).toBe("Bearer tok-123");
    expect(calls[0].header?.token).toBe("tok-123");
  });

  it("serializes query params onto the url and skips null/undefined", async () => {
    const { request, calls } = fakeUni({
      statusCode: 200,
      data: { code: 200, message: "ok", data: null },
    });

    const http = createHttpClient({ baseURL: "http://localhost:8080", adapter: createUniAdapter(request) });

    await http.get("/content/list", { query: { page: 1, size: 20, keyword: null, tag: undefined } });

    expect(calls[0].url).toBe("http://localhost:8080/content/list?page=1&size=20");
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `pnpm --filter @ai-adolescent-mental-health/api-client test`
Expected: FAIL —— `Failed to resolve import "./uni-adapter.js"`

- [ ] **Step 3: 实现**

创建 `packages/api-client/src/uni-adapter.ts`：

```ts
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from "axios";

export type UniRequestOptions = {
  url: string;
  method: string;
  data?: unknown;
  header?: Record<string, string>;
  timeout?: number;
  success?: (res: { data: unknown; statusCode: number; header: Record<string, string> }) => void;
  fail?: (err: { errMsg?: string }) => void;
};

export type UniRequestTask = { abort: () => void };

/** uni.request 的函数签名。传进来而不是读全局，便于测试与显式声明平台依赖。 */
export type UniRequestLike = (options: UniRequestOptions) => UniRequestTask;

function buildUrl(config: InternalAxiosRequestConfig): string {
  const base = (config.baseURL ?? "").replace(/\/$/, "");
  const path = config.url ?? "";
  const url = `${base}${path.startsWith("/") ? path : `/${path}`}`;

  const query = config.params as Record<string, unknown> | undefined;
  if (!query) return url;

  const parts: string[] = [];
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null) continue;
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  }
  return parts.length ? `${url}?${parts.join("&")}` : url;
}

function toPlainHeaders(headers: unknown): Record<string, string> {
  const result: Record<string, string> = {};
  if (!headers) return result;

  const source = headers as { toJSON?: () => Record<string, unknown> } & Record<string, unknown>;
  const plain = typeof source.toJSON === "function" ? source.toJSON() : source;

  for (const [key, value] of Object.entries(plain)) {
    if (value === undefined || value === null) continue;
    result[key] = String(value);
  }
  return result;
}

export function createUniAdapter(request: UniRequestLike): AxiosAdapter {
  return (config: InternalAxiosRequestConfig): Promise<AxiosResponse> =>
    new Promise<AxiosResponse>((resolve, reject) => {
      request({
        url: buildUrl(config),
        method: (config.method ?? "get").toUpperCase(),
        data: config.data,
        header: toPlainHeaders(config.headers),
        timeout: config.timeout,
        success: (res) => {
          const response: AxiosResponse = {
            data: res.data,
            status: res.statusCode,
            statusText: String(res.statusCode),
            headers: res.header ?? {},
            config,
            request: undefined,
          };

          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(response);
            return;
          }

          // 暂时用朴素 Error。错误映射在 Task 3 中修正——那里的测试会暴露问题。
          reject(new Error(`Request failed with status code ${res.statusCode}`));
        },
        fail: (err) => {
          reject(new Error(err?.errMsg ?? "Network Error"));
        },
      });
    });
}
```

**这里有意留下缺陷。** `reject` 出去的是普通 `Error`，不是 `AxiosError`，因此 `toError`（`index.ts:54-66`）的 `axios.isAxiosError(error)` 判定为 false，会一路落到最后的兜底分支，把所有失败都归成 `kind: "parse"`。Task 3 的测试就是用来抓这个的。

- [ ] **Step 4: 运行测试确认通过**

Run: `pnpm --filter @ai-adolescent-mental-health/api-client test`
Expected: 全部 PASS

- [ ] **Step 5: 提交**

```bash
git add packages/api-client/src/uni-adapter.ts packages/api-client/src/uni-adapter.test.ts
git commit -m "feat(api-client): 新增 uni.request 适配器（成功路径）"
```

---

### Task 3: 适配器的错误映射（本计划最关键的一步）

错误的错误映射是本设计里唯一「写错了也看不出来」的地方：请求会正常返回，直到某天用户登录过期却没有任何反应。本任务用测试把三种失败形态钉死。

**Files:**
- Test: `packages/api-client/src/uni-adapter.test.ts`（追加）
- Modify: `packages/api-client/src/uni-adapter.ts`（若测试暴露问题才改）

**Interfaces:**
- Consumes: Task 2 的 `createUniAdapter`、Task 1 的 `adapter` 注入点
- Produces: 无新接口，只固定行为契约

- [ ] **Step 1: 写失败测试**

追加到 `packages/api-client/src/uni-adapter.test.ts`：

```ts
describe("createUniAdapter error mapping", () => {
  it("maps HTTP 401 to ApiClientError kind=unauthorized and fires onUnauthorized", async () => {
    const { request } = fakeUni({ statusCode: 401, data: { code: 401, message: "登录已过期" } });
    const onUnauthorized = vi.fn();
    const http = createHttpClient({
      baseURL: "http://localhost:8080",
      onUnauthorized,
      adapter: createUniAdapter(request),
    });

    await expect(http.get("/user/profile")).rejects.toMatchObject({
      name: "ApiClientError",
      kind: "unauthorized",
      status: 401,
    });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("maps HTTP 403 to ApiClientError kind=forbidden", async () => {
    const { request } = fakeUni({ statusCode: 403, data: { code: 403, message: "权限不足" } });
    const http = createHttpClient({ baseURL: "http://localhost:8080", adapter: createUniAdapter(request) });

    await expect(http.get("/admin/users")).rejects.toMatchObject({
      name: "ApiClientError",
      kind: "forbidden",
      status: 403,
    });
  });

  it("maps a transport failure to kind=network, not an HTTP error", async () => {
    const request = vi.fn((options: UniRequestOptions) => {
      options.fail?.({ errMsg: "request:fail timeout" });
      return { abort: vi.fn() };
    }) as unknown as UniRequestLike;

    const http = createHttpClient({ baseURL: "http://localhost:8080", adapter: createUniAdapter(request) });

    await expect(http.get("/anything")).rejects.toMatchObject({
      name: "ApiClientError",
      kind: "network",
    });
  });

  it("maps a business error code to kind=business and keeps the code", async () => {
    const { request } = fakeUni({ statusCode: 200, data: { code: 4001, message: "用户名已存在" } });
    const http = createHttpClient({ baseURL: "http://localhost:8080", adapter: createUniAdapter(request) });

    await expect(http.post("/user/register", {})).rejects.toMatchObject({
      name: "ApiClientError",
      kind: "business",
      code: 4001,
    });
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `pnpm --filter @ai-adolescent-mental-health/api-client test`
Expected: 本任务 4 个用例**全部 FAIL**，且失败表现一致——`kind` 不是期望值。原因是 Task 2 用朴素 `Error` 拒绝，`toError` 的 `axios.isAxiosError` 判定为 false，四个用例都会落到 `index.ts:65` 的兜底分支得到 `kind: "parse"`。Task 2 的 2 个用例此时仍应 PASS。

- [ ] **Step 3: 实现**

把 `uni-adapter.ts` 的 import 改为引入 `AxiosError`（它既是类型也是值，**不能用 `import type`**）：

```ts
import { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from "axios";
```

把两处 `reject` 改为构造 `AxiosError`：

```ts
          reject(
            new AxiosError(
              `Request failed with status code ${res.statusCode}`,
              String(res.statusCode),
              config,
              undefined,
              response,
            ),
          );
        },
        fail: (err) => {
          reject(new AxiosError(err?.errMsg ?? "Network Error", AxiosError.ERR_NETWORK, config, undefined, undefined));
        },
```

**三个细节是全部关键：**

- **`success` 分支的第五个参数是 `response`，必须传。** `toError`（`index.ts:58`）读 `axiosError.response?.status` 来分类；不传 `response`，401 与 403 都会掉进网络错误分支。
- **`fail` 分支的第五个参数必须是 `undefined`。** 传输层失败没有响应对象；这里传了任何东西，网络错误都会被误判成 HTTP 错误。
- **`response.status` 必须是数字 `401`，不能是字符串 `"401"`。** 否则 `index.ts:59` 的 `status === 401` 严格比较不成立，`onUnauthorized` 不会被调用——这正是「登录过期了但界面毫无反应」的成因。

- [ ] **Step 4: 运行测试确认全部通过**

Run: `pnpm --filter @ai-adolescent-mental-health/api-client test`
Expected: 全部 PASS（Task 2 的 2 个 + 本任务 4 个 + 原有 10 个）

- [ ] **Step 5: 提交**

```bash
git add packages/api-client/src/uni-adapter.ts packages/api-client/src/uni-adapter.test.ts
git commit -m "test(api-client): 固定 uni 适配器的 401/403/网络/业务错误映射"
```

---

### Task 4: 导出适配器并记录接入方式

**Files:**
- Modify: `packages/api-client/src/index.ts`（文件末尾追加导出）
- Test: `packages/api-client/src/uni-adapter.test.ts`（追加一条导入路径断言）

**不修改 `package.json`。** Step 3 说明了原因。

**Interfaces:**
- Consumes: Task 2 的 `createUniAdapter`
- Produces: 子路径导出 `@ai-adolescent-mental-health/api-client/uni`

- [ ] **Step 1: 写失败测试**

追加到 `packages/api-client/src/uni-adapter.test.ts`：

```ts
describe("uni adapter public entry", () => {
  it("is re-exported from the package root", async () => {
    const root = await import("./index.js");
    expect(typeof (root as Record<string, unknown>).createUniAdapter).toBe("function");
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `pnpm --filter @ai-adolescent-mental-health/api-client test`
Expected: FAIL —— `expected "undefined" to be "function"`

- [ ] **Step 3: 实现**

在 `packages/api-client/src/index.ts` 末尾追加：

```ts
export { createUniAdapter } from "./uni-adapter.js";
export type { UniRequestLike, UniRequestOptions, UniRequestTask } from "./uni-adapter.js";
```

在 `packages/api-client/package.json` 的 `exports` 中，为未来可能的独立子路径入口预留（当前根入口已够用，此处**只加注释说明**，不新增 exports 键，避免引入未使用的配置）：

```json
  "exports": {
    ".": "./src/index.ts"
  },
```

**不做**子路径 exports。当前所有消费者都从根入口 import，新增一个只在未来才用的子路径是过早抽象；等真的出现第二个消费场景再加。

- [ ] **Step 4: 运行测试确认通过**

Run: `pnpm --filter @ai-adolescent-mental-health/api-client test`
Expected: 全部 PASS

- [ ] **Step 5: 全量校验**

Run:
```bash
pnpm --filter @ai-adolescent-mental-health/api-client test
pnpm --filter @ai-adolescent-mental-health/api-client typecheck
pnpm --filter @ai-adolescent-mental-health/web-client typecheck
```
Expected: 三条全部通过。**最后一条是关键**——它证明改动没有破坏 web-client 的类型契约。

- [ ] **Step 6: 提交**

```bash
git add packages/api-client/src/index.ts
git commit -m "feat(api-client): 导出 uni.request 适配器"
```

---

## 完成后的接口速查

手机端接入时的写法（供后续计划引用）：

```ts
import { createHttpClient, createApiClient, createUniAdapter } from "@ai-adolescent-mental-health/api-client";

const http = createHttpClient({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  getToken: () => uni.getStorageSync("aiamh.mobile.token") || undefined,
  onUnauthorized: () => {
    uni.removeStorageSync("aiamh.mobile.token");
    uni.reLaunch({ url: "/pages/login/index" });
  },
  adapter: createUniAdapter(uni.request),
});

export const api = createApiClient(http);
```

## 不在本计划范围内

- `apps/mobile` 工程骨架（阻塞于 uni-app 项目形态的决定）
- 登录 / 注册页面、个人中心三块（阻塞于工程骨架）
- 会话存储封装（`uni.setStorageSync`，属于骨架计划）
- 视觉方向与基础组件（基调已定为绿色，具体色值待负责人确认）
