<script setup lang="ts">
/**
 * 找回密码页（两步：验证身份 → 设置新密码）。
 *
 * 与 web-client 的 forgot-password 流程与字段一致：用户名 + 注册邮箱 + 验证码，
 * 验证通过后设置新密码。对应共享层方法已就绪：
 * `sendForgotPasswordCode` / `verifyForgotPasswordCode` / `resetPassword`。
 */

import { onUnmounted, ref } from "vue";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MInput from "@/components/MInput.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api } from "@/lib/api";
import { LOGIN_PAGE } from "@/router/routes";
import { PASSWORD_HINT, isValidCode, isValidEmail, isValidPassword } from "@/lib/validation";

type Step = "verify" | "reset";

const CODE_COUNTDOWN_SECONDS = 60;

const step = ref<Step>("verify");
const username = ref("");
const email = ref("");
const code = ref("");
const newPassword = ref("");
const confirmPassword = ref("");
const submitting = ref(false);
const sendingCode = ref(false);
const error = ref("");
const countdown = ref(0);

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

onUnmounted(stopTimer);

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}

function backToLogin(): void {
  uni.redirectTo({ url: LOGIN_PAGE });
}

async function handleSendCode(): Promise<void> {
  error.value = "";
  if (!username.value || !email.value) {
    error.value = "请先填写用户名和邮箱";
    return;
  }
  if (!isValidEmail(email.value)) {
    error.value = "邮箱格式不正确";
    return;
  }

  sendingCode.value = true;
  try {
    await api.user.sendForgotPasswordCode(username.value, email.value);
    startCountdown();
    uni.showToast({ title: "验证码已发送", icon: "none" });
  } catch (err) {
    error.value = describeError(err, "发送失败，请检查用户名和邮箱是否匹配");
  } finally {
    sendingCode.value = false;
  }
}

async function handleVerify(): Promise<void> {
  error.value = "";
  if (!username.value || !email.value) {
    error.value = "请填写用户名和邮箱";
    return;
  }
  if (!isValidEmail(email.value)) {
    error.value = "邮箱格式不正确";
    return;
  }
  if (!code.value) {
    error.value = "请输入验证码";
    return;
  }

  submitting.value = true;
  try {
    await api.user.verifyForgotPasswordCode(username.value, email.value, code.value);
    step.value = "reset";
    uni.showToast({ title: "验证通过，请设置新密码", icon: "none" });
  } catch (err) {
    error.value = describeError(err, "验证失败，请检查验证码");
  } finally {
    submitting.value = false;
  }
}

async function handleReset(): Promise<void> {
  error.value = "";
  if (!newPassword.value || !confirmPassword.value) {
    error.value = "请填写新密码和确认密码";
    return;
  }
  if (!isValidPassword(newPassword.value)) {
    error.value = `密码格式不正确（${PASSWORD_HINT}）`;
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = "两次密码不一致";
    return;
  }
  if (!isValidCode(code.value)) {
    error.value = "验证码为 6 位数字";
    return;
  }

  submitting.value = true;
  try {
    await api.user.resetPassword(username.value, email.value, code.value, newPassword.value, confirmPassword.value);
    uni.showToast({ title: "密码重置成功，请使用新密码登录", icon: "none" });
    setTimeout(backToLogin, 900);
  } catch (err) {
    error.value = describeError(err, "重置失败，请重试");
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="忘记密码" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="忘记密码" muted>
          <text class="m-text-primary">
            {{ step === "verify" ? "输入用户名和注册邮箱进行验证" : "设置新的登录密码" }}
          </text>
        </MCard>

        <template v-if="step === 'verify'">
          <MInput v-model="username" label="用户名" placeholder="请输入用户名" />
          <MInput v-model="email" label="注册邮箱" placeholder="请输入注册邮箱" />
          <view class="m-code-row">
            <view class="m-code-row__field">
              <MInput v-model="code" label="验证码" placeholder="请输入验证码" />
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

          <text v-if="error" class="m-error">{{ error }}</text>

          <view class="m-actions">
            <MButton :loading="submitting" @click="handleVerify">验证并下一步</MButton>
            <MButton variant="ghost" @click="backToLogin">返回登录</MButton>
          </view>
        </template>

        <template v-else>
          <MInput v-model="newPassword" label="新密码" placeholder="请输入新密码" password />
          <text class="m-text-secondary">{{ PASSWORD_HINT }}</text>
          <MInput v-model="confirmPassword" label="确认密码" placeholder="请再次输入新密码" password />

          <text v-if="error" class="m-error">{{ error }}</text>

          <view class="m-actions">
            <MButton :loading="submitting" @click="handleReset">确认重置</MButton>
            <MButton variant="ghost" @click="backToLogin">返回登录</MButton>
          </view>
        </template>
      </view>
    </view>
  </view>
</template>
