<script setup lang="ts">
/**
 * 基础输入框。
 *
 * 取值方式与「不知道是 App 端还是 H5 端」的实际情况对齐：uni-app 的 input 只有
 * `password` 布尔属性，没有 `type="password"`，故这里用独立 prop 表达。
 * 边框用 var(--color-border-control)（3.85:1），满足 WCAG 1.4.11 对控件边界 >= 3:1 的要求；
 * 装饰性分隔才用 var(--color-border)。
 */

const props = withDefaults(
  defineProps<{
    modelValue?: string;
    label?: string;
    placeholder?: string;
    password?: boolean;
    disabled?: boolean;
    /** 错误提示文案，非空时边框转为危险色 */
    error?: string;
  }>(),
  {
    modelValue: "",
    label: "",
    placeholder: "",
    password: false,
    disabled: false,
    error: "",
  },
);

const emit = defineEmits<{
  (event: "update:modelValue", value: string): void;
}>();

function handleInput(event: unknown): void {
  const detail = (event as { detail?: { value?: string } } | undefined)?.detail;
  emit("update:modelValue", typeof detail?.value === "string" ? detail.value : "");
}
</script>

<template>
  <view class="m-input">
    <text v-if="label" class="m-input__label">{{ label }}</text>
    <input
      class="m-input__control"
      :class="{ 'm-input__control--error': !!error }"
      :value="modelValue"
      :password="password"
      :placeholder="placeholder"
      :disabled="disabled"
      placeholder-class="m-input__placeholder"
      @input="handleInput"
    />
    <text v-if="error" class="m-input__error">{{ error }}</text>
  </view>
</template>

<style scoped>
.m-input {
  display: flex;
  flex-direction: column;
}

.m-input__label {
  margin-bottom: 12rpx;
  color: var(--color-text-secondary);
  font-size: var(--font-caption);
}

.m-input__control {
  box-sizing: border-box;
  width: 100%;
  height: 88rpx;
  padding: 0 20rpx;
  border: 2rpx solid var(--color-border-control);
  border-radius: var(--radius-control);
  background-color: var(--color-surface);
  color: var(--color-text-primary);
  font-size: var(--font-body);
}

.m-input__control--error {
  border-color: var(--color-danger);
}

.m-input__placeholder {
  color: var(--color-text-secondary);
}

.m-input__error {
  margin-top: 8rpx;
  color: var(--color-danger);
  font-size: var(--font-caption);
}
</style>
