import { createSSRApp } from "vue";
import App from "./App.vue";
import { installRouteGuard } from "./router/guard";

// uni-app 要求导出 createApp 工厂（App 端与 H5 端共用一套入口）。
export function createApp() {
  // 守卫必须在任何页面加载前装好，否则首个页面的跳转会漏检。
  installRouteGuard();

  const app = createSSRApp(App);
  return { app };
}
