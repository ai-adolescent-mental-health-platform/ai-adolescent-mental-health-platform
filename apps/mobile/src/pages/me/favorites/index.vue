<script setup lang="ts">
/**
 * 我的收藏。
 *
 * 取数与分页由 `MInteractionList` 内部处理，本页只负责登录校验、标题与「注入哪个接口」。
 * 非 tab 页，按约定用 onLoad 取数（apps/mobile/AGENTS.md 第四节第 7 条）。
 */

import { onLoad } from "@dcloudio/uni-app";
import { ref } from "vue";
import MCard from "@/components/MCard.vue";
import MInteractionList from "@/components/MInteractionList.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api } from "@/lib/api";
import { ensureAuthenticated } from "@/router/guard";
import { ME_FAVORITES_PAGE } from "@/router/routes";

const ready = ref(false);

onLoad(() => {
  if (!ensureAuthenticated(ME_FAVORITES_PAGE)) return;
  ready.value = true;
});
</script>

<template>
  <view class="m-page">
    <MNavBar title="我的收藏" show-back />
    <view class="m-page__body">
      <MCard title="收藏的内容">
        <MInteractionList
          :ready="ready"
          :fetcher="(page, size) => api.user.myCollections({ page, size })"
          empty-text="还没有收藏任何内容"
          fallback-error="收藏加载失败"
        />
      </MCard>
    </view>
  </view>
</template>
