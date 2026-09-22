# 青少年心理健康AI智能服务系统 - 技术详细设计文档

## 1. 系统架构设计 (System Architecture)

### 1.1 技术选型
*   **后端核心框架**: Spring Boot 2.7+ / 3.0+
*   **持久层框架**: MyBatis-Plus
*   **数据库**: MySQL 8.0 (存储业务数据)
*   **缓存**: Redis (缓存热点数据、Session共享、验证码、消息队列)
*   **对象存储**: OSS (阿里云/腾讯云/MinIO) - 存储头像、语音、视频、文章图片
*   **安全框架**: Spring Security + JWT
*   **实时通讯**: WebSocket (用于IM聊天、实时警报)
*   **AI服务集成**: Python Flask/FastAPI (独立部署，提供NLP、情感分析、热梗识别能力) 或 调用第三方大模型API
*   **前端技术**: Vue.3 / UniApp (多端适配)

### 1.2 系统拓扑
![1769701951208](./asset/1769701951208.png)

---

## 2. 数据库设计 (Database Design)

### 2.1 核心ER模型设计

#### 2.1.1 用户与角色基础 (现有 User 表扩展)
*   **表名**: `user`
*   **核心字段**: `id`, `username`, `password`, `role` (0-游客, 1-用户, 2-医生, 3-医院管理员, 4-超级管理员), `nickname`, `phone`, `head_path`, `status`.

#### 2.1.2 医院信息 (Hospital)
*   **表名**: `hospital`
*   **字段**:
    *   `id` (PK)
    *   `name` (医院名称)
    *   `code` (医院编码，用于关联)
    *   `admin_user_id` (关联的管理员账号ID)
    *   `address`, `contact_phone`, `introduction`
    *   `status` (0-停用, 1-正常)

#### 2.1.3 医生档案 (DoctorProfile)
*   **表名**: `doctor_profile`
*   **字段**:
    *   `user_id` (FK -> user.id)
    *   `hospital_id` (FK -> hospital.id)
    *   `real_name` (真实姓名)
    *   `title` (职称: 主治医师/副主任等)
    *   `specialty` (擅长领域: 抑郁/焦虑/青少年等)
    *   `introduction` (简介)
    *   `consultation_price` (咨询价格)
    *   `schedule_config` (排班配置JSON)

#### 2.1.4 患者档案扩展 (PatientProfile)
*   **表名**: `patient_profile`
*   **字段**:
    *   `user_id` (FK -> user.id)
    *   `emergency_contact` (紧急联系人)
    *   `emergency_phone` (紧急电话)
    *   `medical_history` (既往病史)
    *   `tags` (标签: 抑郁倾向, 活跃, 敏感 - 逗号分隔或JSON)

#### 2.1.5 医患关系 (DoctorPatientRelation)
*   **表名**: `doctor_patient_relation`
*   **字段**:
    *   `id` (PK)
    *   `doctor_id`
    *   `patient_id`
    *   `status` (NEW-新患者, ONGOING-进行中, STABLE-稳定, ARCHIVED-归档)
    *   `create_time`

#### 2.1.6 测评体系 (Assessment)
*   **表名**: `assessment_template` (量表模板)
    *   `id`, `title`, `type` (TRADITIONAL-传统, QUICK-快速, DYNAMIC-动态), `questions_json` (题目结构), `scoring_rules_json` (计分规则)
*   **表名**: `assessment_record` (测评记录)
    *   `id`, `user_id`, `template_id`, `answers_json` (用户回答), `result_score` (得分), `result_analysis` (分析结论), `create_time`

#### 2.1.7 聊天与危机 (Chat & Crisis)
*   **表名**: `chat_session`
    *   `id`, `user_id`, `target_id` (AI或医生ID), `type` (AI_TREE_HOLE-树洞, CONSULTATION-咨询), `start_time`, `end_time`
*   **表名**: `chat_message`
    *   `id`, `session_id`, `sender_id`, `content`, `msg_type` (TEXT, IMAGE, VOICE), `emotion_tag` (情感标签: 悲伤, 愤怒...), `risk_level` (0-低, 1-中, 2-高)
*   **表名**: `crisis_alert` (危机警报)
    *   `id`, `user_id`, `trigger_msg_id`, `risk_level`, `status` (PENDING-待处理, PROCESSING-处理中, RESOLVED-已解决), `handler_id` (处理人ID), `create_time`

> **更新（2026-09）**：本节 `crisis_alert` 命名已被签到预警模块的 `risk_alert` 表取代，见 `apps/backend/sql/schema_checkin.sql` 及「每日签到与情绪预警模块设计文档」第 5 节。`risk_alert` 采用 `source_type` / `source_id` 多源定位（`uk_source` 唯一键），支持签到、量表、小爱倾听等来源，`status` 枚举统一为 `0-待处置 / 1-处理中 / 2-已处置 / 3-已忽略`；`risk_level` 统一刻度为 `0-平稳 / 1-需要陪伴 / 2-危机`（与设计文档 5.1 对齐，不再使用 0-低 / 1-中 / 2-高 的旧表述）。

#### 2.1.8 内容与课程 (Content)
*   **表名**: `article` (文章)
    *   `id`, `title`, `content` (Rich Text), `type` (SCIENCE-科普, CASE-案例), `status` (PUBLISHED/DRAFT)
*   **表名**: `course` (课程)
    *   `id`, `title`, `description`, `media_url`, `cover_url`, `type` (VIDEO/AUDIO)
*   **表名**: `user_course_progress`
    *   `user_id`, `course_id`, `progress` (百分比), `last_watch_time`

---

## 3. 核心功能实现方案 (Implementation Plan)

### 3.1 鉴权与多角色系统 (Auth Module)
*   **方案**: Spring Security + JWT。
*   **实现步骤**:
    1.  完善 `UserDetailsServiceImpl`，根据 `user.role` 加载不同的 `GrantedAuthority` (ROLE_USER, ROLE_DOCTOR, etc.)。
    2.  定义注解 `@PreAuthorize("hasRole('DOCTOR')")` 控制Controller访问权限。
    3.  登录接口返回 JWT Token 及用户基础信息。

### 3.2 情感树洞与AI对话 (AI Chat Module)
*   **技术**: WebSocket + 异步任务 + 外部LLM接口。
*   **流程**:
    1.  前端通过 WebSocket 连接 `/ws/chat/{userId}`。
    2.  用户发送消息 -> 后端接收 -> 存入 `chat_message`。
    3.  **异步处理**:
        *   调用 **情感分析服务** (识别情绪标签)。
        *   调用 **热梗识别服务** (解析潜在含义)。
        *   调用 **LLM对话服务** (生成回复)。
    4.  AI回复通过 WebSocket 推送给用户。
    5.  **危机判定**: 若情感分析结果为“极度悲伤/绝望”或包含敏感词，触发 `CrisisEvent`。

### 3.3 危机干预联动 (Crisis Intervention)
*   **技术**: Spring ApplicationEvent / Redis Pub/Sub + WebSocket 推送。
*   **流程**:
    1.  `CrisisEvent` 被触发。
    2.  `CrisisEventListener` 捕获事件：
        *   创建 `crisis_alert` 记录。
        *   查询该用户所属医院管理员及超级管理员在线状态。
        *   通过 WebSocket 向管理员端推送 `{type: "CRISIS_ALERT", userId: xxx, level: "HIGH"}` 消息。
    3.  管理员界面出现闪烁弹窗。

### 3.4 医生工作台与360档案 (Doctor Dashboard)
*   **技术**: 复杂SQL聚合查询 / MyBatis-Plus Wrapper。
*   **接口设计**:
    *   `GET /doctor/dashboard/stats`: 获取待办数、预约数。
    *   `GET /doctor/patients`: 分页查询患者列表。
    *   `GET /doctor/patient/{id}/full-profile`: 聚合查询。
        *   并发调用 Service 获取：基本信息、最近5次测评、最近10条咨询摘要、课程学习进度。
*   **数据脱敏**:
    *   使用 AOP (`@Desensitize`) 在 Controller 返回层对手机号、身份证等敏感字段进行掩码处理 (`138****1234`)。

### 3.5 动态测评 (Dynamic Assessment)
*   **方案**: 基于 Prompt Engineering 的动态问卷生成。
*   **流程**:
    1.  用户点击“动态测评”。
    2.  系统提取用户最近的聊天记录 (Last 50 messages)。
    3.  构造 Prompt: "基于该用户的聊天内容...生成5道心理评估选择题..." 发送给 LLM。
    4.  LLM 返回 JSON 格式题目。
    5.  前端渲染题目，用户作答。
    6.  将答案回传给 LLM 进行分析，生成报告。

### 3.6 医院与管理员功能 (Admin Module)
*   **数据隔离**:
    *   MyBatis-Plus 拦截器或在 Service 层强制追加 `hospital_id` 过滤条件，确保管理员只能看到本院数据。

---

## 4. 接口定义 (API Definitions)

### 4.1 用户端 API
*   `POST /api/auth/login` - 登录
*   `GET /api/user/profile` - 个人信息
*   `GET /api/content/articles` - 文章列表
*   `GET /api/content/courses` - 课程列表
*   `POST /api/assessment/submit` - 提交测评
*   `GET /api/chat/history` - 聊天记录

### 4.2 医生端 API
*   `GET /api/doctor/tasks` - 今日待办
*   `GET /api/doctor/patients` - 患者列表
*   `GET /api/doctor/patient/{id}/archive` - 360档案
*   `POST /api/doctor/prescription` - 下达调适计划

### 4.3 管理端 API
*   `POST /api/admin/doctor` - 添加医生
*   `GET /api/admin/logs` - 操作日志
*   `GET /api/admin/stats` - 医院统计数据

---

## 5. 异常处理与安全 (Exception & Security)

### 5.1 全局异常处理
*   利用 `GlobalExceptionHandler` (已存在，需完善)。
*   捕获 `AccessDeniedException` -> 返回 403。
*   捕获 `BusinessException` -> 返回 业务错误码。

### 5.2 安全验收标准
1.  **敏感数据不落地**: 日志中不打印明文密码、聊天内容需加密存储 (可选)。
2.  **越权访问测试**: 验证 医生ID 能否访问 非自己医院的患者数据 (水平越权)。
3.  **XSS/SQL注入**: 利用框架机制防御。
4.  **脱敏**: 非主治医生/管理员查看患者信息时，手机号必须脱敏。

---

## 6. 任务清单 (Task List)

### Phase 1: 基础架构与用户体系 (Week 1)
*   [ ] 设计并创建数据库表结构 (SQL Script)。
*   [ ] 完善 `User` 实体与 Mapper，实现多角色登录。
*   [ ] 集成 Swagger/Knif4j 接口文档。

### Phase 2: 核心业务功能 (Week 2-3)
*   [ ] 实现文章与课程的 CRUD (管理员端) 及展示 (用户端)。
*   [ ] 实现 心理测评 模块 (传统量表录入、计算逻辑)。
*   [ ] 开发 医生工作台 基础接口 (患者列表、档案查询)。

### Phase 3: AI与实时通讯 (Week 4)
*   [ ] 搭建 WebSocket 服务端。
*   [ ] 对接 LLM API 实现智能聊天。
*   [ ] 实现 情感分析 与 危机识别 逻辑。
*   [ ] 实现 危机预警 推送功能。

### Phase 4: 管理与优化 (Week 5)
*   [ ] 医院/超级管理员后台功能。
*   [ ] 数据脱敏 AOP 实现。
*   [ ] 全链路测试与部署。

---

## 7. 交付物 (Deliverables)
1.  **源代码**: 包含后端 SpringBoot 工程与前端 Vue 工程。
2.  **数据库脚本**: `schema.sql` (结构) 和 `data.sql` (初始数据)。
3.  **接口文档**: 在线 Swagger 地址或导出 PDF。
4.  **部署手册**: Docker Compose 文件或部署 Shell 脚本。
