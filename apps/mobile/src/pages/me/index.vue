<script setup lang="ts">
/**
 * 菜单 3「个人中心」（本步只做会话与退出登录的收口）。
 *
 * 进入时用 `api.user.getUserInfo()` 刷新一次资料：这既让个人中心展示真实数据，
 * 也是 401 路径的真实触发点——token 失效时由共享层的 onUnauthorized 统一清会话并回登录页，
 * 页面内**不写**任何 `status === 401` 判断。
 * 资料编辑、消息、订单等子页在后续 issue 接入。
 */

import { onShow } from "@dcloudio/uni-app";
import { ref } from "vue";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api } from "@/lib/api";
import { clearSession, getStoredUser, getToken } from "@/lib/session";
import { ensureAuthenticated } from "@/router/guard";
import { LOGIN_PAGE } from "@/router/routes";

const ME_PAGE = "/pages/me/index";

const nickname = ref("未记录");
const username = ref("未记录");
const email = ref("未记录");
const tokenPreview = ref("无");
const refreshing = ref(false);
const notice = ref("");

function readFromSession(): void {
  const user = getStoredUser() ?? {};
  nickname.value = typeof user.nickname === "string" && user.nickname ? user.nickname : "未记录";
  username.value = typeof user.username === "string" && user.username ? user.username : "未记录";
  email.value = typeof user.email === "string" && user.email ? user.email : "未记录";
}

/*
 * 首版刻意接受「每次 onShow 都请求一次 /user/info」：tab 页的刷新时机因此简单可预测，
 * 且个人中心的数据需要真实反映账号状态（401 也依赖这次请求触发）。
 * 暂不做缓存或节流；等真实后端联调、拿到实际调用频次后再评估是否加缓存。
 */
async function refreshProfile(): Promise<void> {
  refreshing.value = true;
  notice.value = "";
  try {
    const profile = await api.user.getUserInfo();
    nickname.value = profile.nickname || nickname.value;
    username.value = profile.username ?? username.value;
    email.value = profile.email ?? email.value;
  } catch (err) {
    // 401 已由共享层接管（清会话 + 回登录页），这里只呈现其余失败的提示。
    notice.value = err instanceof Error && err.message ? err.message : "资料刷新失败";
  } finally {
    refreshing.value = false;
  }
}

function updateTokenPreview(): void {
  const token = getToken();
  // 只展示前 6 位，避免把完整凭据渲染到界面上。
  tokenPreview.value = token ? `${token.slice(0, 6)}…（共 ${token.length} 位）` : "无";
}

/*
 * 用 onShow 而不是 onLoad：本页是 tabBar 页面，uni-app 会把 tab 页实例保留复用，
 * 再次切回时 onLoad 不再触发。若只在 onLoad 读会话，会出现「已登录但页面仍显示未记录」
 * 的陈旧状态。实测复现过该问题（登录后切回本页仍显示初始占位值）。
 */
onShow(() => {
  if (!ensureAuthenticated(ME_PAGE)) return;

  readFromSession();
  updateTokenPreview();
  void refreshProfile();
});

function handleLogout(): void {
  // 与 web-client 一致：退出登录只清本地会话，不调用后端 /user/logout。
  clearSession();
  uni.reLaunch({ url: LOGIN_PAGE });
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="个人中心" />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="账号">
          <view class="m-field">
            <text class="m-field__label">昵称</text>
            <text class="m-field__value">{{ nickname }}</text>
          </view>
          <view class="m-field">
            <text class="m-field__label">用户名</text>
            <text class="m-field__value">{{ username }}</text>
          </view>
          <view class="m-field">
            <text class="m-field__label">邮箱</text>
            <text class="m-field__value">{{ email }}</text>
          </view>
          <view class="m-field">
            <text class="m-field__label">token</text>
            <text class="m-field__value">{{ tokenPreview }}</text>
          </view>
        </MCard>

        <text v-if="notice" class="m-error">{{ notice }}</text>

        <MCard title="说明" muted>
          <text class="m-text-primary">资料编辑、消息与订单在后续 issue 接入。</text>
          <text class="m-text-secondary">会话以明文形式存放于本地存储，安全评审时重新评估。</text>
        </MCard>

        <view class="m-actions">
          <MButton variant="secondary" :loading="refreshing" @click="refreshProfile">刷新资料</MButton>
          <MButton variant="ghost" @click="handleLogout">退出登录</MButton>
        </view>
      </view>
    </view>
  </view>
</template>
