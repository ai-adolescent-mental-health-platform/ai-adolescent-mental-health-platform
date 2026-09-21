<script setup lang="ts">
/**
 * 协议与政策页。
 *
 * 正文来自 `src/config/legal.ts`（后端无对应接口，见该文件顶部说明），页面只负责呈现。
 * 本页**不要求登录**：注册页的两个协议链接需要用户在注册前即可查阅。
 * 入口参数：`?tab=terms|privacy`，未匹配时回落到用户服务协议。
 */

import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import MCard from "@/components/MCard.vue";
import MNavBar from "@/components/MNavBar.vue";
import { LEGAL_DOCUMENTS, getLegalDocument, type LegalTab } from "@/config/legal";

const activeTab = ref<LegalTab>("terms");
const currentDoc = computed(() => getLegalDocument(activeTab.value));

onLoad((query) => {
  const tab = typeof query?.tab === "string" ? query.tab : "";
  if (tab === "terms" || tab === "privacy") {
    activeTab.value = tab;
  }
});

function switchTab(tab: LegalTab): void {
  activeTab.value = tab;
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="协议与政策" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <view class="m-choice-row">
          <view
            v-for="doc in LEGAL_DOCUMENTS"
            :key="doc.tab"
            class="m-choice"
            :class="{ 'm-choice--active': activeTab === doc.tab }"
            @tap="switchTab(doc.tab)"
          >
            <text class="m-choice__label">{{ doc.shortLabel }}</text>
          </view>
        </view>

        <MCard :title="currentDoc.title">
          <view
            v-for="section in currentDoc.sections"
            :key="section.heading"
            class="section"
          >
            <text class="section__heading">{{ section.heading }}</text>
            <text class="section__body">{{ section.body }}</text>
          </view>
        </MCard>

        <text class="m-text-secondary">
          本页为当前生效版本；如有更新，以应用内公告与更新后的本页内容为准。
        </text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.section {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  margin-bottom: 32rpx;
}

.section__heading {
  color: var(--color-text-primary);
  font-size: var(--font-body);
  line-height: 1.5;
}

.section__body {
  color: var(--color-text-secondary);
  font-size: var(--font-caption);
  line-height: 1.7;
}
</style>
