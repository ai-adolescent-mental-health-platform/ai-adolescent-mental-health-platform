export type ApiEnvelope<T> = {
  code: number;
  message: string;
  data: T;
};

export type ApiResult<T> = ApiEnvelope<T>;

export type PageResult<T> = {
  total: number;
  records: T[];
  current: number;
  size: number;
  pages?: number;
};

export type UserRole = "teen" | "parent" | "psychologist" | "admin";

export type UserProfile = {
  id: number;
  nickname: string;
  username?: string;
  email?: string;
  phone?: string;
  sex?: number;
  signature?: string;
  headPath?: string;
  role?: UserRole | number;
};

export type Psychologist = {
  id: number;
  name: string;
  title: string;
  avatar?: string;
  fields: string[];
  rating: number;
  price: number;
  onlinePrice?: number;
  offlinePrice?: number;
  city: string;
  availableToday: boolean;
  serviceTypes: string[];
  intro: string;
  educationBackground?: string;
  trainingExperience?: string;
  yearsExperience?: number;
  isFavorite?: boolean;
};

export type AppointmentStatus = "待支付" | "待确认" | "已预约" | "进行中" | "已完成" | "已取消";

export type ConsultationType = "线上咨询" | "到院咨询";

export type Appointment = {
  id: number;
  psychologistName: string;
  patientName: string;
  date: string;
  time: string;
  status: AppointmentStatus;
  type: ConsultationType;
  fee: number;
};

export type AssessmentRiskLevel = "日常筛查" | "情绪压力" | "睡眠关注" | "亲子关系";

export type AssessmentTemplate = {
  id: number;
  title: string;
  description: string;
  questionCount: number;
  duration: string;
  riskLevel: AssessmentRiskLevel;
  questions?: AssessmentQuestion[];
};

export type AssessmentQuestion = {
  id: string;
  title: string;
  options?: Array<{
    label: string;
    value: number;
  }>;
};

export type AssessmentRecord = {
  id: number;
  title: string;
  score: number;
  result: string;
  createTime: string;
};

export type AiSession = {
  id: number;
  title: string;
  createTime: string;
};

export type AiMessageRole = "user" | "assistant" | "reasoning";

export type AiMessage = {
  id: number;
  role: AiMessageRole;
  content: string;
  createTime: string;
  /** 思考过程（仅 assistant 消息，当 enableThinking=true 时） */
  reasoning?: string;
};

/** SSE streaming chunk types */
export type AiStreamChunk =
  | { type: "content"; content: string }
  | { type: "reasoning"; content: string };

export type LibraryItemType = "文章" | "课程" | "书籍" | "社区";

export type LibraryItem = {
  id: number;
  title: string;
  type: LibraryItemType;
  tag: string;
  summary: string;
  author: string;
  authorId?: number;
  readTime: string;
  views: number;
  coverUrl?: string;
  /** 课程的外部链接（视频/音频 URL），文章无此字段 */
  linkUrl?: string;
};

export type CarePlanStatus = "进行中" | "待开始" | "已完成";

export type CarePlanItem = {
  id: string;
  title: string;
  status: CarePlanStatus;
  accent: "green" | "purple" | "yellow" | "coral" | "teal";
};

export type ArticleDetail = {
  id: number;
  title: string;
  content: string;
  tagName?: string;
  type?: string;
  createTime: string;
  viewCount: number;
  likeCount: number;
  dislikeCount: number;
  collectionCount: number;
  commentCount: number;
  authorName: string;
  authorAvatar?: string;
  authorRole?: number;
  liked: boolean;
  disliked: boolean;
  collected: boolean;
  recommendations: { id: number; title: string; type: string }[];
};

export type InteractionItem = {
  articleId: number;
  articleTitle: string;
  authorNickname: string;
  authorId: number;
  coverUrl: string;
  createTime: string;
  source: string;
  authorRole: number;
};

export type FollowUser = {
  userId: number;
  nickname: string;
  headPath: string;
  signature: string;
  isFollowing: boolean;
  isFollowed: boolean;
};

export type DashboardSnapshot = {
  quote: string;
  moodScore: number;
  activePlan: string;
  nextAppointment: Appointment;
  aiSummary: string;
  assessmentProgress: number;
  recommendations: LibraryItem[];
};

export type CheckinMoodTag = {
  id: number;
  name: string;
  code: string;
  icon: string;
  tone: string;
  polarity: number;
  sortOrder?: number;
};

export type CheckinRecord = {
  id: number;
  userId?: number;
  checkinDate: string;
  moodPolarity?: number;
  diaryContent?: string;
  contentLength?: number;
  createTime?: string;
  updateTime?: string;
};

export type CheckinAnalysis = {
  id?: number;
  checkinId?: number;
  status?: number;
  riskLevel?: number;
  userFeedback?: string;
  suggestion?: string;
  riskReason?: string;
  createTime?: string;
};

export type CheckinToday = {
  checkin: CheckinRecord | null;
  tags: CheckinMoodTag[];
  analysis: CheckinAnalysis | null;
};

export type CheckinSubmitBody = {
  tagIds: number[];
  diaryContent?: string;
};

export type CheckinStats = {
  continuousDays: number;
  monthCount: number;
  trend: Array<{ date: string; polarity: number | null }>;
};

export type CheckinHistoryItem = {
  id: number;
  checkinDate: string;
  moodPolarity?: number;
  diaryContent?: string;
  tags: CheckinMoodTag[];
  analysisStatus?: number;
  riskLevel?: number;
  createTime?: string;
};
