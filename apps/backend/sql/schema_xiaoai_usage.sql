-- 用户会员信息表（小艾每日时长配额）
--
-- 说明：原同文件的 user_usage_time 建表已于 2026-09-14 移除。
-- 该表属旧实现残留：UsageTimeService 是无实现类的空接口、
-- UserUsageTimeMapper 无任何调用方；小艾时长功能实际由
-- XiaoaiRecordServiceImpl 经 xiaoai_usage_stat 表实现。
CREATE TABLE IF NOT EXISTS user_membership (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    member_type INT DEFAULT 0 COMMENT '会员类型：0-非会员, 1-VIP, 2-SVIP',
    member_expire_time DATETIME COMMENT '会员过期时间',
    daily_limit_seconds INT DEFAULT 300 COMMENT '每日时长限制（秒）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会员信息表';

-- 初始化数据（测试用）
INSERT INTO user_membership (user_id, member_type, daily_limit_seconds)
VALUES (1, 0, 300)  -- 普通用户，每天300秒
ON DUPLICATE KEY UPDATE member_type = VALUES(member_type);
