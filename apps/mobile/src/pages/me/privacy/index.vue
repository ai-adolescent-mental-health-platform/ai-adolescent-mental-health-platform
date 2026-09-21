<script setup lang="ts">
/**
 * 隐私设置。
 *
 * 五个可见性开关与 web-client 的 `privacy-page.tsx` 对齐，走共享层的
 * `getPrivacy` / `updatePrivacy`（后端 `GET|PUT /user/privacy`）。
 *
 * 本页不是 tab 页，按约定用 onLoad 取数（见 apps/mobile/AGENTS.md 第四节第 7 条）。
 */

import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api } from "@/lib/api";
import { ensureAuthenticated } from "@/router/guard";
import { ME_PRIVACY_PAGE } from "@/router/routes";

const PRIVACY_ITEMS = [
  { key: "allowViewArticles", label: "允许他人查看我的文章" },
  { key: "allowViewLikes", label: "允许他人查看我的点赞" },
  { key: "allowViewCollections", label: "允许他人查看我的收藏" },
  { key: "allowViewFollowings", label: "允许他人查看我的关注列表" },
  { key: "allowViewFans", label: "允许他人查看我的粉丝列表" },
];

const form = ref<Record<string, number>>({});
const loading = ref(true);
const saving = ref(false);
const notice = ref("");

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}

async function loadPrivacy(): Promise<void> {
  loading.value = true;
  notice.value = "";
  try {
    const data = await api.user.getPrivacy();
    const next: Record<string, number> = {};
    for (const item of PRIVACY_ITEMS) {
      next[item.key] = data[item.key] ? 1 : 0;
    }
    form.value = next;
  } catch (err) {
    notice.value = describeError(err, "隐私设置加载失败");
  } finally {
    loading.value = false;
  }
}

onLoad(() => {
  if (!ensureAuthenticated(ME_PRIVACY_PAGE)) return;
  void loadPrivacy();
});

function toggle(key: string): void {
  form.value = { ...form.value, [key]: form.value[key] ? 0 : 1 };
}

async function handleSave(): Promise<void> {
  saving.value = true;
  notice.value = "";
  try {
    await api.user.updatePrivacy(form.value);
    uni.showToast({ title: "保存成功", icon: "none" });
  } catch (err) {
    notice.value = describeError(err, "保存失败");
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="隐私设置" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="谁可以看到我的内容">
          <view v-if="loading" class="m-text-secondary">加载中...</view>
          <view
            v-for="item in PRIVACY_ITEMS"
            v-else
            :key="item.key"
            class="m-entry"
            @tap="toggle(item.key)"
          >
            <text class="m-entry__label">{{ item.label }}</text>
            <view class="m-switch" :class="{ 'm-switch--on': form[item.key] === 1 }">
              <view class="m-switch__knob" />
            </view>
          </view>
        </MCard>

        <MCard title="说明" muted>
          <text class="m-text-primary">
            开启后，其他用户可在首页查看对应内容；关闭后这些信息不会对他人展示。
          </text>
          <text class="m-text-secondary">隐私设置由账号维度保存，与设备无关。</text>
        </MCard>

        <text v-if="notice" class="m-error">{{ notice }}</text>

        <view class="m-actions">
          <MButton :loading="saving" @click="handleSave">保存设置</MButton>
        </view>
      </view>
    </view>
  </view>
</template>
