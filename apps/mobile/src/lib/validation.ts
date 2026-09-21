/**
 * 表单校验与密码强度。
 *
 * 规则**对齐后端**：这里的每条正则都逐字对应后端 `RegexConstant`
 * （`apps/backend/.../constant/RegexConstant.java`），因此客户端拦截的输入
 * 与后端会拒绝的输入完全一致，不会出现「前端拦下后端本可接受的输入」。
 *
 * 一处需要留意的既有陷阱（本步不改后端，仅记录）：`PASSWORD` 的字符集是 `[a-zA-Z\d]`，
 * **不含符号**，因此形如 `Abc12345!` 的密码会被后端直接拒绝；而 web-client 的密码强度算法
 * 会给「含符号」加分。这里沿用 web-client 的强度算法以保持两端一致，
 * 同时用后端正则做提交前校验，让用户在提交前就得到明确提示。
 */

/** 后端 RegexConstant.USERNAME：^[a-zA-Z0-9_-]{4,16}$ */
export const USERNAME_PATTERN = /^[a-zA-Z0-9_-]{4,16}$/;

/** 后端 RegexConstant.PASSWORD：^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d]{8,16}$ */
export const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d]{8,16}$/;

/** 后端 RegexConstant.EMAIL：^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$ */
export const EMAIL_PATTERN = /^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$/;

/** 后端 RegexConstant.PHONE：^1[3-9]\d{9}$ */
export const PHONE_PATTERN = /^1[3-9]\d{9}$/;

/** 验证码位数（与 web-client 的 maxLength=6 一致） */
export const CODE_LENGTH = 6;

export const USERNAME_HINT = "4-16 位字母、数字、下划线、减号";
export const PASSWORD_HINT = "8-16 位，必须包含大小写字母和数字";

export function isValidUsername(value: string): boolean {
  return USERNAME_PATTERN.test(value);
}

export function isValidPassword(value: string): boolean {
  return PASSWORD_PATTERN.test(value);
}

export function isValidEmail(value: string): boolean {
  return EMAIL_PATTERN.test(value);
}

export function isValidPhone(value: string): boolean {
  return PHONE_PATTERN.test(value);
}

export function isValidCode(value: string): boolean {
  return value.length === CODE_LENGTH && /^\d+$/.test(value);
}

export type PasswordStrength = {
  /** 进度百分比 */
  percent: number;
  label: "弱" | "中" | "强";
};

/**
 * 密码强度算法与 web-client 的 `getPasswordStrength` 逐条对齐：
 * 长度 >= 8、同时含大小写、含数字、含符号、长度 >= 12 各计 1 分；
 * 总分 <= 1 为弱，<= 3 为中，否则为强。
 * 只返回百分比与文案，配色由调用方从 theme.css 变量层取。
 */
export function getPasswordStrength(password: string): PasswordStrength {
  let score = 0;
  if (password.length >= 8) score += 1;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 1;
  if (/\d/.test(password)) score += 1;
  if (/[^a-zA-Z\d]/.test(password)) score += 1;
  if (password.length >= 12) score += 1;

  if (score <= 1) return { percent: 25, label: "弱" };
  if (score <= 3) return { percent: 60, label: "中" };
  return { percent: 100, label: "强" };
}
