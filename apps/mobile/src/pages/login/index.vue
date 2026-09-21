<script setup lang="ts">
/**
 * 登录页。
 *
 * 三种登录方式与 web-client 一致：账号密码 / 邮箱验证码 / 邮箱密码；
 * 字段名与校验规则对齐 web-client（规则见 lib/validation.ts 的说明）。
 * 回跳目标经 safe-redirect.ts 校验，登录成功后回到原目标页。
 */

import { onUnmounted, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MInput from "@/components/MInput.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api } from "@/lib/api";
import { safeRedirect } from "@/lib/safe-redirect";
import { saveSession } from "@/lib/session";
import { isValidEmail } from "@/lib/validation";
import { navigateAfterLogin } from "@/router/guard";
import { HOME_PAGE } from "@/router/routes";

type LoginTab = "account" | "emailCode" | "emailPwd";

const TAB_OPTIONS: { value: LoginTab; label: string }[] = [
  { value: "account", label: "账号登录" },
  { value: "emailCode", label: "邮箱验证码" },
  { value: "emailPwd", label: "邮箱密码" },
];

const CODE_COUNTDOWN_SECONDS = 60;

const tab = ref<LoginTab>("account");
const username = ref("");
const password = ref("");
const email = ref("");
const code = ref("");
const rememberMe = ref(false);
const submitting = ref(false);
const sendingCode = ref(false);
const error = ref("");
const countdown = ref(0);
const redirectTarget = ref(HOME_PAGE);

let timer: ReturnType<typeof setInterval> | undefined;

function stopTimer(): void {
  if (timer) {
    clearInterval(timer);
    timer = undefined;
  }
}

function startCountdown(): void {
  countdown.value = CODE_COUNTDOWN_SECONDS;
  stopTimer();
  timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) stopTimer();
  }, 1000);
}

onLoad((query) => {
  // 回跳目标必须经 safe-redirect 校验，不得绕过。
  const raw = typeof query?.redirect === "string" ? query.redirect : "";
  redirectTarget.value = safeRedirect(raw, HOME_PAGE);
});

onUnmounted(stopTimer);

function switchTab(next: LoginTab): void {
  tab.value = next;
  error.value = "";
}

function toggleRemember(): void {
  rememberMe.value = !rememberMe.value;
}

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}

async function handleSendCode(): Promise<void> {
  error.value = "";
  if (!email.value) {
    error.value = "请先输入邮箱";
    return;
  }
  if (!isValidEmail(email.value)) {
    error.value = "邮箱格式不正确";
    return;
  }

  sendingCode.value = true;
  try {
    await api.user.sendEmailCode(email.value, "login");
    startCountdown();
    uni.showToast({ title: "验证码已发送", icon: "none" });
  } catch (err) {
    error.value = describeError(err, "发送失败，请重试");
  } finally {
    sendingCode.value = false;
  }
}

async function handleLogin(): Promise<void> {
  error.value = "";

  if (tab.value === "account") {
    if (!username.value || !password.value) {
      error.value = "请填写账号和密码";
      return;
    }
  } else if (tab.value === "emailCode") {
    if (!email.value || !code.value) {
      error.value = "请填写邮箱和验证码";
      return;
    }
    if (!isValidEmail(email.value)) {
      error.value = "邮箱格式不正确";
      return;
    }
  } else {
    if (!email.value || !password.value) {
      error.value = "请填写邮箱和密码";
      return;
    }
    if (!isValidEmail(email.value)) {
      error.value = "邮箱格式不正确";
      return;
    }
  }

  submitting.value = true;
  try {
    const result =
      tab.value === "account"
        ? await api.user.loginByUsernamePassword(username.value, password.value, rememberMe.value)
        : tab.value === "emailCode"
          ? await api.user.loginByEmailCode(email.value, code.value)
          : await api.user.loginByEmailPassword(email.value, password.value, rememberMe.value);

    saveSession(result.token, result.user);
    uni.showToast({ title: "登录成功", icon: "none" });
    navigateAfterLogin(redirectTarget.value);
  } catch (err) {
    error.value = describeError(err, "登录失败，请检查账号信息");
  } finally {
    submitting.value = false;
  }
}

function goRegister(): void {
  uni.navigateTo({ url: "/pages/register/index" });
}

function goForgotPassword(): void {
  uni.navigateTo({ url: "/pages/forgot-password/index" });
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="登录" />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="登录心愈智联" muted>
          <text class="m-text-secondary">青少年心理健康 AI 平台</text>
        </MCard>

        <view class="seg">
          <view
            v-for="option in TAB_OPTIONS"
            :key="option.value"
            class="seg__item"
            :class="{ 'seg__item--active': tab === option.value }"
            @tap="switchTab(option.value)"
          >
            <text class="seg__label">{{ option.label }}</text>
          </view>
        </view>

        <template v-if="tab === 'account'">
          <MInput v-model="username" label="用户名" placeholder="请输入用户名" />
          <MInput v-model="password" label="密码" placeholder="请输入密码" password />
        </template>

        <template v-else-if="tab === 'emailCode'">
          <MInput v-model="email" label="邮箱" placeholder="请输入邮箱地址" />
          <view class="m-code-row">
            <view class="m-code-row__field">
              <MInput v-model="code" label="验证码" placeholder="6位验证码" />
            </view>
            <MButton
              variant="secondary"
              :block="false"
              :disabled="countdown > 0"
              :loading="sendingCode"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}秒` : "获取验证码" }}
            </MButton>
          </view>
        </template>

        <template v-else>
          <MInput v-model="email" label="邮箱" placeholder="请输入邮箱地址" />
          <MInput v-model="password" label="密码" placeholder="请输入密码" password />
        </template>

        <view v-if="tab !== 'emailCode'" class="remember" @tap="toggleRemember">
          <view class="remember__box" :class="{ 'remember__box--on': rememberMe }" />
          <text class="remember__label">记住我</text>
        </view>

        <text v-if="error" class="m-error">{{ error }}</text>

        <view class="m-actions">
          <MButton :loading="submitting" @click="handleLogin">登录</MButton>
          <MButton variant="ghost" @click="goRegister">没有账号，去注册</MButton>
          <MButton variant="ghost" @click="goForgotPassword">忘记密码</MButton>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.seg {
  display: flex;
  padding: 6rpx;
  border: 2rpx solid var(--color-border);
  border-radius: var(--radius-control);
  background-color: var(--color-surface-muted);
}

.seg__item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 68rpx;
  border-radius: var(--radius-control);
}

.seg__item--active {
  background-color: var(--color-surface);
}

.seg__label {
  color: var(--color-text-secondary);
  font-size: var(--font-caption);
}

.seg__item--active .seg__label {
  color: var(--color-text-primary);
}

.remember {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  border-radius: var(--radius-control);
  background-color: var(--color-surface-muted);
}

.remember__box {
  width: 32rpx;
  height: 32rpx;
  border: 3rpx solid var(--color-border-control);
  border-radius: 6rpx;
  background-color: var(--color-surface);
}

.remember__box--on {
  border-color: var(--color-primary);
  background-color: var(--color-primary);
}

.remember__label {
  color: var(--color-text-primary);
  font-size: var(--font-body);
}
</style>
