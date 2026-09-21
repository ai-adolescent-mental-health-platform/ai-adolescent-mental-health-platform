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

const httpClient = createHttpClient({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080",
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

export { httpClient };
