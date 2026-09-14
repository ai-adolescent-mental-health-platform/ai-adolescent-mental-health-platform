-- ============================================================
-- 审计修复迁移脚本 2026-09-11
-- 修复项：金额精度、余额乐观锁、重复索引、抽成比例统一
-- ============================================================

-- 1. psychologist 表：金额字段 VARCHAR→DECIMAL
ALTER TABLE `psychologist`
    MODIFY COLUMN `consultation_price` DECIMAL(10,2) DEFAULT 0.00 COMMENT '咨询价格(元/次)';
ALTER TABLE `psychologist`
    MODIFY COLUMN `offline_price` DECIMAL(10,2) DEFAULT 0.00 COMMENT '线下咨询价格(元/次)';

-- 2. psychologist_balance 表：加 version 乐观锁
ALTER TABLE `psychologist_balance`
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `frozen_amount`;

-- 3. psychologist_income 表：commission_rate 统一为 DECIMAL(5,4)（存0-1小数）
--    注意：需要先将已有百分比数据转换为小数（如 15.00 → 0.1500）
UPDATE `psychologist_income` SET `commission_rate` = `commission_rate` / 100 WHERE `commission_rate` > 1;
ALTER TABLE `psychologist_income`
    MODIFY COLUMN `commission_rate` DECIMAL(5,4) NOT NULL COMMENT '抽成比例(0-1小数)';

-- 4. ai_message 表：删除重复索引
DROP INDEX `id_session_id` ON `ai_message`;

-- 5. article_comment 表：补索引（高频查询路径）
CREATE INDEX `idx_article_id` ON `article_comment` (`article_id`);
