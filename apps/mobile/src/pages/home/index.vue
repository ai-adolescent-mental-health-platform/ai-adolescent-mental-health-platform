<script setup lang="ts">
/**
 * 菜单 1「问答」（骨架期占位）。
 *
 * 小艾（AI 咨询）的真实对话链路依赖流式接收方案在目标端验证通过（设计文档 6.1），
 * 不在本次骨架范围内。本页只做受保护页面的落地与导航验证。
 */

import { onLoad } from "@dcloudio/uni-app";
import MCard from "@/components/MCard.vue";
import MNavBar from "@/components/MNavBar.vue";
import { ensureAuthenticated } from "@/router/guard";
import { HOME_PAGE } from "@/router/routes";

// 页面级兜底：冷启动直达 / H5 直接输入 URL 时，全局导航拦截覆盖不到。
onLoad(() => {
  ensureAuthenticated(HOME_PAGE);
});
</script>

<template>
  <view class="m-page">
    <MNavBar title="问答" />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="小艾问答">
          <text class="m-text-primary">文字问答将在第 2 期接入。</text>
          <text class="m-text-secondary">本页当前用于验证受保护页面的登录拦截。</text>
        </MCard>
      </view>
    </view>
  </view>
</template>
