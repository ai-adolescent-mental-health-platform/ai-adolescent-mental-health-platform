<script setup lang="ts">
/**
 * 登录页（骨架期版本）。
 *
 * 本页只打通「守卫拦截 -> 登录 -> 回跳原目标页」这条链路，**不发起任何网络请求**。
 * 真实登录接口（packages/api-client + createUniAdapter）在 issue #33 接入，
 * 见 docs/superpowers/plans/2026-09-20-mobile-api-adapter.md 的「完成后的接口速查」。
 */

import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MInput from "@/components/MInput.vue";
import MNavBar from "@/components/MNavBar.vue";
import { saveSession } from "@/lib/session";
import { HOME_PAGE } from "@/router/routes";
import { navigateAfterLogin } from "@/router/guard";
import { safeRedirect } from "@/lib/safe-redirect";

const account = ref("");
const password = ref("");
const error = ref("");
const submitting = ref(false);
const redirectTarget = ref(HOME_PAGE);

onLoad((query) => {
  const raw = typeof query?.redirect === "string" ? query.redirect : "";
  redirectTarget.value = safeRedirect(raw, HOME_PAGE);
});

function handleSubmit(): void {
  error.value = "";
  if (!account.value.trim()) {
    error.value = "请输入账号";
    return;
  }
  if (!password.value) {
    error.value = "请输入密码";
    return;
  }

  submitting.value = true;
  // TODO(#33): 在此调用真实登录接口，用返回的 token 与 user 走 saveSession。
  // 骨架期写入的是一条本地占位会话（不来自任何后端），仅用于验证守卫与回跳链路。
  saveSession("skeleton-placeholder-token", { nickname: account.value.trim() });
  submitting.value = false;

  navigateAfterLogin(redirectTarget.value);
}

function goRegister(): void {
  uni.navigateTo({ url: "/pages/register/index" });
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="登录" />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="欢迎回来" muted>
          <text class="m-text-primary">登录后即可使用问答与心理咨询。</text>
          <text class="m-text-secondary">当前为工程骨架版本，不会向服务端发起请求。</text>
        </MCard>

        <MInput v-model="account" label="账号" placeholder="手机号或邮箱" />
        <MInput v-model="password" label="密码" placeholder="请输入密码" password :error="error" />

        <view class="m-actions">
          <MButton :loading="submitting" @click="handleSubmit">登录</MButton>
          <MButton variant="ghost" @click="goRegister">没有账号，去注册</MButton>
        </view>
      </view>
    </view>
  </view>
</template>
