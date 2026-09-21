<script setup lang="ts">
/**
 * 意见反馈：提交 + 我的反馈记录。
 *
 * 接口：POST /feedback/platform（提交）、GET /feedback/platform/my（我的记录）。
 *
 * 关于记录列表的取数方式：共享层的 `api.feedback.platform()` 把分页参数写死成
 * `page:1, size:100`，**没有暴露 page/size**；而后端 `FeedbackController.myFeedback`
 * 本身是支持 `page`/`size` 的。为了满足"分页行为必须正确"，这里直接用统一 api 层导出的
 * `httpClient` 调同一路径并传分页参数——仍然是走 api 层（不是 uni.request / fetch），
 * 但绕开了共享层未暴露参数的缺口。待办：应在 packages/api-client 侧给该方法补上分页参数。
 *
 * status 文案取自 admin-portal `FeedbackManager.tsx` 的 `getStatusLabel`
 * （0 待处理 / 1 待解决 / 2 已解决 / 3 已删除）；后端 `FeedbackController` 的注释写作「0-已反馈」，
 * 与管理员端的文案不一致，这里采用管理员端的映射。
 *
 * 非 tab 页，按约定用 onLoad 取数。
 */

import { onLoad } from "@dcloudio/uni-app";
import { ref } from "vue";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MListState from "@/components/MListState.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api, httpClient } from "@/lib/api";
import { usePagedList, type PageResultLike } from "@/lib/use-paged-list";
import { ensureAuthenticated } from "@/router/guard";
import { ME_FEEDBACK_PAGE } from "@/router/routes";

type FeedbackItem = {
  id: number;
  content: string;
  status: number;
  createTime: string;
};

const STATUS_LABELS: Record<number, string> = {
  0: "待处理",
  1: "待解决",
  2: "已解决",
  3: "已删除",
};

function statusLabel(status: number): string {
  return STATUS_LABELS[status] ?? `状态 ${status}`;
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function formatTime(value: unknown): string {
  const raw = typeof value === "string" ? value : "";
  return raw ? raw.replace("T", " ").slice(0, 16) : "";
}

function mapFeedback(raw: unknown): FeedbackItem {
  const d = asRecord(raw);
  return {
    id: typeof d.id === "number" ? d.id : 0,
    content: typeof d.content === "string" ? d.content : "",
    status: typeof d.status === "number" ? d.status : 0,
    createTime: formatTime(d.createTime),
  };
}

const content = ref("");
const submitting = ref(false);
const formError = ref("");
const notice = ref("");

const { items, loading, loadingMore, error, total, hasMore, isEmpty, loadFirstPage, loadMore } =
  usePagedList<FeedbackItem>(
    async (page, size) => {
      const result = await httpClient.get<PageResultLike<unknown>>("/feedback/platform/my", {
        query: { page, size },
      });
      return { ...result, records: (result.records ?? []).map(mapFeedback) };
    },
    { size: 10, fallbackError: "反馈记录加载失败" },
  );

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}

function onContentInput(event: unknown): void {
  const detail = (event as { detail?: { value?: string } } | undefined)?.detail;
  content.value = typeof detail?.value === "string" ? detail.value : "";
}

onLoad(() => {
  if (!ensureAuthenticated(ME_FEEDBACK_PAGE)) return;
  void loadFirstPage();
});

async function handleSubmit(): Promise<void> {
  formError.value = "";
  notice.value = "";
  const trimmed = content.value.trim();
  if (!trimmed) {
    formError.value = "请填写反馈内容";
    return;
  }

  submitting.value = true;
  try {
    await api.feedback.submitPlatform({ content: trimmed });
    content.value = "";
    uni.showToast({ title: "提交成功", icon: "none" });
    // 重新拉第一页，让刚提交的记录出现在列表顶部。
    await loadFirstPage();
  } catch (err) {
    formError.value = describeError(err, "提交失败，请重试");
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="意见反馈" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="我要反馈">
          <text class="m-text-secondary">
            可以写下使用中遇到的问题或建议，我们会按提交时间处理。
          </text>
          <view class="editor">
            <textarea
              class="editor__input"
              :value="content"
              placeholder="请输入反馈内容"
              @input="onContentInput"
            />
            <text class="m-text-secondary">{{ content.length }} 字</text>
          </view>

          <text v-if="formError" class="m-error">{{ formError }}</text>

          <view class="m-actions">
            <MButton :loading="submitting" @click="handleSubmit">提交反馈</MButton>
          </view>
        </MCard>

        <text v-if="notice" class="m-text-primary">{{ notice }}</text>

        <MCard title="我的反馈记录">
          <MListState
            :loading="loading"
            :error="error"
            :empty="isEmpty"
            empty-text="还没有提交过反馈"
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
                <text class="m-item__body">{{ item.content }}</text>
                <view class="m-item__meta">
                  <text>{{ statusLabel(item.status) }}</text>
                  <text>{{ item.createTime || "时间未知" }}</text>
                </view>
              </view>
            </view>
          </MListState>
        </MCard>
      </view>
    </view>
  </view>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: var(--space-gap);
}

.editor__input {
  box-sizing: border-box;
  width: 100%;
  min-height: 240rpx;
  padding: 20rpx;
  border: 2rpx solid var(--color-border-control);
  border-radius: var(--radius-control);
  background-color: var(--color-surface);
  color: var(--color-text-primary);
  font-size: var(--font-body);
  line-height: 1.6;
}
</style>
