<script setup lang="ts">
/**
 * 注册页。
 *
 * 字段与 web-client 注册页一致：用户名、密码、邮箱、邮箱验证码、手机号（选填）、协议勾选。
 * 校验规则取自后端 `RegexConstant`（见 lib/validation.ts 的说明），与 web-client 的提示文案一致。
 * 接口走统一 api 层，页面内不出现直接的 uni.request / fetch。
 */

import { computed, onUnmounted, ref } from "vue";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MInput from "@/components/MInput.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api } from "@/lib/api";
import { LOGIN_PAGE, LEGAL_PAGE } from "@/router/routes";
import {
  PASSWORD_HINT,
  USERNAME_HINT,
  getPasswordStrength,
  isValidCode,
  isValidEmail,
  isValidPassword,
  isValidPhone,
  isValidUsername,
} from "@/lib/validation";

const CODE_COUNTDOWN_SECONDS = 60;

const username = ref("");
const password = ref("");
const email = ref("");
const code = ref("");
const phone = ref("");
const agreed = ref(false);
const submitting = ref(false);
const sendingCode = ref(false);
const error = ref("");
const countdown = ref(0);

let timer: ReturnType<typeof setInterval> | undefined;

const strength = computed(() => getPasswordStrength(password.value));

/** 强度条配色：只从主题变量层取色，不写死色值 */
const strengthTone = computed(() => {
  if (strength.value.label === "弱") return "weak";
  if (strength.value.label === "中") return "mid";
  return "strong";
});

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

onUnmounted(stopTimer);

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}

function toggleAgreed(): void {
  agreed.value = !agreed.value;
}

/** 打开协议正文（不要求登录，注册前即可查阅）。 */
function openLegal(tab: "terms" | "privacy"): void {
  uni.navigateTo({ url: `${LEGAL_PAGE}?tab=${tab}` });
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
    await api.user.sendEmailCode(email.value, "register");
    startCountdown();
    uni.showToast({ title: "验证码已发送", icon: "none" });
  } catch (err) {
    error.value = describeError(err, "发送失败，请重试");
  } finally {
    sendingCode.value = false;
  }
}

function backToLogin(): void {
  const pages = getCurrentPages();
  if (pages.length > 1) {
    uni.navigateBack();
    return;
  }
  uni.redirectTo({ url: LOGIN_PAGE });
}

async function handleRegister(): Promise<void> {
  error.value = "";

  if (!username.value || !password.value || !email.value || !code.value) {
    error.value = "请填写必填项";
    return;
  }
  if (!isValidUsername(username.value)) {
    error.value = `用户名格式不正确（${USERNAME_HINT}）`;
    return;
  }
  if (!isValidPassword(password.value)) {
    error.value = `密码格式不正确（${PASSWORD_HINT}）`;
    return;
  }
  if (!isValidEmail(email.value)) {
    error.value = "邮箱格式不正确";
    return;
  }
  if (!isValidCode(code.value)) {
    error.value = "验证码为 6 位数字";
    return;
  }
  if (phone.value && !isValidPhone(phone.value)) {
    error.value = "手机号格式不正确";
    return;
  }
  if (!agreed.value) {
    error.value = "请阅读并同意相关协议";
    return;
  }

  submitting.value = true;
  try {
    await api.user.registerWithEmail({
      username: username.value,
      password: password.value,
      email: email.value,
      code: code.value,
      ...(phone.value ? { phone: phone.value } : {}),
    });
    uni.showToast({ title: "注册成功，请登录", icon: "none" });
    setTimeout(backToLogin, 800);
  } catch (err) {
    error.value = describeError(err, "注册失败，请重试");
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="注册" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="注册心愈智联" muted>
          <text class="m-text-secondary">加入我们，开启心理健康之旅</text>
        </MCard>

        <view>
          <MInput v-model="username" label="用户名" placeholder="用户名" />
          <text class="m-text-secondary">{{ USERNAME_HINT }}</text>
        </view>

        <view>
          <MInput v-model="password" label="密码" placeholder="密码" password />
          <view v-if="password" class="strength">
            <view class="strength__track">
              <view
                class="strength__fill"
                :class="`strength__fill--${strengthTone}`"
                :style="{ width: `${strength.percent}%` }"
              />
            </view>
            <text class="m-text-secondary">强度：{{ strength.label }}</text>
          </view>
          <text class="m-text-secondary">{{ PASSWORD_HINT }}</text>
        </view>

        <view class="m-code-row">
          <view class="m-code-row__field">
            <MInput v-model="email" label="邮箱" placeholder="邮箱" />
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

        <MInput v-model="code" label="验证码" placeholder="请输入验证码" />
        <MInput v-model="phone" label="手机号（选填）" placeholder="请输入手机号" />

        <view class="agree">
          <view class="agree__box" :class="{ 'agree__box--on': agreed }" @tap="toggleAgreed" />
          <view class="agree__text">
            <text class="agree__plain" @tap="toggleAgreed">我已阅读并同意</text>
            <text class="agree__link" @tap.stop="openLegal('terms')">《心愈智联用户服务协议》</text>
            <text class="agree__plain" @tap="toggleAgreed">和</text>
            <text class="agree__link" @tap.stop="openLegal('privacy')">《隐私政策》</text>
          </view>
        </view>

        <text v-if="error" class="m-error">{{ error }}</text>

        <view class="m-actions">
          <MButton :loading="submitting" @click="handleRegister">注册</MButton>
          <MButton variant="ghost" @click="backToLogin">已有账号，去登录</MButton>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.strength {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 12rpx;
}

.strength__track {
  flex: 1;
  height: 14rpx;
  border-radius: 999rpx;
  background-color: var(--color-border);
  overflow: hidden;
}

.strength__fill {
  height: 100%;
  border-radius: 999rpx;
}

.strength__fill--weak {
  background-color: var(--color-danger);
}

.strength__fill--mid {
  background-color: var(--color-warning);
}

.strength__fill--strong {
  background-color: var(--color-primary);
}

.agree {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  border-radius: var(--radius-control);
  background-color: var(--color-surface-muted);
}

.agree__box {
  flex: none;
  width: 32rpx;
  height: 32rpx;
  margin-top: 6rpx;
  border: 3rpx solid var(--color-border-control);
  border-radius: 6rpx;
  background-color: var(--color-surface);
}

.agree__box--on {
  border-color: var(--color-primary);
  background-color: var(--color-primary);
}

.agree__text {
  flex: 1;
  color: var(--color-text-primary);
  font-size: var(--font-caption);
  line-height: 1.7;
}

.agree__plain {
  color: var(--color-text-primary);
  font-size: var(--font-caption);
}

/* 链接色主色在粉彩底上实测 5.54:1，满足正文对比度要求 */
.agree__link {
  color: var(--color-primary);
  font-size: var(--font-caption);
}
</style>
