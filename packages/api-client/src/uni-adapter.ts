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
