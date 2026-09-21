<script setup lang="ts">
/**
 * 个人资料：查看 / 编辑 / 保存。
 *
 * 字段与交互参考 web-client 的 `components/me/info-page.tsx`：昵称、头像、性别、生日、
 * 手机号、个性签名可编辑；用户名与邮箱只读（邮箱在「账号安全」页修改，避免两处重复实现）。
 * 按 uni-app 技术栈重写，未复制 web-client 源文件。
 *
 * 本页不是 tab 页，按约定用 onLoad 取数（见 apps/mobile/AGENTS.md 第四节第 7 条）。
 * 头像上传走 `uploadImage`（内部是 uni.uploadFile），原因见 `src/lib/api.ts` 的注释。
 */

import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import type { UserProfile } from "@ai-adolescent-mental-health/domain";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MInput from "@/components/MInput.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api, uploadImage } from "@/lib/api";
import { getStoredUser, updateStoredUser } from "@/lib/session";
import { isValidPhone } from "@/lib/validation";
import { ensureAuthenticated } from "@/router/guard";
import { ME_INFO_PAGE, ME_SECURITY_PAGE } from "@/router/routes";

const AVATAR_MAX_BYTES = 2 * 1024 * 1024;
const NICKNAME_MIN = 2;
const NICKNAME_MAX = 20;
const SIGNATURE_MAX = 100;

const GENDER_OPTIONS = [
  { value: 0, label: "保密" },
  { value: 1, label: "男" },
  { value: 2, label: "女" },
];

const loading = ref(true);
const editing = ref(false);
const saving = ref(false);
const error = ref("");

const profile = ref<UserProfile | null>(null);
/** 已保存的头像地址 */
const headPath = ref("");
/** 本次新选、尚未上传的本地临时路径 */
const pendingAvatarPath = ref("");

const nickname = ref("");
const sex = ref(0);
const birthday = ref("");
const phone = ref("");
const signature = ref("");

const genderLabel = computed(
  () => GENDER_OPTIONS.find((option) => option.value === sex.value)?.label ?? "保密",
);

const avatarDisplay = computed(() => pendingAvatarPath.value || headPath.value);
const avatarFallback = computed(() => {
  const source = nickname.value || profile.value?.username || "U";
  return source.slice(0, 1).toUpperCase();
});

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}

function applyProfile(next: UserProfile): void {
  profile.value = next;
  nickname.value = next.nickname || "";
  sex.value = typeof next.sex === "number" ? next.sex : 0;
  phone.value = next.phone || "";
  signature.value = next.signature || "";
  headPath.value = next.headPath || "";
  // birthday 不在共享 UserProfile 类型里（web-client 也是按扩展字段读取），故做一次显式收窄。
  const raw = (next as unknown as Record<string, unknown>).birthday;
  birthday.value = typeof raw === "string" ? raw : "";
}

async function loadProfile(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    applyProfile(await api.user.getUserInfo());
  } catch (err) {
    // 401 由共享层的 onUnauthorized 接管；这里只呈现其余失败。
    error.value = describeError(err, "资料加载失败");
  } finally {
    loading.value = false;
  }
}

onLoad(() => {
  if (!ensureAuthenticated(ME_INFO_PAGE)) return;

  // 先用本地会话渲染，避免接口慢时整页空白；随后用接口结果覆盖。
  const cached = getStoredUser<UserProfile>();
  if (cached) applyProfile(cached);

  void loadProfile();
});

function startEditing(): void {
  editing.value = true;
  error.value = "";
}

function cancelEditing(): void {
  editing.value = false;
  error.value = "";
  pendingAvatarPath.value = "";
  if (profile.value) applyProfile(profile.value);
}

function handleChooseAvatar(): void {
  if (!editing.value) return;
  uni.chooseImage({
    count: 1,
    sizeType: ["compressed"],
    sourceType: ["album", "camera"],
    success: (res) => {
      const paths = (res.tempFilePaths ?? []) as string[];
      const path = paths[0];
      if (!path) return;

      const files = (res.tempFiles ?? []) as { size?: number }[];
      const size = files[0]?.size;
      // size 并非所有端都会返回，取不到时不做本地拦截，交由后端与对象存储限制。
      if (typeof size === "number" && size > AVATAR_MAX_BYTES) {
        error.value = "头像大小不能超过 2MB";
        return;
      }

      error.value = "";
      pendingAvatarPath.value = path;
    },
    fail: () => {
      // 用户取消选择不算错误，保持静默。
    },
  });
}

function handleBirthdayChange(event: { detail?: { value?: string } }): void {
  birthday.value = event.detail?.value ?? "";
}

async function handleSave(): Promise<void> {
  error.value = "";

  const trimmedNickname = nickname.value.trim();
  if (trimmedNickname.length < NICKNAME_MIN || trimmedNickname.length > NICKNAME_MAX) {
    error.value = `昵称需在 ${NICKNAME_MIN}-${NICKNAME_MAX} 个字符之间`;
    return;
  }
  if (phone.value && !isValidPhone(phone.value)) {
    error.value = "手机号格式不正确";
    return;
  }
  if (signature.value.length > SIGNATURE_MAX) {
    error.value = `个性签名不能超过 ${SIGNATURE_MAX} 个字符`;
    return;
  }

  saving.value = true;
  try {
    let nextHeadPath = headPath.value;
    if (pendingAvatarPath.value) {
      // 先上传拿到 URL，再随资料一起保存，避免出现「头像换了但资料没存」的半成品状态。
      nextHeadPath = await uploadImage(pendingAvatarPath.value, "avatar");
    }

    const payload: Record<string, unknown> = {
      nickname: trimmedNickname,
      sex: sex.value,
      phone: phone.value,
      signature: signature.value,
      headPath: nextHeadPath,
    };

    /*
     * birthday 只在非空时携带：后端 `UserController.mapToUser` 对该字段走
     * `LocalDate.parse((String) body.get("birthday"))`，传空字符串会抛异常并让整次保存失败。
     * 不传时字段为 null，后端 updateById 会忽略，等于「保持原值不变」。
     */
    if (birthday.value) {
      payload.birthday = birthday.value;
    }

    const updated = await api.user.updateUserInfo(payload as Partial<UserProfile>);
    applyProfile(updated);
    pendingAvatarPath.value = "";

    // 同步本地会话，否则个人中心 tab 页仍显示旧昵称/旧头像。
    updateStoredUser({
      nickname: updated.nickname,
      headPath: updated.headPath,
      signature: updated.signature,
      phone: updated.phone,
      sex: updated.sex,
      birthday: birthday.value,
    });

    editing.value = false;
    uni.showToast({ title: "保存成功", icon: "none" });
  } catch (err) {
    error.value = describeError(err, "保存失败");
  } finally {
    saving.value = false;
  }
}

function goSecurity(): void {
  uni.navigateTo({ url: ME_SECURITY_PAGE });
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="个人信息" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="头像">
          <view class="avatar">
            <image v-if="avatarDisplay" class="avatar__img" :src="avatarDisplay" mode="aspectFill" />
            <view v-else class="avatar__fallback">
              <text class="avatar__letter">{{ avatarFallback }}</text>
            </view>
            <view class="avatar__side">
              <text class="m-text-secondary">
                {{ editing ? "点击右侧按钮更换头像" : "编辑资料时可更换头像" }}
              </text>
              <MButton v-if="editing" variant="secondary" size="small" :block="false" @click="handleChooseAvatar">
                选择图片
              </MButton>
            </view>
          </view>
          <text class="m-text-secondary">支持相册与拍照，单个文件不超过 2MB。</text>
        </MCard>

        <MCard :title="editing ? '编辑资料' : '资料'">
          <view v-if="loading" class="m-text-secondary">加载中...</view>

          <template v-else-if="editing">
            <view class="field">
              <MInput v-model="nickname" label="昵称" placeholder="请输入昵称" />
              <text class="m-text-secondary">{{ nickname.length }}/{{ NICKNAME_MAX }}</text>
            </view>

            <view class="field">
              <text class="m-text-secondary">性别</text>
              <view class="m-choice-row">
                <view
                  v-for="option in GENDER_OPTIONS"
                  :key="option.value"
                  class="m-choice"
                  :class="{ 'm-choice--active': sex === option.value }"
                  @tap="sex = option.value"
                >
                  <text class="m-choice__label">{{ option.label }}</text>
                </view>
              </view>
            </view>

            <view class="field">
              <text class="m-text-secondary">生日</text>
              <picker mode="date" :value="birthday" @change="handleBirthdayChange">
                <view class="picker">
                  <text class="picker__text">{{ birthday || "请选择生日" }}</text>
                </view>
              </picker>
            </view>

            <MInput v-model="phone" label="手机号" placeholder="请输入手机号" />
            <MInput v-model="signature" label="个性签名" placeholder="请输入个性签名" />
            <text class="m-text-secondary">{{ signature.length }}/{{ SIGNATURE_MAX }}</text>

            <text v-if="error" class="m-error">{{ error }}</text>

            <view class="m-actions">
              <MButton :loading="saving" @click="handleSave">保存修改</MButton>
              <MButton variant="ghost" @click="cancelEditing">取消</MButton>
            </view>
          </template>

          <template v-else>
            <view class="m-field">
              <text class="m-field__label">用户名</text>
              <text class="m-field__value">{{ profile?.username || "未设置" }}</text>
            </view>
            <view class="m-field">
              <text class="m-field__label">昵称</text>
              <text class="m-field__value">{{ nickname || "未设置" }}</text>
            </view>
            <view class="m-field">
              <text class="m-field__label">性别</text>
              <text class="m-field__value">{{ genderLabel }}</text>
            </view>
            <view class="m-field">
              <text class="m-field__label">生日</text>
              <text class="m-field__value">{{ birthday || "未设置" }}</text>
            </view>
            <view class="m-field">
              <text class="m-field__label">手机号</text>
              <text class="m-field__value">{{ phone || "未设置" }}</text>
            </view>
            <view class="m-field">
              <text class="m-field__label">邮箱</text>
              <text class="m-field__value">{{ profile?.email || "未设置" }}</text>
            </view>
            <view class="m-field">
              <text class="m-field__label">签名</text>
              <text class="m-field__value">{{ signature || "未设置" }}</text>
            </view>

            <text v-if="error" class="m-error">{{ error }}</text>

            <view class="m-actions">
              <MButton @click="startEditing">编辑资料</MButton>
              <MButton variant="ghost" @click="goSecurity">邮箱等账号信息在「账号安全」修改</MButton>
            </view>
          </template>
        </MCard>
      </view>
    </view>
  </view>
</template>

<style scoped>
.avatar {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar__img {
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background-color: var(--color-surface-muted);
}

.avatar__fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background-color: var(--color-surface-muted);
}

.avatar__letter {
  color: var(--color-text-primary);
  font-size: 56rpx;
}

.avatar__side {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: var(--space-gap);
}

.picker {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 20rpx;
  border: 2rpx solid var(--color-border-control);
  border-radius: var(--radius-control);
  background-color: var(--color-surface);
}

.picker__text {
  color: var(--color-text-primary);
  font-size: var(--font-body);
}
</style>
