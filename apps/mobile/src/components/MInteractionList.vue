<script setup lang="ts">
/**
 * 互动条目列表（收藏 / 点赞共用）。
 *
 * 这两个页面的条目结构与分页行为完全一致，只有取数接口与空态文案不同，
 * 因此把「渲染 + 分页 + 四态」收在这里，页面只负责注入取数函数——避免两份几乎相同的实现各自漂移。
 *
 * 用 `ready` 显式启动首屏加载，而不是让页面在 onLoad 里通过 ref 调用子组件方法：
 * onLoad 触发时子组件还没挂载，ref 为 null，那种写法会静默地什么都不做。
 */

import { watch } from "vue";
import type { InteractionItem } from "@ai-adolescent-mental-health/domain";
import MListState from "@/components/MListState.vue";
import { usePagedList, type PageResultLike } from "@/lib/use-paged-list";

const props = withDefaults(
  defineProps<{
    /** 取数函数：由页面注入，组件内部只管分页与渲染 */
    fetcher: (page: number, size: number) => Promise<PageResultLike<InteractionItem>>;
    /** 页面通过登录校验后置为 true，组件收到后开始首屏加载 */
    ready?: boolean;
    emptyText?: string;
    fallbackError?: string;
  }>(),
  {
    ready: false,
    emptyText: "暂无数据",
    fallbackError: "加载失败，请重试",
  },
);

const { items, loading, loadingMore, error, total, hasMore, isEmpty, loadFirstPage, loadMore } =
  usePagedList<InteractionItem>(props.fetcher, {
    size: 10,
    fallbackError: props.fallbackError,
  });

watch(
  () => props.ready,
  (ready) => {
    if (ready) void loadFirstPage();
  },
  { immediate: true },
);
</script>

<template>
  <MListState
    :loading="loading"
    :error="error"
    :empty="isEmpty"
    :empty-text="emptyText"
    :loading-more="loadingMore"
    :has-more="hasMore"
    :total="total"
    :loaded-count="items.length"
    @retry="loadFirstPage"
    @load-more="loadMore"
  >
    <view class="m-list">
      <view
        v-for="(item, index) in items"
        :key="`${item.articleId}-${index}`"
        class="m-item"
        :class="{ 'm-item--last': index === items.length - 1 }"
      >
        <text class="m-item__title">{{ item.articleTitle || "未命名内容" }}</text>
        <text v-if="item.authorNickname" class="m-item__body">作者：{{ item.authorNickname }}</text>
        <view class="m-item__meta">
          <text>{{ item.createTime || "时间未知" }}</text>
        </view>
      </view>
    </view>
  </MListState>
</template>
