create table if not exists xinyuzhilian.ai_message
(
    id          bigint auto_increment
        primary key,
    session_id  bigint                             not null,
    role        varchar(20)                        not null,
    content     text                               not null,
    create_time datetime default CURRENT_TIMESTAMP null
);


create index idx_session_id
    on xinyuzhilian.ai_message (session_id);

create table if not exists xinyuzhilian.ai_session
(
    id          bigint auto_increment
        primary key,
    user_id     bigint                             not null,
    title       varchar(255)                       null,
    create_time datetime default CURRENT_TIMESTAMP null,
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
);

create index idx_user_id
    on xinyuzhilian.ai_session (user_id);


create table if not exists xinyuzhilian.article
(
    id               bigint auto_increment comment '主键ID'
        primary key,
    title            varchar(255)                          not null comment '文章标题',
    content          longtext                              null comment '文章内容(富文本)',
    cover_url        varchar(255)                          null comment '封面图URL',
    type             varchar(20) default 'SCIENCE'         null comment '类型(SCIENCE-科普, CASE-案例)',
    status           int         default 1                 null comment '状态(0-草稿, 1-已发布)',
    author_id        bigint                                null comment '作者ID',
    create_time      datetime    default CURRENT_TIMESTAMP null,
    update_time      datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    like_count       int         default 0                 null comment '点赞数',
    dislike_count    int         default 0                 null comment '踩数',
    collection_count int         default 0                 null comment '收藏数',
    comment_count    int         default 0                 null comment '评论数',
    view_count       int         default 0                 null comment '阅读数'
)
    comment '心理文章表';

create table if not exists xinyuzhilian.assessment_record
(
    id              bigint auto_increment comment '主键ID'
        primary key,
    user_id         bigint                             not null comment '用户ID',
    template_id     bigint                             not null comment '模板ID',
    answers_json    json                               not null comment '用户作答JSON',
    result_score    int      default 0                 null comment '总分',
    result_analysis text                               null comment '结果分析结论',
    create_time     datetime default CURRENT_TIMESTAMP null,
    patient_contact_id bigint                          null comment '就诊人ID'
)
    comment '测评记录表';

create table if not exists xinyuzhilian.assessment_template
(
    id                 bigint auto_increment comment '主键ID'
        primary key,
    title              varchar(100)                          not null comment '量表标题',
    description        text                                  null comment '量表描述',
    type               varchar(20) default 'TRADITIONAL'     null comment '类型(TRADITIONAL-传统, QUICK-快速, DYNAMIC-动态)',
    questions_json     json                                  not null comment '题目配置JSON',
    scoring_rules_json json                                  null comment '计分规则JSON',
    status             int         default 1                 null comment '状态(0-停用, 1-启用)',
    create_time        datetime    default CURRENT_TIMESTAMP null,
    is_public          int         default 1                 null comment '是否公开(0-仅医生可见, 1-公开)',
    cover_url          varchar(255)                          null comment '封面图'
)
    comment '测评量表模板表';





create table if not exists xinyuzhilian.course
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    title       varchar(255)                          not null comment '课程标题',
    description text                                  null comment '课程简介',
    media_url   varchar(255)                          not null comment '媒体资源URL(视频/音频)',
    cover_url   varchar(255)                          null comment '封面图URL',
    type        varchar(20) default 'VIDEO'           null comment '类型(VIDEO-视频, AUDIO-音频)',
    status      int         default 1                 null comment '状态(0-下架, 1-上架)',
    create_time datetime    default CURRENT_TIMESTAMP null,
    update_time datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
)
    comment '心理课程表';

create table if not exists xinyuzhilian.course_source
(
    id               bigint auto_increment
        primary key,
    course_id        bigint                             not null comment '课程ID',
    source_type      varchar(20)                        not null comment '来源类型(third_party, self_hosted)',
    source_name      varchar(50)                        null comment '第三方平台名称',
    source_url       varchar(500)                       not null comment '视频地址',
    storage_provider varchar(20)                        null comment '存储提供商(oss, local, cdn)',
    create_time      datetime default CURRENT_TIMESTAMP null
)
    comment '课程资源表';

create index idx_course_id
    on xinyuzhilian.course_source (course_id);

create table if not exists xinyuzhilian.cover_image
(
    id             bigint auto_increment
        primary key,
    course_id      bigint                             not null comment '课程ID',
    cover_type     varchar(20)                        not null comment '封面来源(third_party, self_hosted)',
    cover_url_avif varchar(500)                       null comment 'AVIF格式地址',
    cover_url_webp varchar(500)                       null comment 'WebP格式地址',
    cover_url_jpeg varchar(500)                       null comment 'JPEG格式地址',
    md5_hash       varchar(32)                        null comment 'MD5校验值',
    create_time    datetime default CURRENT_TIMESTAMP null
)
    comment '课程封面图表';

create index idx_course_id
    on xinyuzhilian.cover_image (course_id);







create table if not exists xinyuzhilian.inspirational_quote
(
    id          bigint auto_increment
        primary key,
    content     varchar(500)                       not null comment '语录内容',
    author      varchar(50)                        null comment '作者/出处',
    create_time datetime default CURRENT_TIMESTAMP null,
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
)
    comment '每日正能量语录';


create table if not exists xinyuzhilian.platform_feedback
(
    id            bigint auto_increment
        primary key,
    user_id       bigint                             not null comment '用户ID',
    content       text                               not null comment '反馈内容',
    status        int      default 0                 null comment '状态: 0-已反馈, 1-待解决, 2-已解决, 3-已取消',
    cancel_reason varchar(255)                       null comment '取消理由',
    create_time   datetime default CURRENT_TIMESTAMP null,
    update_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
)
    comment '平台反馈表';

create index idx_user_id
    on xinyuzhilian.platform_feedback (user_id);

create table if not exists xinyuzhilian.user
(
    id                 bigint auto_increment comment '主键ID'
        primary key,
    username           varchar(50)                          not null comment '用户名',
    password           varchar(100)                         not null comment '密码(加密)',
    role               int        default 0                 null comment '角色(0-游客, 1-用户, 2-医生, 3-医院管理员, 4-超级管理员)',
    nickname           varchar(50)                          null comment '昵称',
    phone              varchar(20)                          null comment '手机号',
    email              varchar(255)                         not null comment '邮箱（登录账号）',
    is_psychologist    tinyint    default 0                 null comment '是否为心理咨询师(0-否,1-是)',
    email_verified     tinyint(1) default 0                 null comment '邮箱是否已验证(0-否,1-是)',
    email_change_count int        default 0                 not null comment '当月邮箱修改次数',
    email_change_date  date                                 null comment '最近修改邮箱的日期',
    head_path          varchar(255)                         null comment '头像路径',
    signature          varchar(255)                         null comment '个性签名',
    sex                int        default 0                 null comment '性别(0-未知, 1-男, 2-女)',
    birthday           date                                 null comment '出生日期',
    status             int        default 1                 null comment '状态(0-禁用, 1-正常, 2-冻结)',
    deleted            tinyint(1) default 0                 null comment '逻辑删除(0-未删除, 1-已删除)',
    create_time        datetime   default CURRENT_TIMESTAMP null comment '创建时间',
    update_time        datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    member_type        tinyint(1) default 0                 null comment '会员类型(0-非会员,1-VIP,2-SVIP)',
    member_expire_date datetime                             null comment '会员过期时间',
    constraint uk_username
        unique (username),
    constraint uk_email
        unique (email),
    constraint uk_phone
        unique (phone)
)
    comment '用户信息表';

create table if not exists xinyuzhilian.user_course_progress
(
    id              bigint auto_increment
        primary key,
    user_id         bigint                             not null comment '用户ID',
    course_id       bigint                             not null comment '课程ID',
    progress        int      default 0                 null comment '观看进度(百分比0-100)',
    last_watch_time datetime default CURRENT_TIMESTAMP null comment '最近观看时间',
    constraint uk_user_course
        unique (user_id, course_id)
)
    comment '用户课程进度表';

create table if not exists xinyuzhilian.ai_xiaoai_message
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    session_id  bigint                             not null comment '会话ID(ai_xiaoai_session.id)',
    role        varchar(20)                        not null comment '角色: user/assistant',
    content     text                               null comment '文本内容',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '小爱倾听-对话消息记录表';

create index idx_create_time
    on xinyuzhilian.ai_xiaoai_message (create_time);

create index idx_session_id
    on xinyuzhilian.ai_xiaoai_message (session_id);

create table if not exists xinyuzhilian.ai_xiaoai_session
(
    id            bigint auto_increment comment '主键ID'
        primary key,
    user_id       bigint                                 not null comment '用户ID',
    start_time    datetime                               not null comment '会话开始时间',
    end_time      datetime                               null comment '会话结束时间',
    total_seconds int          default 0                 null comment '本次会话总时长(秒)',
    end_reason    varchar(32)                            null comment '结束原因: timeout/manual/error/expired',
    status        tinyint      default 1                 null comment '状态: 1-进行中, 0-已结束',
    create_time   datetime     default CURRENT_TIMESTAMP null comment '创建时间',
    update_time   datetime     default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '小爱倾听-会话记录表';

create index idx_start_time
    on xinyuzhilian.ai_xiaoai_session (start_time);

create index idx_status
    on xinyuzhilian.ai_xiaoai_session (status);

create index idx_user_id
    on xinyuzhilian.ai_xiaoai_session (user_id);

create table if not exists xinyuzhilian.article_audit_log
(
    id           bigint auto_increment
        primary key,
    article_id   bigint                             not null comment '文章ID',
    article_type int                                not null comment '文章类型(1-管理员文章,2-用户文章)',
    user_id      bigint                             not null comment '发布者ID',
    auditor_id   bigint                             null comment '审核人ID',
    action       int                                not null comment '操作(1-通过,2-拒绝,3-下架)',
    reason       varchar(500)                       null comment '原因',
    create_time  datetime default CURRENT_TIMESTAMP null
)
    comment '文章审核记录';

create index idx_article_id
    on xinyuzhilian.article_audit_log (article_id);

create index idx_auditor_id
    on xinyuzhilian.article_audit_log (auditor_id);

create table if not exists xinyuzhilian.article_comment
(
    id               bigint auto_increment
        primary key,
    article_id       bigint                             not null comment '文章ID',
    user_id          bigint                             not null comment '用户ID',
    parent_id        bigint   default 0                 null comment '父评论ID(回复评论时使用)',
    reply_to_user_id bigint                             null comment '被回复人用户ID',
    content          varchar(1000)                      not null comment '评论内容',
    like_count       int      default 0                 null comment '点赞数',
    create_time      datetime default CURRENT_TIMESTAMP null,
    update_time      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
)
    comment '文章评论表';

create index idx_article_id
    on xinyuzhilian.article_comment (article_id);

create table if not exists xinyuzhilian.article_interaction
(
    id          bigint auto_increment
        primary key,
    article_id  bigint                             not null comment '文章ID',
    user_id     bigint                             not null comment '用户ID',
    type        int                                not null comment '类型(1-点赞, 2-踩, 3-收藏)',
    create_time datetime default CURRENT_TIMESTAMP null,
    constraint uk_user_article_type
        unique (user_id, article_id, type)
)
    comment '文章互动表';

create table if not exists xinyuzhilian.article_tag
(
    id          bigint auto_increment
        primary key,
    name        varchar(50)                        not null comment '标签名称',
    code        varchar(50)                        not null comment '标签编码',
    sort_order  int      default 0                 null comment '排序',
    status      int      default 1                 null comment '状态(0-禁用,1-启用)',
    create_time datetime default CURRENT_TIMESTAMP null,
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_code
        unique (code)
)
    comment '文章标签表';

create table if not exists xinyuzhilian.book
(
    id          bigint auto_increment
        primary key,
    title       varchar(200)                       not null comment '书籍标题',
    cover_url   varchar(500)                       null comment '封面图片URL',
    description text                               null comment '书籍简介',
    address     varchar(500)                       null comment '跳转地址',
    view_count  int      default 0                 null comment '浏览数',
    comment_count int    default 0                 null comment '评论数',
    sort_order  int      default 0                 null comment '排序权重',
    status      tinyint  default 1                 null comment '状态 0下架 1上架',
    create_time datetime default CURRENT_TIMESTAMP null,
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    deleted     tinyint  default 0                 null
);

create table if not exists xinyuzhilian.book_comment
(
    id          bigint auto_increment
        primary key,
    book_id     bigint                             not null comment '书籍ID',
    user_id     bigint                             not null comment '用户ID',
    content     text                               not null comment '评论内容',
    create_time datetime default CURRENT_TIMESTAMP null,
    deleted     tinyint  default 0                 null
);

create table if not exists xinyuzhilian.comment_like
(
    id          bigint auto_increment
        primary key,
    comment_id  bigint                             not null comment '评论ID',
    user_id     bigint                             not null comment '用户ID',
    create_time datetime default CURRENT_TIMESTAMP null,
    constraint uk_user_comment
        unique (user_id, comment_id)
)
    comment '评论点赞表';


create table if not exists xinyuzhilian.consultation_field
(
    id          int auto_increment comment '主键ID'
        primary key,
    name        varchar(50)                        not null comment '领域名称',
    code        varchar(50)                        not null comment '领域代码',
    icon        varchar(100)                       null comment '图标',
    description varchar(500)                       null comment '描述',
    status      tinyint  default 1                 null comment '状态(0-禁用，1-启用)',
    sort_order  int      default 0                 null comment '排序',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_code
        unique (code)
)
    comment '咨询领域类型表';


create table if not exists xinyuzhilian.course_category
(
    id          bigint auto_increment
        primary key,
    name        varchar(50)                        not null comment '分类名称',
    code        varchar(50)                        not null comment '分类编码(关联course.type)',
    icon        varchar(255)                       null comment '分类图标',
    sort_order  int      default 0                 null comment '排序',
    status      int      default 1                 null comment '状态(0-禁用,1-启用)',
    is_system   int      default 0                 null comment '是否系统内置(0-否,1-是)',
    create_time datetime default CURRENT_TIMESTAMP null,
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_code
        unique (code)
)
    comment '课程分类表';

create table if not exists xinyuzhilian.hot_search
(
    id          bigint auto_increment
        primary key,
    keyword     varchar(100)                       not null comment '搜索关键词',
    search_count int     default 0                 null comment '搜索次数',
    type        varchar(20) default 'all'          null comment '关联类型',
    status      tinyint  default 1                 null comment '状态: 0-禁用 1-启用',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint keyword
        unique (keyword)
);

create index idx_search_count
    on xinyuzhilian.hot_search (search_count desc);

create index idx_status
    on xinyuzhilian.hot_search (status);






create table if not exists xinyuzhilian.meme
(
    id     bigint auto_increment,
    meme   varchar(255) not null comment '梗',
    `explain` text       null comment '解释',
    constraint meme_pk
        unique (id)
);

create table if not exists xinyuzhilian.patient_contact
(
    id           bigint auto_increment
        primary key,
    user_id      bigint                             not null comment '所属用户ID',
    name         varchar(50)                        not null comment '姓名',
    birthday     date                               not null comment '出生日期',
    sex          int      default 0                 null comment '性别(0-未知, 1-男, 2-女)',
    relationship varchar(50)                        not null comment '与用户关系',
    create_time  datetime default CURRENT_TIMESTAMP null,
    update_time  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    deleted      tinyint(1) default 0               null comment '逻辑删除'
)
    comment '就诊人信息表';

create index idx_user_id
    on xinyuzhilian.patient_contact (user_id);

create table if not exists xinyuzhilian.platform_income
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    income_module     varchar(50)                        not null comment '收入模块：CONSULTATION-心理咨询，MEMBER-会员',
    order_id          bigint                             null comment '关联订单号(心理咨询模块对应预约ID)',
    psychologist_id   bigint                             null comment '心理咨询师ID(心理咨询模块时不为空)',
    user_id           bigint                             null comment '用户ID',
    order_fee         decimal(10, 2)                     null comment '订单总金额',
    commission_rate   decimal(5, 4)                      null comment '抽成比例(0-1之间的小数)',
    commission_amount decimal(10, 2)                     null comment '平台抽成金额',
    rating_score      decimal(3, 1)                      null comment '用户评分(1-5，心理咨询模块有效)',
    remark            varchar(500)                       null comment '备注/描述',
    create_time       datetime                           null comment '创建时间',
    update_time       datetime                           null comment '更新时间'
)
    comment '平台收入表';

create index idx_create_time
    on xinyuzhilian.platform_income (create_time);

create index idx_income_module
    on xinyuzhilian.platform_income (income_module);

create index idx_order_id
    on xinyuzhilian.platform_income (order_id);

create index idx_psychologist_id
    on xinyuzhilian.platform_income (psychologist_id);

create index idx_user_id
    on xinyuzhilian.platform_income (user_id);

create table if not exists xinyuzhilian.psychologist
(
    id                   bigint auto_increment comment '主键ID，自增'
        primary key,
    user_id              bigint                                   not null comment '关联用户ID，唯一',
    real_name            varchar(50)                              not null comment '真实姓名',
    sex                  tinyint                                  null comment '性别(1-男，2-女)',
    head_path            varchar(500)                             null comment '头像路径',
    introduction         text                                     null comment '详细个人介绍',
    education_background text                                     null comment '教育背景',
    training_experience  text                                     null comment '受训经历',
    certifications       text                                     null comment '专业认证(JSON数组)',
    years_experience     int            default 0                 null comment '咨询经验年限',
    consultation_price   decimal(10, 2)    default 0.00               null comment '咨询价格(元/次)',
    offline_price        decimal(10, 2)    default 0.00               null comment '线下咨询价格(元/次)',
    rating_score         decimal(3, 2)  default 0.00              null comment '用户评分(0-5)',
    rating_count         int            default 0                 null comment '评分次数',
    consultation_count   int            default 0                 null comment '咨询接单量',
    offline_region       varchar(200)                             null comment '线下咨询地区',
    offline_address      varchar(500)                             null comment '详细地址',
    languages            varchar(200)                             null comment '语言能力(JSON数组：普通话、方言、外语)',
    status               tinyint        default 1                 null comment '状态(0-禁用，1-启用)',
    online_status        tinyint        default 0                 null comment '在线状态(0-离线，1-在线，2-忙碌)',
    audit_status         tinyint        default 0                 null comment '资料审核状态(0-无需审核，1-待审核，2-审核中，3-已通过，4-已拒绝)',
    create_time          datetime       default CURRENT_TIMESTAMP null comment '创建时间',
    update_time          datetime       default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_id
        unique (user_id)
)
    comment '心理咨询师基础信息表';

create index idx_consultation_count
    on xinyuzhilian.psychologist (consultation_count);

create index idx_rating
    on xinyuzhilian.psychologist (rating_score);

create index idx_status
    on xinyuzhilian.psychologist (status);

create table if not exists xinyuzhilian.psychologist_apply
(
    id                    bigint auto_increment
        primary key,
    user_id               bigint                             not null comment '用户ID',
    apply_count           int      default 1                 null comment '申请次数(第几次申请)',
    status                varchar(20) default 'FILLING'      null comment '状态(FILLING-填写资料,REVIEWING-管理员审核,PAPER-笔试考核,REPORT-案例报告,INTERVIEW-线下面谈,APPROVED-入驻成功,REJECTED-入驻失败)',
    step                  varchar(20) default 'BASIC'        null comment '当前步骤(BASIC-基本资料,PAPER-笔试,REPORT-案例报告,INTERVIEW-面谈)',
    real_name             varchar(50)                        null comment '真实姓名',
    phone                 varchar(20)                        null comment '手机号',
    country               varchar(50) default '中国'          null comment '国家/地区',
    contact_wechat        varchar(100)                       null comment '微信/邮箱联系方式',
    case_hours            varchar(20)                        null comment '咨询个案时长',
    supervision_hours     varchar(20)                        null comment '个体督导时长',
    consultation_price    decimal(10, 2)                     null comment '咨询定价',
    resume_url            varchar(500)                       null comment '个人简历附件URL',
    education             varchar(100)                       null comment '学历及相关专业',
    qualification_urls    json                               null comment '专业资质材料附件(JSON数组)',
    supervision_proof_urls json                              null comment '督导证明附件(JSON数组)',
    experience_proof_urls json                               null comment '个人体验证明附件(JSON数组)',
    other_proof_urls      json                               null comment '其他相关证明附件(JSON数组)',
    self_narration        text                               null comment '个人自我叙述',
    reject_reason         varchar(500)                       null comment '拒绝/失败原因',
    exam_deadline         datetime                           null comment '笔试截止时间',
    paper_result          int      default -1                null comment '笔试结果(-1-未开始,0-进行中,1-通过,2-失败)',
    report_result         int      default -1                null comment '案例报告结果(-1-未提交,0-待审核,1-通过,2-失败)',
    interview_result      int      default -1                null comment '面谈结果(-1-未开始,0-待审核,1-通过,2-失败)',
    interview_time        datetime                           null comment '面谈时间',
    interview_location    varchar(255)                       null comment '面谈地点',
    reviewer_id           bigint                             null comment '审核人ID',
    review_time           datetime                           null comment '审核时间',
    create_time           datetime default CURRENT_TIMESTAMP null,
    update_time           datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
)
    comment '心理咨询师入驻申请表';

create index idx_status
    on xinyuzhilian.psychologist_apply (status);

create index idx_user_id
    on xinyuzhilian.psychologist_apply (user_id);

create table if not exists xinyuzhilian.psychologist_apply_count
(
    user_id             bigint                             not null comment '用户ID'
        primary key,
    apply_count         int      default 0                 null comment '累计申请次数',
    last_apply_time     datetime                           null comment '最后申请时间',
    permanently_rejected tinyint default 0                 null comment '是否永久拒绝(0-否,1-是)'
)
    comment '心理咨询师申请次数记录表';

create table if not exists xinyuzhilian.psychologist_appointment
(
    id                    bigint auto_increment comment '主键ID'
        primary key,
    order_no              varchar(32)                                not null comment '订单编号',
    user_id               bigint                                     not null comment '用户ID',
    psychologist_id       bigint                                     not null comment '心理咨询师ID',
    schedule_id           bigint                                     null comment '排班ID',
    service_type          varchar(20)                                not null comment '服务类型',
    appointment_time      datetime                                   not null comment '预约时间',
    fee                   decimal(10, 2)                             not null comment '费用',
    pay_status            tinyint        default 0                   null comment '支付状态(0-未支付，1-已支付，2-已退款)',
    pay_time              datetime                                   null comment '支付时间',
    status                tinyint        default 0                   null comment '预约状态(0-待审核，1-已确认，2-已拒绝，3-进行中，4-已完成，5-已取消，6-已爽约)',
    reject_reason         varchar(500)                               null comment '拒绝原因',
    video_link            varchar(500)                               null comment '视频会议链接',
    start_time            datetime                                   null comment '咨询开始时间',
    end_time              datetime                                   null comment '咨询结束时间',
    user_basic_info       text                                       null comment '用户基本情况(JSON)',
    user_problems         text                                       null comment '用户困扰描述',
    user_experience       text                                       null comment '过往经历',
    user_health           text                                       null comment '健康状况',
    consultation_content  text                                       null comment '咨询内容记录',
    is_rated              tinyint        default 0                   null comment '是否已评分(0-否，1-是)',
    rating_score          decimal(3, 2)                              null comment '评分',
    rating_content        text                                       null comment '评价内容',
    rating_time           datetime                                   null comment '评分时间',
    complete_time         datetime                                   null comment '完成时间',
    create_time           datetime       default CURRENT_TIMESTAMP   null comment '创建时间',
    update_time           datetime       default CURRENT_TIMESTAMP   null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_order_no
        unique (order_no)
)
    comment '心理咨询预约订单表';

create index idx_appointment_time
    on xinyuzhilian.psychologist_appointment (appointment_time);

create index idx_psychologist_id
    on xinyuzhilian.psychologist_appointment (psychologist_id);

create index idx_status
    on xinyuzhilian.psychologist_appointment (status);

create index idx_user_id
    on xinyuzhilian.psychologist_appointment (user_id);

create table if not exists xinyuzhilian.psychologist_balance
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    psychologist_id   bigint                                   not null comment '心理咨询师ID，唯一',
    total_income      decimal(12, 2) default 0.00              null comment '累计收入',
    total_withdraw    decimal(12, 2) default 0.00              null comment '累计提现',
    balance           decimal(12, 2) default 0.00              null comment '可用余额',
    frozen_amount     decimal(12, 2) default 0.00              null comment '冻结金额',
    version           int            default 0                 not null comment '乐观锁版本号',
    create_time       datetime       default CURRENT_TIMESTAMP null comment '创建时间',
    update_time       datetime       default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_psychologist_id
        unique (psychologist_id)
)
    comment '心理咨询师余额表';

create table if not exists xinyuzhilian.psychologist_field_relation
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    psychologist_id   bigint                             not null comment '心理咨询师ID',
    field_id          int                                not null comment '领域ID',
    sub_tags          varchar(500)                       null comment '细分标签(JSON数组：考试焦虑、宿舍关系等)',
    create_time       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_psychologist_field
        unique (psychologist_id, field_id)
)
    comment '心理咨询师-擅长领域关联表';

create index idx_field_id
    on xinyuzhilian.psychologist_field_relation (field_id);

create index idx_psychologist_id
    on xinyuzhilian.psychologist_field_relation (psychologist_id);

create table if not exists xinyuzhilian.psychologist_income
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    psychologist_id   bigint                             not null comment '心理咨询师ID',
    appointment_id    bigint                             not null comment '预约ID',
    order_fee         decimal(10, 2)                     not null comment '订单金额',
    commission_rate   decimal(5, 4)                      not null comment '抽成比例(0-1小数)',
    commission_amount decimal(10, 2)                     not null comment '抽成金额',
    income_amount     decimal(10, 2)                     not null comment '实际收入',
    rating_score      decimal(3, 2)                      null comment '用户评分',
    create_time       datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '心理咨询师收入表';

create index idx_create_time
    on xinyuzhilian.psychologist_income (create_time);

create index idx_psychologist_id
    on xinyuzhilian.psychologist_income (psychologist_id);

create table if not exists xinyuzhilian.psychologist_message
(
    id             bigint auto_increment comment '主键ID'
        primary key,
    appointment_id bigint                             not null comment '预约ID',
    sender_id      bigint                             not null comment '发送者ID',
    receiver_id    bigint                             not null comment '接收者ID',
    content        text                               null comment '消息内容',
    content_type   tinyint  default 0                 null comment '消息类型(0-文本，1-图片，2-系统消息)',
    create_time    datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '心理咨询聊天消息表';

create index idx_appointment_id
    on xinyuzhilian.psychologist_message (appointment_id);

create index idx_create_time
    on xinyuzhilian.psychologist_message (create_time);

create index idx_sender_id
    on xinyuzhilian.psychologist_message (sender_id);

create table if not exists xinyuzhilian.psychologist_profile_audit
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    psychologist_id   bigint                             not null comment '心理咨询师ID',
    field_name        varchar(50)                        not null comment '变更字段名',
    old_value         text                               null comment '原值',
    new_value         text                               null comment '新值',
    proof_urls        text                               null comment '证明材料URLs(JSON数组)',
    reason            varchar(500)                       null comment '申请理由',
    audit_status      tinyint  default 0                 null comment '审核状态(0-待审核，1-已通过，2-已拒绝)',
    audit_remark      varchar(500)                       null comment '审核备注',
    auditor_id        bigint                             null comment '审核人ID',
    audit_time        datetime                           null comment '审核时间',
    create_time       datetime default CURRENT_TIMESTAMP null comment '申请时间'
)
    comment '心理咨询师资料变更审核表';

create index idx_audit_status
    on xinyuzhilian.psychologist_profile_audit (audit_status);

create index idx_psychologist_id
    on xinyuzhilian.psychologist_profile_audit (psychologist_id);

create table if not exists xinyuzhilian.psychologist_qualification
(
    id          int auto_increment comment '主键ID'
        primary key,
    name        varchar(100)                       not null comment '资质名称',
    code        varchar(50)                        not null comment '资质代码',
    description varchar(500)                       null comment '描述',
    status      tinyint  default 1                 null comment '状态(0-禁用，1-启用)',
    sort_order  int      default 0                 null comment '排序',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_code
        unique (code)
)
    comment '心理咨询师资质类型表';

create table if not exists xinyuzhilian.psychologist_qualification_relation
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    psychologist_id   bigint                             not null comment '心理咨询师ID',
    qualification_id  int                                not null comment '资质ID',
    certificate_url   varchar(500)                       null comment '证书图片URL',
    is_verified       tinyint  default 0                 null comment '是否已认证(0-未认证，1-已认证)',
    create_time       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_psychologist_qualification
        unique (psychologist_id, qualification_id)
)
    comment '心理咨询师-资质关联表';

create index idx_psychologist_id
    on xinyuzhilian.psychologist_qualification_relation (psychologist_id);

create index idx_qualification_id
    on xinyuzhilian.psychologist_qualification_relation (qualification_id);

create table if not exists xinyuzhilian.psychologist_rating
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    appointment_id    bigint                             not null comment '预约ID',
    user_id           bigint                             not null comment '用户ID',
    psychologist_id   bigint                             not null comment '心理咨询师ID',
    rating_score      decimal(3, 2)                      not null comment '评分(1-5)',
    rating_content    text                               null comment '评价内容',
    is_anonymous      tinyint  default 1                 null comment '是否匿名(0-否，1-是)',
    create_time       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_appointment_id
        unique (appointment_id)
)
    comment '心理咨询师评价表';

create index idx_psychologist_id
    on xinyuzhilian.psychologist_rating (psychologist_id);

create index idx_user_id
    on xinyuzhilian.psychologist_rating (user_id);

create table if not exists xinyuzhilian.psychologist_schedule
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    psychologist_id   bigint                             not null comment '心理咨询师ID',
    schedule_date     date                               not null comment '排班日期',
    time_slot         varchar(20)                        not null comment '时段(MORNING-上午，AFTERNOON-下午，EVENING-晚上)',
    start_time        time                               not null comment '开始时间',
    end_time          time                               not null comment '结束时间',
    max_appointments  int      default 1                 null comment '最大预约人数',
    booked_count      int      default 0                 null comment '已预约人数',
    status            tinyint  default 1                 null comment '状态(0-休息，1-可预约)',
    create_time       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_psychologist_date_slot
        unique (psychologist_id, schedule_date, time_slot)
)
    comment '心理咨询师排班表';

create index idx_psychologist_id
    on xinyuzhilian.psychologist_schedule (psychologist_id);

create index idx_schedule_date
    on xinyuzhilian.psychologist_schedule (schedule_date);

create table if not exists xinyuzhilian.psychologist_service
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    psychologist_id   bigint                             not null comment '心理咨询师ID',
    service_type      varchar(20)                        not null comment '服务类型(VIDEO-视频，VOICE-语音，TEXT-文字，OFFLINE-线下)',
    description       varchar(500)                       null comment '服务说明',
    status            tinyint  default 1                 null comment '状态(0-关闭，1-开启)',
    create_time       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_psychologist_service
        unique (psychologist_id, service_type)
)
    comment '心理咨询师咨询方式与价格表';

create index idx_psychologist_id
    on xinyuzhilian.psychologist_service (psychologist_id);

create table if not exists xinyuzhilian.psychologist_withdraw
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    psychologist_id   bigint                             not null comment '心理咨询师ID',
    amount            decimal(10, 2)                     not null comment '提现金额',
    status            tinyint  default 0                 null comment '状态(0-处理中，1-已提现，2-已拒绝)',
    bank_name         varchar(100)                       null comment '银行名称',
    bank_account      varchar(50)                        null comment '银行账号',
    account_name      varchar(50)                        null comment '开户名',
    remark            varchar(500)                       null comment '备注',
    create_time       datetime default CURRENT_TIMESTAMP null comment '申请时间',
    process_time      datetime                           null comment '处理时间'
)
    comment '心理咨询师提现记录表';

create index idx_psychologist_id
    on xinyuzhilian.psychologist_withdraw (psychologist_id);

create index idx_status
    on xinyuzhilian.psychologist_withdraw (status);

create table if not exists xinyuzhilian.search_history
(
    id          bigint auto_increment
        primary key,
    user_id     bigint                             not null comment '用户ID，-1表示未登录用户',
    keyword     varchar(100)                       not null comment '搜索关键词',
    search_type varchar(20) default 'all'          null comment '搜索类型: all/article/course',
    create_time datetime default CURRENT_TIMESTAMP null
);

create index idx_create_time
    on xinyuzhilian.search_history (create_time);

create index idx_keyword
    on xinyuzhilian.search_history (keyword(50));

create index idx_user_id
    on xinyuzhilian.search_history (user_id);

create table if not exists xinyuzhilian.sys_message
(
    id               bigint auto_increment
        primary key,
    user_id          bigint                             not null comment '接收用户ID',
    title            varchar(100)                       not null comment '消息标题',
    content          text                               not null comment '消息内容',
    type             int      default 1                 null comment '类型(1-系统通知,2-文章审核,3-文章下架)',
    is_read          int      default 0                 null comment '是否已读(0-否,1-是)',
    create_time      datetime default CURRENT_TIMESTAMP null,
    source_type      int      default 0                 null comment '来源类型(0-系统,1-关注,2-文章点赞,3-评论点赞,4-评论回复)',
    source_id        bigint                             null comment '来源ID(文章ID/评论ID等)',
    from_user_id     bigint                             null comment '触发通知的用户ID',
    extra_type       int      default 0                 null,
    article_author_id bigint                            null
)
    comment '站内消息表';

create index idx_create_time
    on xinyuzhilian.sys_message (create_time);

create index idx_is_read
    on xinyuzhilian.sys_message (is_read);

create index idx_user_id
    on xinyuzhilian.sys_message (user_id);

create table if not exists xinyuzhilian.user_article
(
    id               bigint auto_increment
        primary key,
    user_id          bigint                             not null comment '用户ID',
    title            varchar(255)                       not null comment '文章标题',
    content          longtext                           null comment '文章内容',
    cover_url        varchar(500)                       null comment '封面图',
    tag_id           bigint                             null comment '标签ID',
    status           int      default 0                 null comment '状态(0-待审核,1-已发布,2-已下架)',
    reject_reason    varchar(500)                       null comment '拒绝/下架原因',
    like_count       int      default 0                 null comment '点赞数',
    dislike_count    int      default 0                 null comment '踩数',
    collection_count int      default 0                 null comment '收藏数',
    comment_count    int      default 0                 null comment '评论数',
    view_count       int      default 0                 null comment '阅读数',
    create_time      datetime default CURRENT_TIMESTAMP null,
    update_time      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
)
    comment '用户文章表';

create index idx_create_time
    on xinyuzhilian.user_article (create_time);

create index idx_status
    on xinyuzhilian.user_article (status);

create index idx_tag_id
    on xinyuzhilian.user_article (tag_id);

create index idx_user_id
    on xinyuzhilian.user_article (user_id);

create table if not exists xinyuzhilian.user_article_comment
(
    id               bigint auto_increment
        primary key,
    article_id       bigint                             not null comment '文章ID',
    user_id          bigint                             not null comment '用户ID',
    parent_id        bigint   default 0                 null comment '父评论ID',
    reply_to_user_id bigint                             null comment '被回复人用户ID',
    content          text                               null comment '评论内容',
    like_count       int      default 0                 null comment '点赞数',
    create_time      datetime                           null,
    update_time      datetime                           null
);

create index idx_article_id
    on xinyuzhilian.user_article_comment (article_id);

create index idx_user_id
    on xinyuzhilian.user_article_comment (user_id);

create table if not exists xinyuzhilian.user_article_comment_like
(
    id          bigint auto_increment
        primary key,
    comment_id  bigint not null comment '评论ID',
    user_id     bigint not null comment '用户ID',
    create_time datetime null
);

create index idx_comment_id
    on xinyuzhilian.user_article_comment_like (comment_id);

create index idx_user_id
    on xinyuzhilian.user_article_comment_like (user_id);

create table if not exists xinyuzhilian.user_favorite_psychologist
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    user_id           bigint                             not null comment '用户ID',
    psychologist_id   bigint                             not null comment '心理咨询师ID',
    create_time       datetime default CURRENT_TIMESTAMP null comment '收藏时间',
    constraint uk_user_psychologist
        unique (user_id, psychologist_id)
)
    comment '用户收藏心理咨询师表';

create index idx_psychologist_id
    on xinyuzhilian.user_favorite_psychologist (psychologist_id);

create index idx_user_id
    on xinyuzhilian.user_favorite_psychologist (user_id);

create table if not exists xinyuzhilian.user_follow
(
    id           bigint auto_increment
        primary key,
    follower_id  bigint                             not null comment '关注者ID',
    following_id bigint                             not null comment '被关注者ID',
    create_time  datetime default CURRENT_TIMESTAMP null,
    constraint uk_follow
        unique (follower_id, following_id)
)
    comment '用户关注关系表';

create index idx_follower
    on xinyuzhilian.user_follow (follower_id);

create index idx_following
    on xinyuzhilian.user_follow (following_id);

create table if not exists xinyuzhilian.user_membership
(
    id                 bigint auto_increment
        primary key,
    user_id            bigint                             not null comment '用户ID',
    member_type        tinyint  default 0                 null comment '会员类型：0-非会员，1-VIP，2-SVIP',
    member_expire_time datetime                           null comment '会员过期时间',
    daily_limit_seconds int    default 0                 null comment '每日限制秒数',
    create_time        datetime default CURRENT_TIMESTAMP null,
    update_time        datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_user_id
        unique (user_id)
)
    comment '用户会员表';

create table if not exists xinyuzhilian.user_privacy_setting
(
    user_id              bigint                             not null comment '用户ID'
        primary key,
    allow_view_likes     int      default 1                 null comment '允许他人查看点赞(0-否,1-是)',
    allow_view_articles  int      default 1                 null comment '允许他人查看发布文章(0-否,1-是)',
    allow_view_collections int    default 1                 null comment '允许他人查看收藏(0-否,1-是)',
    allow_view_followings int     default 0                 null comment '允许他人查看关注(0-否,1-是)',
    allow_view_fans      int      default 0                 null comment '允许他人查看粉丝(0-否,1-是)',
    update_time          datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
)
    comment '用户隐私设置表';

create table if not exists xinyuzhilian.user_psychologist_history
(
    id                    bigint auto_increment comment '主键ID'
        primary key,
    user_id               bigint                             not null comment '用户ID',
    psychologist_id       bigint                             not null comment '心理咨询师ID',
    appointment_count     int      default 1                 null comment '预约次数',
    last_appointment_time datetime                           null comment '最后一次预约时间',
    last_appointment_id   bigint                             null comment '最后一次预约ID',
    create_time           datetime default CURRENT_TIMESTAMP null comment '首次咨询时间',
    update_time           datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_psychologist
        unique (user_id, psychologist_id)
)
    comment '用户-心理咨询师咨询记录表';

create index idx_last_time
    on xinyuzhilian.user_psychologist_history (last_appointment_time);

create index idx_user_id
    on xinyuzhilian.user_psychologist_history (user_id);

create table if not exists xinyuzhilian.user_stats
(
    user_id        bigint                             not null comment '用户ID'
        primary key,
    follow_count   int      default 0                 null comment '关注数',
    fan_count      int      default 0                 null comment '粉丝数',
    article_count  int      default 0                 null comment '发布文章数',
    like_count     int      default 0                 null comment '获赞数',
    update_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP
)
    comment '用户统计数据表';

create table if not exists xinyuzhilian.xiaoai_usage_stat
(
    id            bigint auto_increment comment '主键ID'
        primary key,
    user_id       bigint                             not null comment '用户ID',
    total_seconds int      default 0                 null comment '今日已使用秒数',
    last_date     date                               null comment '最后使用日期(用于每日重置)',
    create_time   datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_id
        unique (user_id)
)
    comment '小爱倾听-每日使用时长统计表';

create index idx_last_date
    on xinyuzhilian.xiaoai_usage_stat (last_date);
