<script setup lang="ts">
/**
 * 我的文章。
 *
 * 接口：GET /article/user/list（分页）。共享层已映射为
 * { id, title, coverUrl, status, viewCount, likeCount, commentCount, createTime }。
 *
 * status 语义来自后端 `UserArticleServiceImpl` 的实际写入点（不是猜的）：
 *   publishArticle → 0；onlineArticle → 1；withdrawArticle / offlineArticle → 2
 * 即 0=待审核、1=已发布、2=已下架或已撤回。遇到未知取值时如实显示原始数字，不编造文案。
 *
 * 非 tab 页，按约定用 onLoad 取数。
 */

import { onLoad, onUnload } from "@dcloudio/uni-app";
import { ref } from "vue";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MListState from "@/components/MListState.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api } from "@/lib/api";
import { usePagedList } from "@/lib/use-paged-list";
import { ensureAuthenticated } from "@/router/guard";
import { ME_ARTICLES_PAGE, ME_PUBLISH_PAGE } from "@/router/routes";

type ArticleItem = {
  id: number;
  title: string;
  coverUrl: string;
  status: number;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  createTime: string;
};

/** 与后端写入点对应；未收录的取值走 fallback，不猜。 */
const STATUS_TONES: Record<number, string> = {
  0: "待审核",
  1: "已发布",
  2: "已下架或已撤回",
};

function statusLabel(status: number): string {
  return STATUS_TONES[status] ?? `状态 ${status}`;
}

const ready = ref(false);

const { items, loading, loadingMore, error, total, hasMore, isEmpty, loadFirstPage, loadMore, dispose } =
  usePagedList<ArticleItem>(
    async (page, size) => api.content.myArticles({ page, size }),
    { size: 10, fallbackError: "文章加载失败" },
  );

onUnload(() => {
  dispose();
});

onLoad(() => {
  if (!ensureAuthenticated(ME_ARTICLES_PAGE)) return;
  ready.value = true;
  void loadFirstPage();
});

function goPublish(): void {
  uni.navigateTo({ url: ME_PUBLISH_PAGE });
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="我的文章" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="说明" muted>
          <text class="m-text-primary">发布后的文章需经审核，通过后才会出现在社区。</text>
          <text class="m-text-secondary">本页显示全部状态的文章，含待审核与已下架。</text>
        </MCard>

        <MCard title="文章列表">
          <MListState
            :loading="loading"
            :error="error"
            :empty="isEmpty"
            empty-text="还没有发布过文章"
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
                :key="item.id"
                class="m-item"
                :class="{ 'm-item--last': index === items.length - 1 }"
              >
                <text class="m-item__title">{{ item.title || "未命名文章" }}</text>
                <view class="m-item__meta">
                  <text>{{ statusLabel(item.status) }}</text>
                  <text>{{ item.createTime || "时间未知" }}</text>
                </view>
                <view class="m-item__meta">
                  <text>浏览 {{ item.viewCount }}</text>
                  <text>点赞 {{ item.likeCount }}</text>
                  <text>评论 {{ item.commentCount }}</text>
                </view>
              </view>
            </view>
          </MListState>
        </MCard>

        <view class="m-actions">
          <MButton @click="goPublish">去发布文章</MButton>
        </view>
      </view>
    </view>
  </view>
</template>
