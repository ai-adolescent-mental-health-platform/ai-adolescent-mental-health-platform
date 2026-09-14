-- ============================================================
-- 字典种子数据
--
-- 来源：原 apps/backend/sql/psychologist_module.sql 与
--       apps/backend/sql/schema_phase9_social_features.sql
--       （2026-09-14 归档清理时抽取；这两个文件已删除，
--         完整内容见 git 历史中的对应提交）
--
-- 为什么单独存在：schema.sql 只含表结构、不含任何 INSERT，
--   而这三张字典表的数据是后端 DictDataServiceImpl
--   （ApplicationRunner，启动时加载并缓存到 Redis）所需的运行时数据。
--   表为空会导致前端「咨询领域」「资质类型」等下拉框空白。
--
-- 幂等性：三张表均有 uk_code(code) 唯一约束，
--   下方 ON DUPLICATE KEY UPDATE 保证可重复执行，
--   且只覆盖 name，不会把运维手工禁用的 status 改回启用。
--
-- 注：同批删除的 schema_phase9_social_features.sql 中另有两条
--   针对存量用户的回填 INSERT（user_stats / user_privacy_setting，
--   形如 INSERT ... SELECT id FROM user WHERE deleted = 0）。
--   它们依赖 user 表既有数据、属一次性数据迁移而非种子数据，
--   故未纳入本文件，需要时请从 git 历史取回。
-- ============================================================

USE xinyuzhilian;

-- 咨询领域（前端领域筛选，psychologist 服务范围）
-- status / create_time 依赖表默认值（status 默认 1）
INSERT INTO `consultation_field` (`name`, `code`, `icon`, `description`, `sort_order`) VALUES
('情感婚恋', 'EMOTIONAL', 'heart', '恋爱、婚姻、家庭情感问题', 1),
('职场压力', 'WORKPLACE', 'briefcase', '职业发展、工作压力、职业规划', 2),
('学业困扰', 'ACADEMIC', 'book', '学习困难、考试焦虑、升学压力', 3),
('人际关系', 'RELATIONSHIP', 'users', '人际交往、社交恐惧、沟通障碍', 4),
('情绪管理', 'EMOTION', 'smile', '焦虑、抑郁、愤怒、压力管理', 5),
('青少年心理', 'ADOLESCENT', 'child', '青少年成长问题、亲子关系', 6)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 心理咨询师资质类型（前端资质下拉）
INSERT INTO `psychologist_qualification` (`name`, `code`, `description`, `sort_order`) VALUES
('国家二级心理咨询师', 'NATIONAL_LEVEL2', '国家职业资格二级心理咨询师', 1),
('国家三级心理咨询师', 'NATIONAL_LEVEL3', '国家职业资格三级心理咨询师', 2),
('临床心理学硕士', 'CLINICAL_MASTER', '临床心理学硕士学位', 3),
('心理学博士', 'PSYCHOLOGY_PHD', '心理学博士学位', 4),
('注册心理师', 'REGISTERED', '中国心理学会注册心理师', 5),
('实习咨询师', 'INTERN', '实习咨询师', 6)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 文章标签
INSERT INTO `article_tag` (`name`, `code`, `sort_order`, `status`) VALUES
('科普', 'SCIENCE', 1, 1),
('案例', 'CASE', 2, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
