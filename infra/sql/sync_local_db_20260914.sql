-- ============================================================
-- 已有库同步脚本 2026-09-14
--
-- 背景：apps/backend/sql/schema.sql 已按当前开发库重写对齐（54 张活表），
--   以下两项由补丁文件提供、未合入 schema.sql，导致部分已有库缺少它们：
--     1. user_usage_time 表        ← apps/backend/sql/schema_xiaoai_usage.sql
--     2. email_verify_code 的两个列 ← apps/backend/sql/schema_email_bind.sql
--
-- 影响：UserUsageTime.java（@TableName("user_usage_time")）/ UsageTimeService
--   会读写该表，表缺失时调用抛「表不存在」；email_verify_code 缺 openid
--   两列时，邮箱验证码的 openid 关联功能不可用。
--
-- 适用范围：仅用于补齐【已存在】的库。CI 建库不需要本脚本
--   （ci.yml 已按序导入 schema.sql + schema_email_bind.sql + schema_xiaoai_usage.sql）。
--
-- 幂等性：CREATE TABLE 可重复执行；ALTER ADD COLUMN 不可，
--   重复执行会报 "Duplicate column name"，可忽略。
-- ============================================================

USE xinyuzhilian;

-- 1. 用户使用时长表（小爱倾听时长管理）
CREATE TABLE IF NOT EXISTS user_usage_time (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    used_seconds INT DEFAULT 0 COMMENT '今日已使用秒数',
    last_reset_date DATE COMMENT '上次重置日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_id (user_id),
    INDEX idx_last_reset_date (last_reset_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户使用时长记录表';

-- 2. email_verify_code 补充 openid / openid_type 两列
--    列顺序对齐 schema_email_bind.sql 中的定义（scene 之后）
ALTER TABLE email_verify_code
    ADD COLUMN openid VARCHAR(64) NULL COMMENT '发起验证时的微信OpenID' AFTER scene,
    ADD COLUMN openid_type VARCHAR(10) NULL COMMENT 'openid类型: mini-小程序, gzh-公众号' AFTER openid;
