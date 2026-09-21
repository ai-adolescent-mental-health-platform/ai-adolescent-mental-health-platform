<script setup lang="ts">
/**
 * 基础卡片容器。
 * 底色用 var(--color-surface)，文字继承全局 var(--color-text-primary)，
 * 保证正文对比度 16.10:1（见 styles/theme.css）。
 */

withDefaults(
  defineProps<{
    title?: string;
    /** 是否使用粉彩底：粉彩底只允许配深色字 */
    muted?: boolean;
  }>(),
  {
    title: "",
    muted: false,
  },
);
</script>

<template>
  <view class="m-card" :class="{ 'm-card--muted': muted }">
    <text v-if="title" class="m-card__title">{{ title }}</text>
    <view class="m-card__body">
      <slot />
    </view>
    <view v-if="$slots.footer" class="m-card__footer">
      <slot name="footer" />
    </view>
  </view>
</template>

<style scoped>
.m-card {
  padding: var(--space-gap);
  border: 2rpx solid var(--color-border);
  border-radius: var(--radius-card);
  background-color: var(--color-surface);
}

.m-card--muted {
  background-color: var(--color-surface-muted);
}

.m-card__title {
  display: block;
  margin-bottom: 16rpx;
  color: var(--color-text-primary);
  font-size: var(--font-title);
  line-height: 1.4;
}

.m-card__body {
  color: var(--color-text-primary);
  font-size: var(--font-body);
}

.m-card__footer {
  margin-top: 16rpx;
  color: var(--color-text-secondary);
  font-size: var(--font-caption);
}
</style>
