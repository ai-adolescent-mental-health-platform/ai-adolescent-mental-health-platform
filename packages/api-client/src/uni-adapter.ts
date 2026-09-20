import { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from "axios";

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

/**
 * 与 axios 的 isAbsoluteURL 同款判定：带协议的 URL，或协议相对 URL（`//host/path`）。
 * 绝对地址（例如对象存储的预签名 URL）不参与 baseURL 拼接。
 */
const ABSOLUTE_URL_PATTERN = /^([a-z][a-z\d+\-.]*:)?\/\//i;

function buildUrl(config: InternalAxiosRequestConfig): string {
  const path = config.url ?? "";
  let url: string;

  if (ABSOLUTE_URL_PATTERN.test(path)) {
    // 对齐 axios 的 buildFullPath：绝对 URL 直接返回，忽略 baseURL。
    // 不这么判，baseURL 会被无脑拼在前面，发出畸形请求（服务端 404，却报成 kind: "http"）。
    url = path;
  } else {
    const base = (config.baseURL ?? "").replace(/\/$/, "");
    url = `${base}${path.startsWith("/") ? path : `/${path}`}`;
  }

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
      try {
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

            // 第五个参数必须是 response：toError 读 axiosError.response?.status 来分类。
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
            // 传输层失败没有响应对象，第五个参数必须是 undefined，否则会被误判成 HTTP 错误。
            reject(new AxiosError(err?.errMsg ?? "Network Error", AxiosError.ERR_NETWORK, config, undefined, undefined));
          },
        });
      } catch (error) {
        // uni 规范里 uni.request 走 fail 回调而非抛错，但平台实现若同步抛错，
        // 原始错误会绕过 axios.isAxiosError，被 index.ts 兜底成 kind: "parse"（文案「请求处理失败」），
        // 掩盖真实的传输层问题。这里归类为 ERR_NETWORK，且与 fail 分支一样不带 response。
        const message = error instanceof Error && error.message ? error.message : "Network Error";
        reject(new AxiosError(message, AxiosError.ERR_NETWORK, config, undefined, undefined));
      }
    });
}
