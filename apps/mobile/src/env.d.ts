/// <reference types="@dcloudio/types" />

/**
 * 环境变量类型声明。
 *
 * 这里手写而不引入 `vite/client`：vite 只是 `@dcloudio/vite-plugin-uni` 的
 * peer 依赖，且被根 pnpm-workspace.yaml 的 override 统一到 8.0.16，
 * 并非 apps/mobile 的直接依赖，直接引用 `vite/client` 会导致 typecheck 失败。
 * 手写声明既避免该耦合，又保留了后续（#33 接 api-client 时）读写 VITE_ 前缀变量的类型信息。
 */

interface ImportMetaEnv {
  /** 后端 API 基地址；#33 接入 packages/api-client 时使用 */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module "*.vue" {
  import type { DefineComponent } from "vue";

  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>;
  export default component;
}
