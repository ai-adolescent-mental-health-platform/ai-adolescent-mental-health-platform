import { defineConfig } from "vite";
import uni from "@dcloudio/vite-plugin-uni";

// uni-app CLI(vite) 形态：由 @dcloudio/vite-plugin-uni 接管多端编译。
// 端口固定 3200，避免与 backend(8080) / web-client(3300) / admin-portal(3101) 冲突。
//
// 两处与 monorepo 其他工作区不同的刻意安排，都有实测依据：
//
// 1. 本包不设 "type": "module"。@dcloudio/vite-plugin-uni 是 CJS 包
//    （package.json 无 type / exports，main 指向 dist/index.js，插件函数挂在
//    module.exports.default 上）。若把本包标为 ESM 包，Node 的原生 ESM/CJS 互操作
//    会把默认导入解析成整个 module.exports 对象，启动时报 "uni is not a function"。
//    保持 CJS，vite 才会以 CJS 方式打包配置文件并正确识别 __esModule 标记。
//
// 2. 这里不声明 "@/" 别名。插件在 config 钩子里已注入
//    /^(~@|@)\// → <inputDir>/(.*) 的别名（见 vite-plugin-uni/dist/config/resolve.js），
//    重复声明只会引入 import.meta.url 这类在 CJS 配置下不可用的写法。
export default defineConfig({
  plugins: [uni()],
  server: {
    host: "127.0.0.1",
    port: 3200,
  },
});
