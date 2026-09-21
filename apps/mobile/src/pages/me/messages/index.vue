<script setup lang="ts">
/**
 * 我的消息（站内信）。
 *
 * 接口：GET /user/messages（分页）、PUT /user/messages/{id}/read、
 *       PUT /user/messages/read-all、GET /user/messages/unread-count。
 * 未读数来自后端 `unread-count`，**不是前端数出来的**；单条未读态来自 `SysMessage.isRead`。
 *
 * 共享层把 `message.list` 的 records 原样透出（`PageResult<unknown>`，未做映射），
 * 因此这里按后端 `SysMessage` 的字段就地收窄。
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
import { ME_MESSAGES_PAGE } from "@/router/routes";

type MessageItem = {
  id: number;
  title: string;
  content: string;
  fromNickname: string;
  isRead: boolean;
  createTime: string;
};

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function formatTime(value: unknown): string {
  const raw = typeof value === "string" ? value : "";
  return raw ? raw.replace("T", " ").slice(0, 16) : "";
}

function mapMessage(raw: unknown): MessageItem {
  const d = asRecord(raw);
  return {
    id: typeof d.id === "number" ? d.id : 0,
    title: typeof d.title === "string" && d.title ? d.title : "站内信",
    content: typeof d.content === "string" ? d.content : "",
    fromNickname: typeof d.fromUserNickname === "string" ? d.fromUserNickname : "",
    // 后端 SysMessage.isRead 为 Integer：1 已读 / 0 未读
    isRead: Number(d.isRead) === 1,
    createTime: formatTime(d.createTime),
  };
}

const { items, loading, loadingMore, error, total, hasMore, isEmpty, loadFirstPage, loadMore, dispose } =
  usePagedList<MessageItem>(
    async (page, size) => {
      const result = await api.message.list({ page, size });
      return { ...result, records: (result.records ?? []).map(mapMessage) };
    },
    { size: 10, fallbackError: "消息加载失败" },
  );

onUnload(() => {
  // 卸载时作废在飞请求（代际校验），避免响应回来后写已销毁页面的状态。
  dispose();
});

const unread = ref(0);
const unreadLoaded = ref(false);
const marking = ref(false);
const notice = ref("");
const ready = ref(false);

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}

async function loadUnread(): Promise<void> {
  try {
    const count = await api.message.unreadCount();
    unread.value = typeof count === "number" ? count : 0;
  } catch {
    // 未读数失败不影响列表本身，保持 0 并标记为未取到
    unread.value = 0;
  } finally {
    unreadLoaded.value = true;
  }
}

onLoad(() => {
  if (!ensureAuthenticated(ME_MESSAGES_PAGE)) return;
  ready.value = true;
  void loadFirstPage();
  void loadUnread();
});

async function markOne(item: MessageItem): Promise<void> {
  if (item.isRead) return;
  notice.value = "";
  try {
    await api.message.markRead(item.id);
    items.value = items.value.map((one) => (one.id === item.id ? { ...one, isRead: true } : one));
    unread.value = Math.max(0, unread.value - 1);
  } catch (err) {
    notice.value = describeError(err, "标记已读失败");
  }
}

async function markAll(): Promise<void> {
  if (unread.value === 0) return;
  marking.value = true;
  notice.value = "";
  try {
    await api.message.markAllRead();
    items.value = items.value.map((one) => ({ ...one, isRead: true }));
    unread.value = 0;
    uni.showToast({ title: "已全部标记为已读", icon: "none" });
  } catch (err) {
    notice.value = describeError(err, "全部标记已读失败");
  } finally {
    marking.value = false;
  }
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="我的消息" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="未读">
          <view class="m-field">
            <text class="m-field__label">未读消息</text>
            <text class="m-field__value">{{ unreadLoaded ? `${unread} 条` : "读取中..." }}</text>
          </view>
          <view class="m-actions">
            <MButton
              variant="secondary"
              :disabled="unread === 0"
              :loading="marking"
              @click="markAll"
            >
              全部标记为已读
            </MButton>
          </view>
        </MCard>

        <text v-if="notice" class="m-error">{{ notice }}</text>

        <MCard title="消息列表">
          <MListState
            :loading="loading"
            :error="error"
            :empty="isEmpty"
            empty-text="暂时没有消息"
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
                @tap="markOne(item)"
              >
                <view class="m-item__head">
                  <text class="m-item__title">{{ item.title }}</text>
                  <view v-if="!item.isRead" class="m-dot" />
                </view>
                <text class="m-item__body">{{ item.content }}</text>
                <view class="m-item__meta">
                  <text>{{ item.fromNickname || "系统" }}</text>
                  <text>{{ item.createTime || "时间未知" }}</text>
                </view>
                <text v-if="!item.isRead" class="m-text-secondary">点击标记为已读</text>
              </view>
            </view>
          </MListState>
        </MCard>
      </view>
    </view>
  </view>
</template>
