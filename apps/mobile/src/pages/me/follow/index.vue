<script setup lang="ts">
/**
 * 我的关注 / 我的粉丝（一个页面两个 tab，各自独立分页）。
 *
 * 接口：GET /user/followings、GET /user/followers（均支持 page/size），
 *       POST /user/follow/{userId}、DELETE /user/follow/{userId}。
 * 共享层顶层键是 `follow`（`api.follow.*`），不是 `api.user.follow.*`。
 *
 * 两个列表各自持有分页状态，切换 tab 时首次进入才拉取，避免把两个列表的数据混在一起。
 * 非 tab 页（指不在一级 tabBar 上），按约定用 onLoad 取数。
 */

import { computed, ref } from "vue";
import { onLoad, onUnload } from "@dcloudio/uni-app";
import type { FollowUser } from "@ai-adolescent-mental-health/domain";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MListState from "@/components/MListState.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api } from "@/lib/api";
import { usePagedList } from "@/lib/use-paged-list";
import { ensureAuthenticated } from "@/router/guard";
import { ME_FOLLOW_PAGE } from "@/router/routes";

type TabKey = "followings" | "followers";

const TABS: { key: TabKey; label: string }[] = [
  { key: "followings", label: "我的关注" },
  { key: "followers", label: "我的粉丝" },
];

const tab = ref<TabKey>("followings");
const notice = ref("");
const actingId = ref(0);

const followings = usePagedList<FollowUser>(
  (page, size) => api.follow.myFollowings({ page, size }),
  { size: 20, fallbackError: "关注列表加载失败" },
);

const followers = usePagedList<FollowUser>(
  (page, size) => api.follow.myFollowers({ page, size }),
  { size: 20, fallbackError: "粉丝列表加载失败" },
);

onUnload(() => {
  // 两个列表各自作废在飞请求：切走页面后返回的响应不再写状态。
  followings.dispose();
  followers.dispose();
});

/** 每个 tab 是否已经拉过首屏；切换时只对未拉取过的 tab 发请求。 */
const started: Record<TabKey, boolean> = { followings: false, followers: false };

const active = computed(() => (tab.value === "followings" ? followings : followers));
const emptyText = computed(() =>
  tab.value === "followings" ? "还没有关注任何人" : "还没有粉丝",
);

onLoad(() => {
  if (!ensureAuthenticated(ME_FOLLOW_PAGE)) return;
  started.followings = true;
  void followings.loadFirstPage();
});

function switchTab(key: TabKey): void {
  if (tab.value === key) return;
  tab.value = key;
  notice.value = "";
  if (!started[key]) {
    started[key] = true;
    void (key === "followings" ? followings : followers).loadFirstPage();
  }
}

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}

async function toggleFollow(user: FollowUser): Promise<void> {
  if (actingId.value) return;
  actingId.value = user.userId;
  notice.value = "";
  try {
    if (user.isFollowing) {
      await api.follow.unfollow(user.userId);
    } else {
      await api.follow.follow(user.userId);
    }
    const flip = (list: FollowUser[]): FollowUser[] =>
      list.map((one) =>
        one.userId === user.userId ? { ...one, isFollowing: !user.isFollowing } : one,
      );
    followings.items.value = flip(followings.items.value);
    followers.items.value = flip(followers.items.value);
    uni.showToast({ title: user.isFollowing ? "已取消关注" : "已关注", icon: "none" });
  } catch (err) {
    notice.value = describeError(err, user.isFollowing ? "取消关注失败" : "关注失败");
  } finally {
    actingId.value = 0;
  }
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="我的关注" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <view class="m-choice-row">
          <view
            v-for="item in TABS"
            :key="item.key"
            class="m-choice"
            :class="{ 'm-choice--active': tab === item.key }"
            @tap="switchTab(item.key)"
          >
            <text class="m-choice__label">{{ item.label }}</text>
          </view>
        </view>

        <text v-if="notice" class="m-error">{{ notice }}</text>

        <MCard :title="tab === 'followings' ? '我关注的人' : '关注我的人'">
          <MListState
            :loading="active.loading.value"
            :error="active.error.value"
            :empty="active.isEmpty.value"
            :empty-text="emptyText"
            :loading-more="active.loadingMore.value"
            :has-more="active.hasMore.value"
            :total="active.total.value"
            :loaded-count="active.items.value.length"
            @retry="active.loadFirstPage"
            @load-more="active.loadMore"
          >
            <view class="m-list">
              <view
                v-for="(user, index) in active.items.value"
                :key="user.userId"
                class="m-item"
                :class="{ 'm-item--last': index === active.items.value.length - 1 }"
              >
                <view class="m-item__head">
                  <text class="m-item__title">{{ user.nickname || "未设置昵称" }}</text>
                </view>
                <text v-if="user.signature" class="m-item__body">{{ user.signature }}</text>
                <view class="m-item__actions">
                  <MButton
                    variant="secondary"
                    size="small"
                    :block="false"
                    :loading="actingId === user.userId"
                    @click="toggleFollow(user)"
                  >
                    {{ user.isFollowing ? "取消关注" : "关注" }}
                  </MButton>
                </view>
              </view>
            </view>
          </MListState>
        </MCard>
      </view>
    </view>
  </view>
</template>
