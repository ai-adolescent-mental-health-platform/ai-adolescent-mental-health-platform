<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";

onLaunch(() => {
  // 路由守卫已在 main.ts 的 createApp 中安装。
  // 这里保留启动钩子，供后续（#33 起）做会话恢复 / 版本检查使用。
});
</script>

<style>
@import "./styles/theme.css";
@import "./styles/layout.css";

page {
  background-color: var(--color-bg-page);
  color: var(--color-text-primary);
  font-size: var(--font-body);
  line-height: 1.6;
}
</style>
