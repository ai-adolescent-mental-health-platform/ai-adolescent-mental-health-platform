<script setup lang="ts">
/**
 * 菜单 2「心理咨询」（骨架期占位）。
 *
 * 咨询师目录 / 详情 / 图文咨询 / 预约下单均不在本次骨架范围内（设计文档第 7 节）。
 */

import { onLoad } from "@dcloudio/uni-app";
import MCard from "@/components/MCard.vue";
import MNavBar from "@/components/MNavBar.vue";
import { ensureAuthenticated } from "@/router/guard";

const CONSULTATION_PAGE = "/pages/consultation/index";

onLoad(() => {
  ensureAuthenticated(CONSULTATION_PAGE);
});
</script>

<template>
  <view class="m-page">
    <MNavBar title="心理咨询" />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="咨询师目录">
          <text class="m-text-primary">咨询师目录与预约将在第 3 期接入。</text>
          <text class="m-text-secondary">本页当前用于验证受保护页面的登录拦截。</text>
        </MCard>
      </view>
    </view>
  </view>
</template>
