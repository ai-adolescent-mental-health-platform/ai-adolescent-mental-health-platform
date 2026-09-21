<script setup lang="ts">
/**
 * 我的点赞。
 *
 * 与「我的收藏」共用 `MInteractionList`，本页只换取数接口与文案。
 * 非 tab 页，按约定用 onLoad 取数。
 */

import { onLoad } from "@dcloudio/uni-app";
import { ref } from "vue";
import MCard from "@/components/MCard.vue";
import MInteractionList from "@/components/MInteractionList.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api } from "@/lib/api";
import { ensureAuthenticated } from "@/router/guard";
import { ME_LIKES_PAGE } from "@/router/routes";

const ready = ref(false);

onLoad(() => {
  if (!ensureAuthenticated(ME_LIKES_PAGE)) return;
  ready.value = true;
});
</script>

<template>
  <view class="m-page">
    <MNavBar title="我的点赞" show-back />
    <view class="m-page__body">
      <MCard title="我点赞的内容">
        <MInteractionList
          :ready="ready"
          :fetcher="(page, size) => api.user.myLikes({ page, size })"
          empty-text="还没有点赞任何内容"
          fallback-error="点赞加载失败"
        />
      </MCard>
    </view>
  </view>
</template>
