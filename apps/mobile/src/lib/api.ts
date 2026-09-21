import {
  createApiClient,
  createHttpClient,
  createUniAdapter,
  type UniRequestTask,
} from "@ai-adolescent-mental-health/api-client";

import { clearSession, getToken } from "./session";
import { LOGIN_PAGE } from "@/router/routes";

/**
 * 手机端统一 api 层。
 *
 * 约定（与根 AGENTS.md 一致）：页面内**不得**出现直接的 uni.request / fetch，
 * 所有后端通信一律经此文件导出的 `api`。401 的处理也只在这里发生，
 * 页面层不要写「如果 status === 401 就……」这类判断。
 */

type RealRequestOptions = Parameters<Uni["request"]>[0];

/**
 * 后端基地址。单独导出，供**无法走 axios 适配器**的 uni API 复用（目前只有 `uni.uploadFile`）。
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080";

const httpClient = createHttpClient({
  baseURL: API_BASE_URL,
  getToken,
  // 401 统一走共享层回调：清会话 + 回登录页。页面层不再重复判断。
  onUnauthorized: () => {
    clearSession();
    uni.reLaunch({ url: LOGIN_PAGE });
  },
  adapter: createUniAdapter(
    /*
     * 这里有两处断言，各自解决一个独立问题，缺一不可：
     *
     * 1. `options as unknown as RealRequestOptions` —— 解决 method / data 的逆变不兼容。
     *    适配器声明的入参是 `method: string`、`data?: unknown`，而 uni 的真实入参是
     *    `method: 'GET' | 'POST' | ...` 字面量联合与 `data?: string | AnyObject | ArrayBuffer`。
     *    函数参数为逆变比较，宽类型不能赋给窄类型，故直接传 `uni.request` 无法通过类型检查。
     *
     * 2. `as unknown as UniRequestTask` —— 解决 promisify 重载把返回类型解析成 Promise 的问题。
     *    `@dcloudio/types` 的 promisify 补丁给 `uni.request` 追加了一个返回
     *    `Promise<RequestSuccessCallbackResult>` 的重载，且它排在返回 `RequestTask` 的重载之前，
     *    于是类型层面拿到的是 Promise（没有 `abort`），与运行时的实际返回不符。
     *
     * **不要为了避免上面第 2 条而删掉 success / fail 回调**：H5 运行时实测，不传回调时
     * `uni.request` 返回 Promise（无 abort），传了才返回带可用 abort 的 RequestTask。
     *
     * 这两处断言是**接线层面的权宜处理**。根治方式是在 `packages/api-client` 侧收窄
     * `UniRequestOptions`（method 用字面量联合、data 用 `string | AnyObject | ArrayBuffer`），
     * 让适配器签名与 uni 的真实类型一致——那属于该工作区的改动，不在本步范围内。
     */
    (options) => uni.request(options as unknown as RealRequestOptions) as unknown as UniRequestTask,
  ),
});

export const api = createApiClient(httpClient);

/**
 * 上传单个文件（目前用于头像），返回后端给出的文件 URL。
 *
 * 为什么不能用 api 层：`createUniAdapter` 是把 axios 请求转成 `uni.request`，而
 * `uni.request` 的 `data` 只接受 `string | AnyObject | ArrayBuffer`，**承载不了 FormData**；
 * 强行传会静默发出一个没有文件体的请求（服务端收到空文件），属于「不报错但结果是错的」。
 * 因此这里直接用 `uni.uploadFile`——这是本工作区唯一一处绕开适配器的网络调用，
 * 封装在 api 层内部，页面依然只调 `api` 侧的函数。
 *
 * 注意：`/common/upload` 的 `folder` 是后端 `@RequestParam`，这里用 `formData` 承载，
 * **不带任何 query 参数**（适配器/uni 对带 query 的 URL 拼接行为不一致，容易产出畸形地址）。
 */
export function uploadImage(filePath: string, folder: string): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const token = getToken();

    uni.uploadFile({
      url: `${API_BASE_URL}/common/upload`,
      filePath,
      name: "file",
      formData: { folder },
      // 后端该端点为 permitAll，带上凭据是为了将来收紧权限时不用改客户端；
      // 与共享层一致，同时给 Authorization 与 token 两个头。
      header: token ? { Authorization: `Bearer ${token}`, token } : {},
      success: (res) => {
        let payload: { code?: number; message?: string; data?: unknown };
        try {
          payload = JSON.parse(res.data) as typeof payload;
        } catch {
          reject(new Error("上传响应解析失败"));
          return;
        }

        if (res.statusCode === 401 || payload.code === 401) {
          // uni.uploadFile 走不到 axios 适配器，401 只能在这里收口；
          // 处理方式与 createHttpClient 的 onUnauthorized 保持一致（清会话 + 回登录页）。
          clearSession();
          uni.reLaunch({ url: LOGIN_PAGE });
          reject(new Error(payload.message || "登录已过期，请重新登录"));
          return;
        }

        if (payload.code !== 200 || !payload.data) {
          reject(new Error(payload.message || "上传失败"));
          return;
        }

        resolve(String(payload.data));
      },
      fail: (err) => {
        reject(new Error(err?.errMsg || "上传失败，请重试"));
      },
    });
  });
}

export { httpClient };
