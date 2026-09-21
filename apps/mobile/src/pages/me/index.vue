<script setup lang="ts">
/**
 * 菜单 3「个人中心」（骨架期版本）。
 *
 * 展示会话存储的实际内容并提供登出，用于验证会话四个操作
 * （读 token / 读 user / 保存会话 / 清除会话）确实可用。
 * 资料编辑、消息、订单等子页在 issue #33 起接入。
 */

import { onLoad } from "@dcloudio/uni-app";
import { ref } from "vue";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MNavBar from "@/components/MNavBar.vue";
import { clearSession, getStoredUser, getToken } from "@/lib/session";
import { ensureAuthenticated } from "@/router/guard";
import { LOGIN_PAGE } from "@/router/routes";

const ME_PAGE = "/pages/me/index";

const nickname = ref("未记录");
const tokenPreview = ref("无");

onLoad(() => {
  if (!ensureAuthenticated(ME_PAGE)) return;

  const user = getStoredUser() ?? {};
  const storedName = user.nickname;
  nickname.value = typeof storedName === "string" && storedName ? storedName : "未记录";

  const token = getToken();
  // 只展示前 6 位，避免把完整凭据渲染到界面上。
  tokenPreview.value = token ? `${token.slice(0, 6)}…（共 ${token.length} 位）` : "无";
});

function handleLogout(): void {
  clearSession();
  uni.reLaunch({ url: LOGIN_PAGE });
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="个人中心" />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="当前会话">
          <view class="m-field">
            <text class="m-field__label">昵称</text>
            <text class="m-field__value">{{ nickname }}</text>
          </view>
          <view class="m-field">
            <text class="m-field__label">token</text>
            <text class="m-field__value">{{ tokenPreview }}</text>
          </view>
        </MCard>

        <MCard title="说明" muted>
          <text class="m-text-primary">资料、消息与订单将在 issue #33 起接入。</text>
          <text class="m-text-secondary">会话以明文形式存放于本地存储，安全评审时重新评估。</text>
        </MCard>

        <view class="m-actions">
          <MButton variant="secondary" @click="handleLogout">退出登录</MButton>
        </view>
      </view>
    </view>
  </view>
</template>
