-- ============================================================
-- 每日签到与情绪预警模块 建表脚本
--
-- 范围：5 张表
--   checkin_mood_tag       情绪标签字典（平台预置）
--   user_checkin           每日签到记录（一人一天一条）
--   user_checkin_mood_tag  签到-标签关联
--   checkin_analysis       签到AI分析结果（任务状态机+结果合并）
--   risk_alert             风险预警记录（通用，多源共用）
--
-- 设计文档：docs/superpowers/specs/2026-09-16-checkin-module-design.md
-- 约定：表名统一带 xinyuzhilian. 前缀，与 schema.sql 保持一致
--       （不写 USE 语句）。
-- ============================================================

-- 情绪标签字典表
-- 仿 consultation_field 的字典表范式：name/code/icon/sort_order/status
create table if not exists xinyuzhilian.checkin_mood_tag
(
    id          int auto_increment comment '主键ID' primary key,
    name        varchar(20)  not null comment '标签名称',
    code        varchar(30)  not null comment '标签代码',
    icon        varchar(100) null comment 'lucide图标名(前端静态映射,不存emoji)',
    tone        varchar(20)  null comment 'pouf色调(pink/purple/blue/mint/yellow/orange)',
    polarity    tinyint      not null default 0 comment '情绪极性(-2-很负面,-1-负面,0-中性,1-正面,2-很正面)',
    status      tinyint          default 1 null comment '状态(0-禁用,1-启用)',
    sort_order  int              default 0 null comment '排序',
    create_time datetime         default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_code unique (code)
) comment '签到情绪标签表';

-- 每日签到记录表
create table if not exists xinyuzhilian.user_checkin
(
    id             bigint auto_increment comment '主键ID' primary key,
    user_id        bigint       not null comment '用户ID',
    checkin_date   date         not null comment '签到日期',
    mood_polarity  tinyint      null comment '当日情绪极性(所选标签polarity均值,写入时冗余)',
    diary_content  text         null comment '日记/困惑/难处/开心事原文(可空,允许跳过)',
    content_length int          default 0 null comment '正文字数(冗余,列表页免count)',
    create_time    datetime     default CURRENT_TIMESTAMP null comment '创建时间',
    update_time    datetime     default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP null comment '更新时间',
    constraint uk_user_checkin_date unique (user_id, checkin_date)
) comment '每日签到记录表';

create index idx_checkin_date on xinyuzhilian.user_checkin (checkin_date);

-- 签到-情绪标签关联表
create table if not exists xinyuzhilian.user_checkin_mood_tag
(
    id          bigint auto_increment comment '主键ID' primary key,
    checkin_id  bigint not null comment '签到记录ID',
    tag_id      int    not null comment '情绪标签ID',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_checkin_tag unique (checkin_id, tag_id)
) comment '签到-情绪标签关联表';

create index idx_tag_id on xinyuzhilian.user_checkin_mood_tag (tag_id);

-- 签到AI分析结果表(任务状态机与结果合并)
create table if not exists xinyuzhilian.checkin_analysis
(
    id                bigint auto_increment comment '主键ID' primary key,
    checkin_id        bigint       not null comment '签到记录ID',
    user_id           bigint       not null comment '用户ID(冗余,供家长端与运营按用户查询)',
    status            tinyint      not null default 0 comment '状态(0-待处理,1-处理中,2-成功,3-失败)',
    risk_level        tinyint      null comment '预警等级(0-平稳,1-关注,2-危机);失败时为NULL,禁止默认0',
    user_feedback     text         null comment '面向用户的共情反馈全文',
    suggestion        varchar(500) null comment '面向用户的建议',
    risk_reason       varchar(500) null comment '判定理由(平台侧专用,不向用户展示)',
    model             varchar(50)  null comment '实际使用的模型名',
    prompt_tokens     int          default 0 null comment '输入token数',
    completion_tokens int          default 0 null comment '输出token数',
    retry_count       int          default 0 null comment '已重试次数',
    error_msg         varchar(500) null comment '失败原因(截断存储)',
    started_at        datetime     null comment '开始处理时间',
    finished_at       datetime     null comment '处理完成时间',
    create_time       datetime     default CURRENT_TIMESTAMP null comment '创建时间',
    update_time       datetime     default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP null comment '更新时间',
    constraint uk_checkin_id unique (checkin_id)
) comment '签到AI分析结果表';

create index idx_user_create on xinyuzhilian.checkin_analysis (user_id, create_time);
create index idx_status_create on xinyuzhilian.checkin_analysis (status, create_time);

-- 风险预警记录表(通用,多源共用)
create table if not exists xinyuzhilian.risk_alert
(
    id           bigint auto_increment comment '主键ID' primary key,
    user_id      bigint       not null comment '用户ID',
    source_type  varchar(20)  not null comment '来源类型(CHECKIN-签到,ASSESSMENT-量表,XIAOAI-小爱倾听)',
    source_id    bigint       not null comment '来源记录ID',
    level        tinyint      not null comment '预警等级(2-危机,当前仅危机级入表,保留该列以兼容后续多源刻度)',
    trigger_type varchar(20)  not null default 'MODEL' comment '触发方式(MODEL-模型判定,KEYWORD-关键词兜底,MANUAL-人工)',
    reason       varchar(500) null comment '判定理由(平台侧)',
    evidence     varchar(500) null comment '触发该预警的原文摘录(供平台与家长端展示)',
    status       tinyint      default 0 null comment '处置状态(0-待处置,1-处置中,2-已处置,3-已忽略)',
    handler_id   bigint       null comment '处置人ID',
    handle_note  varchar(500) null comment '处置备注',
    handled_at   datetime     null comment '处置时间',
    create_time  datetime     default CURRENT_TIMESTAMP null comment '创建时间',
    update_time  datetime     default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP null comment '更新时间',
    constraint uk_source unique (source_type, source_id)
) comment '风险预警记录表';

create index idx_user_level on xinyuzhilian.risk_alert (user_id, level);
create index idx_status_create on xinyuzhilian.risk_alert (status, create_time);
