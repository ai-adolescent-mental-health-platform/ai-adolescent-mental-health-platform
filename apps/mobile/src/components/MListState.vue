<script setup lang="ts">
/**
 * 列表状态外壳：统一「加载中 / 失败可重试 / 空 / 内容 + 分页页脚」四态。
 *
 * 这一批 7 个列表页共用，保证四态与分页页脚的表现一致，页面只负责渲染条目本身。
 * 色值全部走 theme.css 变量层。
 */

import MButton from "@/components/MButton.vue";

withDefaults(
  defineProps<{
    loading?: boolean;
    error?: string;
    empty?: boolean;
    emptyText?: string;
    loadingMore?: boolean;
    hasMore?: boolean;
    total?: number;
    loadedCount?: number;
  }>(),
  {
    loading: false,
    error: "",
    empty: false,
    emptyText: "暂无数据",
    loadingMore: false,
    hasMore: false,
    total: 0,
    loadedCount: 0,
  },
);

defineEmits<{
  (event: "retry"): void;
  (event: "loadMore"): void;
}>();
</script>

<template>
  <view class="state">
    <view v-if="loading" class="state__block">
      <text class="m-text-secondary">加载中...</text>
    </view>

    <view v-else-if="error" class="state__block">
      <text class="m-error">{{ error }}</text>
      <view class="state__action">
        <MButton variant="secondary" :block="false" @click="$emit('retry')">重试</MButton>
      </view>
    </view>

    <view v-else-if="empty" class="state__block">
      <text class="m-text-secondary">{{ emptyText }}</text>
    </view>

    <template v-else>
      <slot />

      <view class="state__foot">
        <text class="m-text-secondary">已显示 {{ loadedCount }} / {{ total }} 条</text>
        <MButton
          v-if="hasMore"
          variant="secondary"
          size="small"
          :block="false"
          :loading="loadingMore"
          @click="$emit('loadMore')"
        >
          加载更多
        </MButton>
        <text v-else class="m-text-secondary">没有更多了</text>
      </view>
    </template>
  </view>
</template>

<style scoped>
.state__block {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding: 32rpx 0;
}

.state__action {
  display: flex;
}

.state__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding-top: 24rpx;
}
</style>
