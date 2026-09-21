<script setup lang="ts">
/**
 * 基础按钮。
 *
 * 配色全部走主题变量，组件内不出现任何字面色值。
 * 对比度（见 styles/theme.css 注释）：primary 为白字压 #1F6B4A（6.44:1），
 * secondary 为深字压粉彩（13.84:1）——粉彩底一律配深色字，不配白字。
 */

type ButtonVariant = "primary" | "secondary" | "ghost";
type ButtonSize = "normal" | "small";

const props = withDefaults(
  defineProps<{
    variant?: ButtonVariant;
    size?: ButtonSize;
    disabled?: boolean;
    loading?: boolean;
    block?: boolean;
  }>(),
  {
    variant: "primary",
    size: "normal",
    disabled: false,
    loading: false,
    block: true,
  },
);

const emit = defineEmits<{
  (event: "click", payload: unknown): void;
}>();

function handleTap(payload: unknown): void {
  if (props.disabled || props.loading) return;
  emit("click", payload);
}
</script>

<template>
  <button
    class="m-button"
    :class="[
      `m-button--${variant}`,
      `m-button--${size}`,
      {
        'm-button--block': block,
        'm-button--inactive': disabled || loading,
      },
    ]"
    :disabled="disabled || loading"
    @tap="handleTap"
  >
    <text v-if="loading" class="m-button__label">处理中</text>
    <text v-else class="m-button__label"><slot /></text>
  </button>
</template>

<style scoped>
.m-button {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0 var(--space-gap);
  height: 88rpx;
  border: 2rpx solid transparent;
  border-radius: var(--radius-control);
  font-size: var(--font-body);
  line-height: 1;
}

.m-button--block {
  width: 100%;
}

.m-button--small {
  height: 64rpx;
  padding: 0 20rpx;
  font-size: var(--font-caption);
}

.m-button--primary {
  background-color: var(--color-primary);
  color: var(--color-on-primary);
}

.m-button--secondary {
  background-color: var(--color-surface-muted);
  color: var(--color-text-primary);
}

.m-button--ghost {
  background-color: transparent;
  border-color: var(--color-border-control);
  color: var(--color-primary);
}

.m-button--inactive {
  background-color: var(--color-surface-muted);
  border-color: var(--color-border);
  color: var(--color-text-secondary);
}

.m-button__label {
  color: inherit;
  font-size: inherit;
}
</style>
