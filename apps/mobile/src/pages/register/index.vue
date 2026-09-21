<script setup lang="ts">
/**
 * 注册页（骨架期版本）。
 *
 * 与登录页同理：只落 UI 与校验，不发起网络请求。
 * 真实注册接口在 issue #33 起接入。
 */

import { ref } from "vue";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MInput from "@/components/MInput.vue";
import MNavBar from "@/components/MNavBar.vue";

const account = ref("");
const password = ref("");
const confirm = ref("");
const error = ref("");

function handleSubmit(): void {
  error.value = "";
  if (!account.value.trim()) {
    error.value = "请输入账号";
    return;
  }
  if (password.value.length < 6) {
    error.value = "密码至少 6 位";
    return;
  }
  if (password.value !== confirm.value) {
    error.value = "两次输入的密码不一致";
    return;
  }
  // TODO(#33): 接入真实注册接口。
  uni.showToast({ title: "注册接口待接入", icon: "none" });
}

function goLogin(): void {
  uni.navigateBack();
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="注册" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="创建账号">
          <text class="m-text-secondary">注册接口在 issue #33 接入，本页当前仅校验输入格式。</text>
        </MCard>

        <MInput v-model="account" label="账号" placeholder="手机号或邮箱" />
        <MInput v-model="password" label="密码" placeholder="至少 6 位" password />
        <MInput v-model="confirm" label="确认密码" placeholder="再次输入密码" password :error="error" />

        <view class="m-actions">
          <MButton @click="handleSubmit">注册</MButton>
          <MButton variant="ghost" @click="goLogin">已有账号，去登录</MButton>
        </view>
      </view>
    </view>
  </view>
</template>
