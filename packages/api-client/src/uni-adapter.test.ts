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

describe("uni adapter public entry", () => {
  it("is re-exported from the package root", async () => {
    const root = await import("./index.js");
    expect(typeof (root as Record<string, unknown>).createUniAdapter).toBe("function");
  });
});

describe("createUniAdapter absolute url", () => {
  it("keeps an absolute url and ignores baseURL, matching axios buildFullPath", async () => {
    const { request, calls } = fakeUni({ statusCode: 200, data: { code: 200, message: "ok", data: null } });
    const http = createHttpClient({ baseURL: "http://localhost:8080", adapter: createUniAdapter(request) });

    await http.get("https://oss.example.com/avatar.png");

    expect(calls[0].url).toBe("https://oss.example.com/avatar.png");
  });

  it("still appends query params to an absolute url", async () => {
    const { request, calls } = fakeUni({ statusCode: 200, data: { code: 200, message: "ok", data: null } });
    const http = createHttpClient({ baseURL: "http://localhost:8080", adapter: createUniAdapter(request) });

    await http.get("https://oss.example.com/avatar.png", { query: { sign: "abc", expire: 120 } });

    expect(calls[0].url).toBe("https://oss.example.com/avatar.png?sign=abc&expire=120");
  });
});

describe("createUniAdapter synchronous throw", () => {
  it("maps a synchronously throwing uni.request to kind=network, not parse", async () => {
    const request = vi.fn(() => {
      throw new Error("uni.request is not available");
    }) as unknown as UniRequestLike;

    const http = createHttpClient({ baseURL: "http://localhost:8080", adapter: createUniAdapter(request) });

    await expect(http.get("/anything")).rejects.toMatchObject({
      name: "ApiClientError",
      kind: "network",
    });
  });
});
