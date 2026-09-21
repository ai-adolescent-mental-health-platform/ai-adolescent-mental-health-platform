<script setup lang="ts">
/**
 * 基础导航栏。
 *
 * pages.json 的 globalStyle 已设 `navigationStyle: "custom"`，各页统一用本组件，
 * 因此导航栏配色同样走主题变量（原生导航栏只能在 JSON 里写死色值，无法引用变量）。
 * 顶部用 uni-app 提供的 --status-bar-height 变量避让状态栏，H5 端该值为 0。
 */

// getCurrentPages 是 uni-app 的全局函数（由 @dcloudio/types 声明），无需 import。
withDefaults(
  defineProps<{
    title?: string;
    showBack?: boolean;
  }>(),
  {
    title: "",
    showBack: false,
  },
);

function handleBack(): void {
  const pages = getCurrentPages();
  if (pages.length > 1) {
    uni.navigateBack();
    return;
  }
  // 无历史栈（冷启动直达）时回首页，避免返回动作变成死键。
  uni.switchTab({ url: "/pages/home/index" });
}
</script>

<template>
  <view class="m-navbar">
    <view class="m-navbar__bar">
      <view class="m-navbar__side">
        <text v-if="showBack" class="m-navbar__action" @tap="handleBack">返回</text>
      </view>
      <text class="m-navbar__title">{{ title }}</text>
      <view class="m-navbar__side m-navbar__side--right">
        <slot name="right" />
      </view>
    </view>
  </view>
</template>

<style scoped>
.m-navbar {
  padding-top: var(--status-bar-height);
  background-color: var(--color-surface);
  border-bottom: 2rpx solid var(--color-border);
}

.m-navbar__bar {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 var(--space-page);
}

.m-navbar__side {
  display: flex;
  align-items: center;
  min-width: 96rpx;
}

.m-navbar__side--right {
  justify-content: flex-end;
}

.m-navbar__title {
  flex: 1;
  color: var(--color-text-primary);
  font-size: var(--font-title);
  text-align: center;
}

.m-navbar__action {
  color: var(--color-primary);
  font-size: var(--font-body);
}
</style>
