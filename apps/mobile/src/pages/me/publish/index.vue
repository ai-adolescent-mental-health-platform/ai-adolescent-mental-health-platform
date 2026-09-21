<script setup lang="ts">
/**
 * 发布文章（文本版）。
 *
 * 接口：POST /article/user，载荷 `{ title, content, coverUrl?, tagId? }`；
 *       标签来自 GET /article/tag/list。
 *
 * 本步只做**文本发布**，不做封面上传：
 * 后端有 `POST /article/user/cover/upload`，但共享层未封装它（现有的 `uploadImage` 指向 `/common/upload`，
 * 是另一个端点），而任务约定"接口不完整或复杂度高时先做能做的部分并单独记待办，不要卡住整块"。
 * 待办见页面内的「封面」卡片与交付报告。
 *
 * 客户端只拦「标题/正文为空」这类确定性错误，长度与内容规则交给后端裁决，避免两端口径分叉。
 */

import { onLoad } from "@dcloudio/uni-app";
import { ref } from "vue";
import MButton from "@/components/MButton.vue";
import MCard from "@/components/MCard.vue";
import MInput from "@/components/MInput.vue";
import MNavBar from "@/components/MNavBar.vue";
import { api } from "@/lib/api";
import { ensureAuthenticated } from "@/router/guard";
import { ME_ARTICLES_PAGE, ME_PUBLISH_PAGE } from "@/router/routes";

type TagOption = { id: number; name: string };

const title = ref("");
const content = ref("");
const tagId = ref(0);
const tags = ref<TagOption[]>([]);
const tagsError = ref("");
const error = ref("");
const submitting = ref(false);

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function mapTag(raw: unknown): TagOption {
  const d = asRecord(raw);
  return {
    id: typeof d.id === "number" ? d.id : 0,
    name: typeof d.name === "string" ? d.name : "",
  };
}

function onContentInput(event: unknown): void {
  const detail = (event as { detail?: { value?: string } } | undefined)?.detail;
  content.value = typeof detail?.value === "string" ? detail.value : "";
}

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback;
}

async function loadTags(): Promise<void> {
  tagsError.value = "";
  try {
    const raw = await api.content.articleTags();
    tags.value = (raw ?? []).map(mapTag).filter((tag) => tag.id > 0 && tag.name);
  } catch (err) {
    // 标签失败不阻断发布：tagId 是可选字段。
    tagsError.value = describeError(err, "标签加载失败，可不选标签直接发布");
  }
}

onLoad(() => {
  if (!ensureAuthenticated(ME_PUBLISH_PAGE)) return;
  void loadTags();
});

function selectTag(id: number): void {
  tagId.value = tagId.value === id ? 0 : id;
}

function backAfterPublish(): void {
  const pages = getCurrentPages();
  if (pages.length > 1) {
    uni.navigateBack();
    return;
  }
  uni.redirectTo({ url: ME_ARTICLES_PAGE });
}

async function handleSubmit(): Promise<void> {
  error.value = "";
  if (!title.value.trim()) {
    error.value = "请填写标题";
    return;
  }
  if (!content.value.trim()) {
    error.value = "请填写正文";
    return;
  }

  submitting.value = true;
  try {
    const payload: { title: string; content: string; tagId?: number } = {
      title: title.value.trim(),
      content: content.value.trim(),
    };
    if (tagId.value) payload.tagId = tagId.value;

    await api.content.publishArticle(payload);
    uni.showToast({ title: "已提交，等待审核", icon: "none" });
    setTimeout(backAfterPublish, 900);
  } catch (err) {
    error.value = describeError(err, "发布失败，请重试");
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <view class="m-page">
    <MNavBar title="发布文章" show-back />
    <view class="m-page__body">
      <view class="m-stack">
        <MCard title="正文内容">
          <MInput v-model="title" label="标题" placeholder="请输入标题" />
          <view class="editor">
            <text class="m-text-secondary">正文</text>
            <textarea
              class="editor__input"
              :value="content"
              placeholder="请输入正文"
              @input="onContentInput"
            />
            <text class="m-text-secondary">{{ content.length }} 字</text>
          </view>
        </MCard>

        <MCard title="标签（可选）">
          <text v-if="tagsError" class="m-error">{{ tagsError }}</text>
          <view v-else-if="tags.length === 0" class="m-text-secondary">暂无可用标签</view>
          <view v-else class="tag-row">
            <view
              v-for="tag in tags"
              :key="tag.id"
              class="m-choice"
              :class="{ 'm-choice--active': tagId === tag.id }"
              @tap="selectTag(tag.id)"
            >
              <text class="m-choice__label">{{ tag.name }}</text>
            </view>
          </view>
        </MCard>

        <MCard title="封面（本步未实现）" muted>
          <text class="m-text-primary">
            封面上传需要调用 `POST /article/user/cover/upload`，共享层尚未封装该端点。
          </text>
          <text class="m-text-secondary">
            已记为待办：补齐共享层封装后再接上，本步不阻塞文字发布。
          </text>
        </MCard>

        <text v-if="error" class="m-error">{{ error }}</text>

        <view class="m-actions">
          <MButton :loading="submitting" @click="handleSubmit">提交发布</MButton>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: var(--space-gap);
}

.editor__input {
  box-sizing: border-box;
  width: 100%;
  min-height: 360rpx;
  padding: 20rpx;
  border: 2rpx solid var(--color-border-control);
  border-radius: var(--radius-control);
  background-color: var(--color-surface);
  color: var(--color-text-primary);
  font-size: var(--font-body);
  line-height: 1.6;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.tag-row .m-choice {
  flex: none;
  padding: 0 24rpx;
}
</style>
