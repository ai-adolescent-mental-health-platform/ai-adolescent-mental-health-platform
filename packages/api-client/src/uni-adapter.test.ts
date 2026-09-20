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
