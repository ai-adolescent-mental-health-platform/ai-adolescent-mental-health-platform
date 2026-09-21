<script setup lang="ts">
/**
 * 账号安全。
 *
 * 三块：更换邮箱（验证码校验）、修改密码（复用找回密码流程）、退出登录。
 *
 * 更换邮箱只提交 `newEmail` + `emailCode` 两个字段：已核实后端
 * `UserController.mapToUser` 只读取 body 中的非 null 字段，`UserServiceImpl.updateUserInfo`
 * 走 `updateById`（null 字段被忽略），因此不必回传整份资料，也就不会误清空其它字段。
 *
 * 本页不是 tab 页，按约定用 onLoad 取数（见 apps/mobile/AGENTS.md 第四节第 7 条）。
 */

import { onUnmounted, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import type { UserProfile } from "@ai-adolescent-mental-health/domain";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MInput from "@/components/MInput.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api } from "@/lib/api";
import { clearSession, updateStoredUser } from "@/lib/session";
import { isValidCode, isValidEmail } from "@/lib/validation";
import { ensureAuthenticated } from "@/router/guard";
import { FORGOT_PASSWORD_PAGE, LOGIN_PAGE, ME_SECURITY_PAGE } from "@/router/routes";

const CODE_COUNTDOWN_SECONDS = 60;

const username = ref("");
const currentEmail = ref("");
const newEmail = ref("");
const emailCode = ref("");
const error = ref("");
const notice = ref("");
const sendingCode = ref(false);
const submitting = ref(false);
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

async function loadAccount(): Promise<void> {
  try {
    const profile = await api.user.getUserInfo();
    username.value = profile.username ?? "";
    currentEmail.value = profile.email ?? "";
  } catch (err) {
    // 401 由共享层接管，这里只呈现其余失败。
    error.value = describeError(err, "账号信息加载失败");
  }
}

onLoad(() => {
  if (!ensureAuthenticated(ME_SECURITY_PAGE)) return;
  void loadAccount();
});

async function handleSendCode(): Promise<void> {
  error.value = "";
  notice.value = "";
  if (!newEmail.value) {
    error.value = "请先输入新邮箱";
    return;
  }
  if (!isValidEmail(newEmail.value)) {
    error.value = "邮箱格式不正确";
    return;
  }
  if (newEmail.value === currentEmail.value) {
    error.value = "新邮箱不能与当前邮箱相同";
    return;
  }

  sendingCode.value = true;
  try {
    await api.user.sendEmailCode(newEmail.value, "email-change");
    startCountdown();
    uni.showToast({ title: "验证码已发送", icon: "none" });
  } catch (err) {
    error.value = describeError(err, "发送验证码失败");
  } finally {
    sendingCode.value = false;
  }
}

async function handleChangeEmail(): Promise<void> {
  error.value = "";
  notice.value = "";
  if (!newEmail.value) {
    error.value = "请输入新邮箱";
    return;
  }
  if (!isValidEmail(newEmail.value)) {
    error.value = "邮箱格式不正确";
    return;
  }
  if (!emailCode.value) {
    error.value = "请输入验证码";
    return;
  }
  if (!isValidCode(emailCode.value)) {
    error.value = "验证码为 6 位数字";
    return;
  }

  submitting.value = true;
  try {
    // 只提交这两个字段：后端 mapToUser 只读取非 null 字段，updateById 忽略 null，无需回传整份资料。
    const payload: Record<string, unknown> = {
      newEmail: newEmail.value,
      emailCode: emailCode.value,
    };
    const updated = await api.user.updateUserInfo(payload as Partial<UserProfile>);
    currentEmail.value = updated.email ?? newEmail.value;
    updateStoredUser({ email: currentEmail.value });
    newEmail.value = "";
    emailCode.value = "";
    stopTimer();
    countdown.value = 0;
    notice.value = "邮箱修改成功";
    uni.showToast({ title: "邮箱修改成功", icon: "none" });
  } catch (err) {
    error.value = describeError(err, "修改失败");
  } finally {
    submitting.value = false;
  }
}

function goForgotPassword(): void {
  // 修改密码复用找回密码流程（验证身份 → 设置新密码），不另造一套。
  uni.navigateTo({ url: FORGOT_PASSWORD_PAGE });
}

function handleLogout(): void {
  // 与 web-client 一致：退出登录只清本地会话，不调用后端 /user/logout。
  clearSession();
  uni.reLaunch({ url: LOGIN_PAGE });
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="账号安全" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="账号">
          <view class="m-field">
            <text class="m-field__label">用户名</text>
            <text class="m-field__value">{{ username || "未设置" }}</text>
          </view>
          <view class="m-field">
            <text class="m-field__label">当前邮箱</text>
            <text class="m-field__value">{{ currentEmail || "未设置" }}</text>
          </view>
        </MCard>

        <MCard title="更换邮箱">
          <MInput v-model="newEmail" label="新邮箱" placeholder="请输入新邮箱" />
          <view class="m-code-row">
            <view class="m-code-row__field">
              <MInput v-model="emailCode" label="验证码" placeholder="6位验证码" />
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
          <text v-if="notice" class="m-text-primary">{{ notice }}</text>

          <view class="m-actions">
            <MButton :loading="submitting" @click="handleChangeEmail">确认修改</MButton>
          </view>
        </MCard>

        <MCard title="密码">
          <text class="m-text-secondary">通过注册邮箱验证身份后重设密码。</text>
          <view class="m-actions">
            <MButton variant="secondary" @click="goForgotPassword">修改密码</MButton>
          </view>
        </MCard>

        <view class="m-actions">
          <MButton variant="ghost" @click="handleLogout">退出登录</MButton>
        </view>
      </view>
    </view>
  </view>
</template>
