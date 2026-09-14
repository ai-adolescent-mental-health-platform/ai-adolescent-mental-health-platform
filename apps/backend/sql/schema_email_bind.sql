-- ================================================================
-- 邮箱绑定 - 邮箱验证码表
-- wx_gzh_id / email_verified 已合入 schema.sql，此处仅保留建表
-- 说明：openid / openid_type 两列已于 2026-09-14 移除 ——
--   EmailVerifyCode 实体中无对应字段，后端与前端均零引用，
--   属微信模块残留。
-- ================================================================

-- 邮箱验证码表
CREATE TABLE IF NOT EXISTS email_verify_code (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    email         VARCHAR(100) NOT NULL COMMENT '邮箱地址',
    code          VARCHAR(6) NOT NULL COMMENT '6位验证码',
    scene         VARCHAR(20) NOT NULL COMMENT '场景: bind_email-绑定邮箱',
    expire_time   DATETIME NOT NULL COMMENT '过期时间',
    used          TINYINT(1) DEFAULT 0 COMMENT '是否已使用(0-否,1-是)',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_email_scene (email, scene),
    INDEX idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱验证码表';
