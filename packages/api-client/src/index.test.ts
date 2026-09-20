import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import type { AxiosAdapter } from "axios";
import { createHttpClient, createApiClient, streamAiChat, ApiClientError } from "./index.js";

function mockHttp(get?: unknown, post?: unknown, put?: unknown, del?: unknown) {
  return {
    raw: {} as never,
    get: vi.fn().mockResolvedValue(get),
    post: vi.fn().mockResolvedValue(post),
    put: vi.fn().mockResolvedValue(put),
    delete: vi.fn().mockResolvedValue(del),
  };
}

describe("createApiClient content", () => {
  it("article comments maps a flat list response (not PageResult)", async () => {
    const mockComments = [
      {
        id: 1,
        content: "好文章",
        nickname: "张三",
        headPath: "/avatars/1.png",
        createTime: "2025-01-01T10:00:00",
        likeCount: 5,
        liked: false,
        userId: 10,
        replies: [
          {
            id: 2,
            content: "回复内容",
            nickname: "李四",
            replyToNickname: "张三",
            createTime: "2025-01-01T11:00:00",
          },
        ],
      },
    ];

    const http = mockHttp(mockComments);
    const api = createApiClient(http);
    const result = await api.content.comments(1);

    expect(http.get).toHaveBeenCalledWith(
      "/content/article/comments/1",
      expect.objectContaining({ query: { page: 1, size: 20 } }),
    );
    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      id: 1,
      content: "好文章",
      nickname: "张三",
      headPath: "/avatars/1.png",
      likeCount: 5,
      userId: 10,
    });
    expect(result[0].replies).toHaveLength(1);
    expect(result[0].replies[0]).toMatchObject({
      id: 2,
      content: "回复内容",
      nickname: "李四",
      replyToNickname: "张三",
    });
  });

  it("article comments returns empty array when server returns empty list", async () => {
    const http = mockHttp([]);
    const api = createApiClient(http);
    const result = await api.content.comments(1);
    expect(result).toEqual([]);
  });

  it("article comments handles null/undefined response gracefully", async () => {
    const http = mockHttp(null);
    const api = createApiClient(http);
    const result = await api.content.comments(1);
    expect(result).toEqual([]);
  });

  it("book comments uses the correct URL /book/{id}/comment/list", async () => {
    const pageResult = {
      total: 2,
      records: [
        {
          id: 1,
          content: "书评",
          userNickname: "读者A",
          userAvatar: "/a.png",
          createTime: "2025-01-01T10:00:00",
        },
      ],
      current: 1,
      size: 10,
    };

    const http = mockHttp(pageResult);
    const api = createApiClient(http);
    const result = await api.content.bookComments(5);

    expect(http.get).toHaveBeenCalledWith(
      "/book/5/comment/list",
      expect.objectContaining({ query: { page: 1, size: 10 } }),
    );
    expect(result.records).toHaveLength(1);
  });
});

describe("streamAiChat", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const encoder = new TextEncoder();

  function mockFetchResponse(chunks: string[]) {
    let callCount = 0;
    const mockReader = {
      read: vi.fn().mockImplementation(() => {
        if (callCount < chunks.length) {
          const value = encoder.encode(chunks[callCount]);
          callCount++;
          return Promise.resolve({ done: false, value });
        }
        return Promise.resolve({ done: true });
      }),
    };

    vi.mocked(fetch).mockResolvedValue({
      ok: true,
      status: 200,
      body: {
        getReader: () => mockReader,
      },
    } as unknown as Response);

    return mockReader;
  }

  it("preserves trailing whitespace in SSE tokens", async () => {
    const reader = mockFetchResponse(["data: hello \ndata: world\n"]);
    const chunks: string[] = [];
    const onChunk = (chunk: string) => chunks.push(chunk);

    await streamAiChat(
      { baseURL: "http://localhost", getToken: () => null },
      1,
      "test",
      onChunk,
    );

    // Trailing space on "hello " is preserved — not trimmed
    expect(chunks).toEqual(["hello ", "world"]);
  });

  it("handles SSE lines split across chunk boundaries", async () => {
    const reader = mockFetchResponse(["data: first chunk partial", " second part\n"]);

    const chunks: string[] = [];
    const onChunk = (chunk: string) => chunks.push(chunk);

    await streamAiChat(
      { baseURL: "http://localhost", getToken: () => null },
      1,
      "test",
      onChunk,
    );

    expect(chunks).toEqual(["first chunk partial second part"]);
  });

  it("skips truly empty data lines", async () => {
    const reader = mockFetchResponse(["data: valid\n", "data:\n", "data: another\n"]);

    const chunks: string[] = [];
    const onChunk = (chunk: string) => chunks.push(chunk);

    await streamAiChat(
      { baseURL: "http://localhost", getToken: () => null },
      1,
      "test",
      onChunk,
    );

    expect(chunks).toEqual(["valid", "another"]);
  });

  it("handles multiple complete SSE lines in a single chunk", async () => {
    const reader = mockFetchResponse(["data: line1\ndata: line2\n"]);

    const chunks: string[] = [];
    const onChunk = (chunk: string) => chunks.push(chunk);

    await streamAiChat(
      { baseURL: "http://localhost", getToken: () => null },
      1,
      "test",
      onChunk,
    );

    expect(chunks).toEqual(["line1", "line2"]);
  });

  it("handles full SSE messages with leading spaces", async () => {
    const reader = mockFetchResponse(["data:  leading space here\n"]);

    const chunks: string[] = [];
    const onChunk = (chunk: string) => chunks.push(chunk);

    await streamAiChat(
      { baseURL: "http://localhost", getToken: () => null },
      1,
      "test",
      onChunk,
    );

    // Leading space after data: prefix is removed by regex, but not the content spaces
    expect(chunks).toEqual([" leading space here"]);
  });

  it("processes final buffer when stream ends with incomplete line", async () => {
    const reader = mockFetchResponse(["data: last message"]);

    const chunks: string[] = [];
    const onChunk = (chunk: string) => chunks.push(chunk);

    await streamAiChat(
      { baseURL: "http://localhost", getToken: () => null },
      1,
      "test",
      onChunk,
    );

    expect(chunks).toEqual(["last message"]);
  });

  it("throws ApiClientError on non-ok response", async () => {
    vi.mocked(fetch).mockResolvedValue({
      ok: false,
      status: 500,
      body: null,
    } as unknown as Response);

    await expect(
      streamAiChat(
        { baseURL: "http://localhost", getToken: () => null },
        1,
        "test",
        vi.fn(),
      ),
    ).rejects.toThrow(ApiClientError);
  });
});

describe("api.user forgot-password", () => {
  it("sendForgotPasswordCode calls POST /user/forgot/send", async () => {
    const post = vi.fn().mockResolvedValue("ok");
    const http = { raw: {} as never, get: vi.fn(), post, put: vi.fn(), delete: vi.fn() };
    const api = createApiClient(http);

    await api.user.sendForgotPasswordCode("alice", "alice@test.com");

    expect(post).toHaveBeenCalledWith("/user/forgot/send", {
      username: "alice",
      email: "alice@test.com",
    });
  });

  it("verifyForgotPasswordCode calls POST /user/forgot/verify", async () => {
    const post = vi.fn().mockResolvedValue("ok");
    const http = { raw: {} as never, get: vi.fn(), post, put: vi.fn(), delete: vi.fn() };
    const api = createApiClient(http);

    await api.user.verifyForgotPasswordCode("alice", "alice@test.com", "123456");

    expect(post).toHaveBeenCalledWith("/user/forgot/verify", {
      username: "alice",
      email: "alice@test.com",
      code: "123456",
    });
  });

  it("resetPassword calls POST /user/forgot/reset", async () => {
    const post = vi.fn().mockResolvedValue("ok");
    const http = { raw: {} as never, get: vi.fn(), post, put: vi.fn(), delete: vi.fn() };
    const api = createApiClient(http);

    await api.user.resetPassword("alice", "alice@test.com", "123456", "newPwd", "newPwd");

    expect(post).toHaveBeenCalledWith("/user/forgot/reset", {
      username: "alice",
      email: "alice@test.com",
      code: "123456",
      newPassword: "newPwd",
      confirmPassword: "newPwd",
    });
  });
});

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

  it("installs the injected adapter only when the option is provided", () => {
    const injected = vi.fn();

    const withAdapter = createHttpClient({
      baseURL: "http://localhost",
      adapter: injected as unknown as AxiosAdapter,
    });
    const withoutAdapter = createHttpClient({ baseURL: "http://localhost" });

    expect(withAdapter.raw.defaults.adapter).toBe(injected);
    expect(withoutAdapter.raw.defaults.adapter).not.toBe(injected);
  });
});
