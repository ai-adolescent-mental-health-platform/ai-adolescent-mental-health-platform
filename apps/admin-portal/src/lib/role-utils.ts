// Role values
export const ROLE_USER = 1;
export const ROLE_SUPER_ADMIN = 4;

// Role labels（医生=2 / 医院管理员=3 已随模块退役，不再提供）
export const ROLE_LABELS: Record<number, string> = {
  1: "普通用户",
  4: "超级管理员",
};

// Get redirect path based on role
export function getRoleDashboardPath(role: number, isPsychologist: boolean): string {
  if (isPsychologist) return "/psychologist-admin/workbench";
  if (role === ROLE_SUPER_ADMIN) return "/admin/dashboard";
  return "/login";
}

export interface MenuItem {
  path?: string;
  label: string;
  icon?: string;
  children?: MenuItem[];
}

// Super Admin (role 4) menu
export const SUPER_ADMIN_MENU: MenuItem[] = [
  { path: "/admin/dashboard", label: "工作概览", icon: "DataBoard" },
  { path: "/admin/users", label: "用户管理", icon: "UserFilled" },
  { path: "/admin/alerts", label: "预警管理", icon: "Warning" },
  { path: "/admin/psychologist", label: "心理咨询师管理", icon: "Service" },
  { path: "/admin/psychologist-fields", label: "擅长领域管理", icon: "Collection" },
  { path: "/admin/psychologist-qualifications", label: "资质管理", icon: "Medal" },
  { path: "/admin/psychologist/audit", label: "资料审核", icon: "DocumentChecked" },
  {
    label: "内容管理", icon: "Reading",
    children: [
      { path: "/admin/content/articles", label: "心理文章" },
      { path: "/admin/content/courses", label: "心理课程" },
      { path: "/admin/content/assessments", label: "心理测评" },
      { path: "/admin/content/books", label: "心理书籍" },
      { path: "/admin/system/quotes", label: "每日语录" },
      { path: "/admin/system/tags", label: "标签管理" },
      { path: "/admin/content/audit", label: "审核管理" },
    ],
  },
  { path: "/admin/platform/income", label: "平台收入", icon: "Money" },
  { path: "/admin/meme", label: "热梗管理", icon: "Sunny" },
  { path: "/admin/system/feedbacks", label: "反馈管理", icon: "ChatDotSquare" },
];

// Psychologist menu
export const PSYCHOLOGIST_MENU: MenuItem[] = [
  { path: "/psychologist-admin/workbench", label: "工作台", icon: "Monitor" },
  { path: "/psychologist-admin/schedule", label: "排班管理", icon: "Calendar" },
  { path: "/psychologist-admin/appointments", label: "预约管理", icon: "Tickets" },
  { path: "/psychologist-admin/income", label: "收入管理", icon: "Money" },
  { path: "/psychologist-admin/chat", label: "在线咨询", icon: "ChatLineSquare" },
  { path: "/psychologist-admin/profile", label: "个人资料", icon: "User" },
];

// Get menu based on role
export function getMenuByRole(role: number, isPsychologist: boolean): MenuItem[] {
  if (isPsychologist) return PSYCHOLOGIST_MENU;
  if (role === ROLE_SUPER_ADMIN) return SUPER_ADMIN_MENU;
  return [];
}
