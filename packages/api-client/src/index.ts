import axios, { AxiosError, type AxiosAdapter, type AxiosInstance, type AxiosRequestConfig } from "axios";
import type {
  AiMessage,
  AiSession,
  ApiEnvelope,
  Appointment,
  AppointmentStatus,
  ArticleDetail,
  AssessmentQuestion,
  AssessmentRecord,
  AssessmentRiskLevel,
  AssessmentTemplate,
  ConsultationType,
  FollowUser,
  InteractionItem,
  LibraryItem,
  LibraryItemType,
  PageResult,
  Psychologist,
  UserProfile,
} from "@ai-adolescent-mental-health/domain";

export type ApiClientErrorKind = "http" | "business" | "network" | "unauthorized" | "forbidden" | "parse";

export class ApiClientError extends Error {
  kind: ApiClientErrorKind;
  status?: number;
  code?: number;
  cause?: unknown;

  constructor(message: string, options: { kind: ApiClientErrorKind; status?: number; code?: number; cause?: unknown }) {
    super(message);
    this.name = "ApiClientError";
    this.kind = options.kind;
    this.status = options.status;
    this.code = options.code;
    this.cause = options.cause;
  }
}

export type HttpClientOptions = {
  baseURL: string;
  getToken?: () => string | undefined | null;
  onUnauthorized?: () => void;
  timeout?: number;
  /**
   * 可选的自定义传输层。不传时走 axios 默认实现（浏览器 / Node）。
   * uni-app 运行时通过它注入 uni.request 适配器，见 uni-adapter.ts。
   */
  adapter?: AxiosAdapter;
};

export type RequestConfig = Omit<AxiosRequestConfig, "url" | "baseURL" | "method" | "params" | "data"> & {
  query?: Record<string, string | number | boolean | null | undefined>;
};

type UnknownRecord = Record<string, unknown>;

function toError(error: unknown, onUnauthorized?: () => void): ApiClientError {
  if (error instanceof ApiClientError) return error;
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<ApiEnvelope<unknown>>;
    const status = axiosError.response?.status;
    if (status === 401) onUnauthorized?.();
    if (status === 401) return new ApiClientError("登录已过期，请重新登录", { kind: "unauthorized", status, cause: error });
    if (status === 403) return new ApiClientError("权限不足，无法访问", { kind: "forbidden", status, cause: error });
    if (status) return new ApiClientError(axiosError.response?.data?.message || `HTTP ${status}`, { kind: "http", status, cause: error });
    return new ApiClientError("网络连接异常，请稍后重试", { kind: "network", cause: error });
  }
  return new ApiClientError("请求处理失败", { kind: "parse", cause: error });
}

function unwrap<T>(payload: ApiEnvelope<T>, onUnauthorized?: () => void) {
  if (!payload || typeof payload.code !== "number") {
    throw new ApiClientError("响应格式不正确", { kind: "parse" });
  }
  if (payload.code === 401) onUnauthorized?.();
  if (payload.code !== 200) {
    throw new ApiClientError(payload.message || "请求失败", {
      kind: payload.code === 401 ? "unauthorized" : payload.code === 403 ? "forbidden" : "business",
      code: payload.code,
    });
  }
  return payload.data;
}

function asRecord(value: unknown): UnknownRecord {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as UnknownRecord) : {};
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function asString(value: unknown, fallback = "") {
  if (value === null || value === undefined) return fallback;
  return String(value);
}

function asNumber(value: unknown, fallback = 0) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const next = Number(value);
    if (Number.isFinite(next)) return next;
  }
  return fallback;
}

function asBoolean(value: unknown) {
  return value === true || value === 1 || value === "1" || value === "true";
}

function parseJsonArray(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (typeof value !== "string" || !value.trim()) return [];
  try {
    return asArray(JSON.parse(value));
  } catch {
    return [];
  }
}

function formatDateTime(value: unknown) {
  const raw = asString(value);
  if (!raw) return "";
  return raw.replace("T", " ").slice(0, 16);
}

function splitAppointmentTime(value: unknown) {
  const formatted = formatDateTime(value);
  if (!formatted) return { date: "", time: "" };
  const [date = "", time = ""] = formatted.split(" ");
  return { date, time };
}

function mapPage<T, U>(page: PageResult<T>, mapper: (item: T) => U): PageResult<U> {
  return {
    total: page?.total ?? 0,
    records: asArray(page?.records).map((item) => mapper(item as T)),
    current: page?.current ?? 1,
    size: page?.size ?? 0,
    pages: page?.pages,
  };
}

function mapUserProfile(value: unknown, fallbackNickname = "用户"): UserProfile {
  const data = asRecord(value);
  return {
    id: asNumber(data.id),
    nickname: asString(data.nickname || data.username, fallbackNickname),
    username: asString(data.username, undefined as unknown as string) || undefined,
    email: asString(data.email, undefined as unknown as string) || undefined,
    phone: asString(data.phone, undefined as unknown as string) || undefined,
    sex: data.sex === undefined ? undefined : asNumber(data.sex),
    signature: asString(data.signature, undefined as unknown as string) || undefined,
    headPath: asString(data.headPath, undefined as unknown as string) || undefined,
    role: data.role as UserProfile["role"],
  };
}

function fieldName(value: unknown): string {
  const data = asRecord(value);
  return asString(data.name || data.fieldName || data.title || value);
}

function serviceName(value: unknown): string {
  const data = asRecord(value);
  const raw = asString(data.serviceType || data.type || value);
  const lower = raw.toLowerCase();
  if (lower === "online" || lower === "video") return "视频咨询";
  if (lower === "offline") return "线下面询";
  if (lower === "text") return "图文咨询";
  if (lower === "voice") return "语音咨询";
  return asString(data.name || data.label, raw || "咨询服务");
}

function mapPsychologist(value: unknown): Psychologist {
  const data = asRecord(value);
  const fields = asArray(data.fields).map(fieldName).filter(Boolean);
  const services = asArray(data.services).map(serviceName).filter(Boolean);

  // Extract online price from services array (VIDEO/VOICE combined)
  const svcArr = asArray(data.services);
  let onlinePriceFromSvc = 0;
  for (const svc of svcArr) {
    const s = asRecord(svc);
    const type = asString(s.serviceType).toUpperCase();
    if (type === "VIDEO" || type === "VOICE") {
      const p = asNumber(s.price);
      if (p > 0) { onlinePriceFromSvc = p; break; }
    }
  }
  // If no VIDEO/VOICE price found, try TEXT
  if (onlinePriceFromSvc === 0) {
    for (const svc of svcArr) {
      const s = asRecord(svc);
      const type = asString(s.serviceType).toUpperCase();
      if (type === "TEXT") {
        onlinePriceFromSvc = asNumber(s.price);
        break;
      }
    }
  }

  const price = onlinePriceFromSvc > 0
    ? onlinePriceFromSvc
    : asNumber(data.consultationPrice ?? data.basePrice ?? data.price);
  return {
    id: asNumber(data.id),
    name: asString(data.realName || data.name, "心理咨询师"),
    title: asString(data.title || data.qualificationName, "心理咨询师"),
    avatar: asString(data.headPath || data.avatar, undefined as unknown as string) || undefined,
    fields,
    rating: asNumber(data.ratingScore ?? data.rating, 0),
    price,
    onlinePrice: price,
    offlinePrice: asNumber(data.offlinePrice, undefined as unknown as number) || undefined,
    city: asString(data.offlineRegion || data.city, "线上"),
    availableToday: asNumber(data.onlineStatus) === 1,
    serviceTypes: services.length ? services : ["视频咨询"],
    intro: asString(data.introduction || data.intro, "暂无简介"),
    educationBackground: asString(data.educationBackground, undefined as unknown as string) || undefined,
    trainingExperience: asString(data.trainingExperience, undefined as unknown as string) || undefined,
    yearsExperience: asNumber(data.yearsExperience, undefined as unknown as number) || undefined,
    isFavorite: asBoolean(data.isFavorited ?? data.isFavorite),
  };
}

function mapAppointmentStatus(value: unknown): AppointmentStatus {
  const status = asNumber(value, Number.NaN);
  if (status === 0) return "待支付";
  if (status === 1) return "待确认";
  if (status === 2) return "已预约";
  if (status === 3) return "进行中";
  if (status === 4) return "已完成";
  if (status === 5 || status === 6) return "已取消";
  const text = asString(value);
  if (["待支付", "待确认", "已预约", "进行中", "已完成", "已取消"].includes(text)) return text as AppointmentStatus;
  return "待确认";
}

function mapConsultationType(value: unknown): ConsultationType {
  const type = asString(value).toLowerCase();
  return type === "offline" || type.includes("线下") || type.includes("到院") ? "到院咨询" : "线上咨询";
}

function mapAppointment(value: unknown): Appointment {
  const data = asRecord(value);
  const { date, time } = splitAppointmentTime(data.appointmentTime ?? data.date);
  return {
    id: asNumber(data.id),
    psychologistName: asString(data.psychologistName, "心理咨询师"),
    patientName: asString(data.patientName || data.patientContactName, "就诊人"),
    date,
    time,
    status: mapAppointmentStatus(data.status),
    type: mapConsultationType(data.serviceType ?? data.type),
    fee: asNumber(data.price ?? data.fee),
  };
}

function mapAssessmentRisk(value: unknown): AssessmentRiskLevel {
  const type = asString(value).toUpperCase();
  if (type.includes("SLEEP")) return "睡眠关注";
  if (type.includes("PARENT")) return "亲子关系";
  if (type.includes("STRESS") || type.includes("MOOD")) return "情绪压力";
  return "日常筛查";
}

function mapQuestion(value: unknown, index: number): AssessmentQuestion {
  const data = asRecord(value);
  const options = asArray(data.options).map((item, optionIndex) => {
    const option = asRecord(item);
    return {
      label: asString(option.label || option.text || item, String(optionIndex + 1)),
      value: asNumber(option.value ?? option.score, optionIndex + 1),
    };
  });
  return {
    id: asString(data.id || data.key, String(index)),
    title: asString(data.title || data.question || data.text, `第 ${index + 1} 题`),
    options: options.length ? options : undefined,
  };
}

function mapAssessmentTemplate(value: unknown): AssessmentTemplate {
  const data = asRecord(value);
  const questions = parseJsonArray(data.questionsJson).map(mapQuestion);
  return {
    id: asNumber(data.id),
    title: asString(data.title, "未命名量表"),
    description: asString(data.description, "暂无量表说明"),
    questionCount: questions.length,
    duration: questions.length ? `约 ${Math.max(1, Math.ceil(questions.length / 4))} 分钟` : "按量表题量",
    riskLevel: mapAssessmentRisk(data.type),
    questions,
  };
}

function mapAssessmentRecord(value: unknown): AssessmentRecord {
  const data = asRecord(value);
  const record = asRecord(data.record || data);
  return {
    id: asNumber(record.id),
    title: asString(data.templateTitle || record.templateTitle || record.title, "心理测评"),
    score: asNumber(record.resultScore ?? record.score),
    result: asString(record.resultAnalysis ?? record.result, "暂无分析结果"),
    createTime: formatDateTime(record.createTime),
  };
}

function mapAiSession(value: unknown): AiSession {
  const data = asRecord(value);
  return {
    id: asNumber(data.id),
    title: asString(data.title, "新的会话"),
    createTime: formatDateTime(data.createTime),
  };
}

function mapAiMessage(value: unknown): AiMessage | null {
  const data = asRecord(value);
  const role = asString(data.role);
  if (role !== "user" && role !== "assistant") return null;
  return {
    id: asNumber(data.id),
    role,
    content: asString(data.content),
    createTime: formatDateTime(data.createTime),
  };
}

function mapLibraryItem(value: unknown, type: LibraryItemType): LibraryItem {
  const data = asRecord(value);
  return {
    id: asNumber(data.id),
    title: asString(data.title, "未命名内容"),
    type,
    tag: asString(data.tagName || data.type || data.categoryName, type),
    summary: asString(data.description || data.summary || data.content, "暂无简介"),
    author: asString(data.authorName || data.sourceName || data.author || data.nickname, "心愈智联"),
    authorId: asNumber(data.userId) || undefined,
    readTime: type === "课程" ? "课程" : type === "书籍" ? "章节导读" : "阅读",
    views: asNumber(data.view_count ?? data.viewCount ?? data.views),
    coverUrl: asString(data.coverUrl ?? data.cover_url ?? data.cover ?? data.image, ""),
    linkUrl: asString(data.mediaUrl ?? data.media_url, "") || undefined,
  };
}

function mapArticleDetail(value: unknown): ArticleDetail {
  const data = asRecord(value);
  const article = asRecord(data.article ?? data);
  return {
    id: asNumber(article.id),
    title: asString(article.title, "未命名文章"),
    content: asString(article.content ?? data.content),
    tagName: asString(article.tagName ?? data.tagName, ""),
    type: asString(article.type ?? data.type, ""),
    createTime: formatDateTime(article.createTime ?? data.createTime),
    viewCount: asNumber(article.view_count ?? article.viewCount ?? data.viewCount ?? data.view_count),
    likeCount: asNumber(article.like_count ?? article.likeCount ?? data.likeCount ?? data.like_count),
    dislikeCount: asNumber(article.dislike_count ?? article.dislikeCount ?? data.dislikeCount ?? data.dislike_count),
    collectionCount: asNumber(article.collection_count ?? article.collectionCount ?? data.collectionCount ?? data.collection_count),
    commentCount: asNumber(article.comment_count ?? article.commentCount ?? data.commentCount ?? data.comment_count),
    authorName: asString(data.authorName ?? data.author ?? data.userNickname ?? article.authorName ?? article.author, "心愈智联"),
    authorAvatar: asString(data.authorAvatar ?? data.userAvatar ?? article.authorAvatar ?? data.avatar ?? "", ""),
    authorRole: asNumber(data.authorRole ?? article.authorRole),
    liked: asBoolean(data.liked ?? article.liked),
    disliked: asBoolean(data.disliked ?? article.disliked),
    collected: asBoolean(data.collected ?? article.collected),
    recommendations: (asArray(data.recommendedArticles ?? data.recommendations) as unknown[]).map((r: unknown) => {
      const rec = asRecord(r);
      return { id: asNumber(rec.id), title: asString(rec.title, ""), type: "文章" };
    }),
  };
}

function mapInteractionItem(value: unknown): InteractionItem {
  const d = asRecord(value);
  return {
    articleId: asNumber(d.articleId ?? d.id),
    articleTitle: asString(d.articleTitle ?? d.title, ""),
    authorNickname: asString(d.authorNickname ?? d.nickname, ""),
    authorId: asNumber(d.authorId ?? d.userId ?? d.author_id),
    coverUrl: asString(d.coverUrl ?? d.cover_url ?? d.cover, ""),
    createTime: formatDateTime(d.createTime ?? d.create_time),
    source: asString(d.source, "user"),
    authorRole: asNumber(d.authorRole ?? d.author_role),
  };
}

function mapFollowUser(value: unknown): FollowUser {
  const d = asRecord(value);
  return {
    userId: asNumber(d.userId ?? d.id),
    nickname: asString(d.nickname, ""),
    headPath: asString(d.headPath ?? d.avatar ?? d.head_path, ""),
    signature: asString(d.signature, ""),
    isFollowing: asBoolean(d.isFollowing ?? d.is_following),
    isFollowed: asBoolean(d.isFollowed ?? d.is_followed),
  };
}

export function createHttpClient(options: HttpClientOptions) {
  const client: AxiosInstance = axios.create({
    baseURL: options.baseURL.replace(/\/$/, ""),
    timeout: options.timeout ?? 10000,
    headers: {
      "Content-Type": "application/json;charset=UTF-8",
    },
    adapter: options.adapter,
  });

  client.interceptors.request.use((config) => {
    const token = options.getToken?.();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
      config.headers.token = token;
    }
    return config;
  });

  async function request<T>(method: AxiosRequestConfig["method"], url: string, data?: unknown, config: RequestConfig = {}) {
    try {
      const response = await client.request<ApiEnvelope<T>>({
        ...config,
        method,
        url,
        data,
        params: config.query,
      });
      return unwrap(response.data, options.onUnauthorized);
    } catch (error) {
      throw toError(error, options.onUnauthorized);
    }
  }

  return {
    raw: client,
    get: <T>(url: string, config?: RequestConfig) => request<T>("GET", url, undefined, config),
    post: <T>(url: string, data?: unknown, config?: RequestConfig) => request<T>("POST", url, data, config),
    put: <T>(url: string, data?: unknown, config?: RequestConfig) => request<T>("PUT", url, data, config),
    delete: <T>(url: string, config?: RequestConfig) => request<T>("DELETE", url, undefined, config),
  };
}

export type HttpClient = ReturnType<typeof createHttpClient>;

export function createApiClient(http: HttpClient) {
  return {
    user: {
      async loginByUsernamePassword(username: string, password: string, remember = false) {
        const data = await http.post<Record<string, unknown>>(
          "/user/login",
          {
            username,
            password,
          },
          { query: { remember } },
        );
        const token = asString(data.token);
        const user = mapUserProfile(data.userInfo ?? data.user, username);
        return { token, user };
      },
      async loginByEmailCode(email: string, code: string) {
        const data = await http.post<Record<string, unknown>>("/user/login/email", {
          email,
          code,
        });
        const token = asString(data.token);
        const user = mapUserProfile(data.userInfo ?? data.user, email);
        return { token, user };
      },
      async loginByEmailPassword(email: string, password: string, remember = false) {
        const data = await http.post<Record<string, unknown>>("/user/login/email/password", {
          email,
          password,
          remember: String(remember),
        });
        const token = asString(data.token);
        const user = mapUserProfile(data.userInfo ?? data.user, email);
        return { token, user };
      },
      sendEmailCode: (email: string, scene = "login") => http.post<string>("/user/email/send", { email, scene }),
      registerWithEmail: (payload: Record<string, string>) => http.post<string>("/user/register/email", payload),
      getUserInfo: async () => mapUserProfile(await http.get<unknown>("/user/info")),
      updateUserInfo: async (payload: Partial<UserProfile>) => mapUserProfile(await http.post<unknown>("/user/update", payload)),
      getPrivacy: () => http.get<Record<string, unknown>>("/user/privacy"),
      updatePrivacy: (payload: Record<string, number>) => http.put<string>("/user/privacy", payload),
      getUserHome: async (userId: number) => {
        const data = asRecord(await http.get<unknown>(`/user/home/${userId}`));
        return {
          userId: asNumber(data.userId ?? userId),
          nickname: asString(data.nickname, "用户"),
          headPath: asString(data.headPath ?? data.avatar, ""),
          signature: asString(data.signature, ""),
          isFollowing: asBoolean(data.isFollowing),
          isFollowed: asBoolean(data.isFollowed),
          stats: {
            followCount: asNumber((data.stats as Record<string, unknown>)?.followCount ?? data.followCount),
            fanCount: asNumber((data.stats as Record<string, unknown>)?.fanCount ?? data.fanCount),
            articleCount: asNumber((data.stats as Record<string, unknown>)?.articleCount ?? data.articleCount),
            likeCount: asNumber((data.stats as Record<string, unknown>)?.likeCount ?? data.likeCount),
          },
          privacy: {
            allowViewLikes: asBoolean((data.privacy as Record<string, unknown>)?.allowViewLikes ?? true),
            allowViewCollections: asBoolean((data.privacy as Record<string, unknown>)?.allowViewCollections ?? true),
            allowViewFollowings: asBoolean((data.privacy as Record<string, unknown>)?.allowViewFollowings ?? true),
            allowViewFans: asBoolean((data.privacy as Record<string, unknown>)?.allowViewFans ?? true),
          },
        };
      },
      getUserArticles: async (userId: number, params?: { page?: number; size?: number }) =>
        mapPage(
          await http.get<PageResult<unknown>>(`/user/home/${userId}/articles`, { query: { page: params?.page ?? 1, size: params?.size ?? 10 } }),
          (item) => mapLibraryItem(item, "社区"),
        ),
      myCollections: async (params?: { page?: number; size?: number }) =>
        mapPage(
          await http.get<PageResult<unknown>>("/user/content/collections", { query: { page: params?.page ?? 1, size: params?.size ?? 20 } }),
          mapInteractionItem,
        ),
      myLikes: async (params?: { page?: number; size?: number }) =>
        mapPage(
          await http.get<PageResult<unknown>>("/user/content/likes", { query: { page: params?.page ?? 1, size: params?.size ?? 20 } }),
          mapInteractionItem,
        ),
      sendForgotPasswordCode: (username: string, email: string) =>
        http.post<string>("/user/forgot/send", { username, email }),
      verifyForgotPasswordCode: (username: string, email: string, code: string) =>
        http.post<string>("/user/forgot/verify", { username, email, code }),
      resetPassword: (username: string, email: string, code: string, newPassword: string, confirmPassword: string) =>
        http.post<string>("/user/forgot/reset", { username, email, code, newPassword, confirmPassword }),
    },
    message: {
      list: async (params?: { page?: number; size?: number }) =>
        http.get<PageResult<unknown>>("/user/messages", { query: { page: params?.page ?? 1, size: params?.size ?? 10 } }),
      markRead: (id: number) => http.put<string>(`/user/messages/${id}/read`),
      markAllRead: () => http.put<string>("/user/messages/read-all"),
      unreadCount: () => http.get<number>("/user/messages/unread-count"),
    },
    follow: {
      myFollowings: async (params?: { page?: number; size?: number }) =>
        mapPage(
          await http.get<PageResult<unknown>>("/user/followings", { query: { page: params?.page ?? 1, size: params?.size ?? 20 } }),
          mapFollowUser,
        ),
      myFollowers: async (params?: { page?: number; size?: number }) =>
        mapPage(
          await http.get<PageResult<unknown>>("/user/followers", { query: { page: params?.page ?? 1, size: params?.size ?? 20 } }),
          mapFollowUser,
        ),
      follow: (userId: number) => http.post<string>(`/user/follow/${userId}`),
      unfollow: (userId: number) => http.delete<string>(`/user/follow/${userId}`),
      userFollowings: async (userId: number, params?: { page?: number; size?: number }) =>
        mapPage(
          await http.get<PageResult<unknown>>(`/user/${userId}/followings`, { query: { page: params?.page ?? 1, size: params?.size ?? 20 } }),
          mapFollowUser,
        ),
      userFollowers: async (userId: number, params?: { page?: number; size?: number }) =>
        mapPage(
          await http.get<PageResult<unknown>>(`/user/${userId}/followers`, { query: { page: params?.page ?? 1, size: params?.size ?? 20 } }),
          mapFollowUser,
        ),
    },
    psychologistApply: {
      check: () => http.get<Record<string, unknown>>("/psychologist-apply/check"),
      status: () => http.get<Record<string, unknown>>("/psychologist-apply/status"),
      detail: () => http.get<Record<string, unknown>>("/psychologist-apply/detail"),
      submitBasic: (payload: Record<string, unknown>) => http.post<string>("/psychologist-apply/basic", payload),
      submitReport: (payload: Record<string, unknown>) => http.post<string>("/psychologist-apply/report", payload),
    },
    psychologist: {
      list: async (query?: Record<string, string | number | boolean | undefined>) =>
        mapPage(await http.get<PageResult<unknown>>("/psychologist/list", { query }), mapPsychologist),
      detail: async (id: number) => {
        const [detailData, listPage] = await Promise.all([
          http.get<unknown>(`/psychologist/${id}`),
          http.get<PageResult<unknown>>("/psychologist/list", { query: { page: 1, size: 100 } }).catch(() => null),
        ]);
        const psychologist = mapPsychologist(detailData);
        if (listPage && (!psychologist.onlinePrice || psychologist.onlinePrice <= 0)) {
          const listData = asRecord(listPage);
          const records = asArray(listData.records ?? listData.data ?? listData.list);
          for (const item of records) {
            const record = asRecord(item);
            if (asNumber(record.id) === id) {
              const svcArr = asArray(record.services);
              for (const svc of svcArr) {
                const s = asRecord(svc);
                const type = asString(s.serviceType).toUpperCase();
                if (type === "VIDEO" || type === "VOICE") {
                  const p = asNumber(s.price);
                  if (p > 0) { psychologist.onlinePrice = p; psychologist.price = p; break; }
                }
              }
              if (!psychologist.onlinePrice || psychologist.onlinePrice <= 0) {
                const cp = asNumber(record.consultationPrice);
                if (cp > 0) { psychologist.onlinePrice = cp; psychologist.price = cp; }
              }
              break;
            }
          }
        }
        return psychologist;
      },
      schedule: (id: number, startDate: string, endDate: string) =>
        http.get<Record<string, unknown>[]>(`/psychologist/${id}/schedule`, { query: { startDate, endDate } }),
      favorite: (id: number) => http.post<string>(`/psychologist/favorite/${id}`),
      myFavorites: async () => {
        const data = asArray(await http.get<unknown[]>("/psychologist/favorites"));
        return data.map((item) => {
          const d = asRecord(item);
          return {
            id: asNumber(d.psychologistId ?? d.id),
            name: asString(d.psychologistName ?? d.realName ?? d.name, "心理咨询师"),
            title: asString(d.title ?? d.qualificationName, "心理咨询师"),
            avatar: asString(d.psychologistHead ?? d.headPath ?? d.avatar, undefined as unknown as string) || undefined,
            fields: asArray(d.fields).map(fieldName).filter(Boolean),
            rating: asNumber(d.ratingScore ?? d.rating, 0),
            price: asNumber(d.basePrice ?? d.consultationPrice ?? d.price),
            onlinePrice: asNumber(d.onlinePrice, undefined as unknown as number) || undefined,
            offlinePrice: asNumber(d.offlinePrice, undefined as unknown as number) || undefined,
            city: asString(d.offlineRegion ?? d.city, "线上"),
            availableToday: asNumber(d.onlineStatus) === 1,
            serviceTypes: asArray(d.services ?? d.serviceTypes).map(serviceName).filter(Boolean),
            intro: asString(d.introduction ?? d.intro, "暂无简介"),
            isFavorite: true,
          } as Psychologist;
        });
      },
    },
    appointment: {
      create: (payload: Record<string, unknown>) => http.post<Record<string, unknown>>("/psychologist/appointment", payload),
      pay: (id: number) => http.post<string>(`/psychologist/appointment/${id}/pay`),
      cancel: (id: number, cancelReason: string) =>
        http.post<string>(`/psychologist/appointment/${id}/cancel`, { cancelReason }),
      rate: (id: number, rating: number, content: string) =>
        http.post<string>(`/psychologist/appointment/${id}/rate`, undefined, { query: { rating, content, isAnonymous: 0 } }),
      my: async (status?: number) =>
        mapPage(await http.get<PageResult<unknown>>("/psychologist/appointment/my", { query: { page: 1, size: 20, status } }), mapAppointment),
      detail: (id: number) => http.get<Record<string, unknown>>(`/psychologist/appointment/${id}/detail`),
    },
    chat: {
      history: (appointmentId: number) => http.get<unknown[]>(`/psychologist/message/history/${appointmentId}`),
      send: (appointmentId: number, receiverId: number, content: string, contentType = 0) =>
        http.post<Record<string, unknown>>("/psychologist/message/send", { appointmentId, receiverId, content, contentType }),
      sendImage: (appointmentId: number, receiverId: number, imageUrl: string) =>
        http.post<Record<string, unknown>>("/psychologist/message/send/image", { appointmentId, receiverId, imageUrl }),
    },
    assessment: {
      templates: async () => asArray(await http.get<unknown[]>("/assessment/templates")).map(mapAssessmentTemplate),
      submit: async (templateId: number, answers: Record<string, number>, patientContactId?: number) =>
        mapAssessmentRecord(await http.post<unknown>(`/assessment/submit/${templateId}`, { answers, patientContactId })),
      records: async () =>
        mapPage(await http.get<PageResult<unknown>>("/assessment/records", { query: { page: 1, size: 20 } }), mapAssessmentRecord),
      recordDetail: (id: number) => http.get<Record<string, unknown>>(`/assessment/record/${id}`),
    },
    feedback: {
      platform: () => http.get<PageResult<unknown>>("/feedback/platform/my", { query: { page: 1, size: 100 } }),
      submitPlatform: (data: { content: string }) => http.post<string>("/feedback/platform", data),
    },
    ai: {
      sessions: async () => asArray(await http.get<unknown[]>("/ai/sessions")).map(mapAiSession),
      createSession: async () => mapAiSession(await http.post<unknown>("/ai/session")),
      messages: async (sessionId: number) =>
        asArray(await http.get<unknown[]>(`/ai/session/${sessionId}/messages`)).map(mapAiMessage).filter((item): item is AiMessage => Boolean(item)),
    },
    xiaoai: {
      getRemainingTime: () => http.get<number>("/xiaoai/remaining"),
      getMemberType: () => http.get<number>("/xiaoai/member-type"),
      getDailyLimit: () => http.get<number>("/xiaoai/daily-limit"),
      getTodayMessages: () => http.get<unknown[]>("/xiaoai/today-messages"),
      reportUsageTime: (seconds: number) => http.post<string>("/xiaoai/report-usage", { seconds }),
    },
    content: {
      articles: async (params?: { page?: number; size?: number }) =>
        mapPage(
          await http.get<PageResult<unknown>>("/content/articles", { query: { page: params?.page ?? 1, size: params?.size ?? 12 } }),
          (item) => mapLibraryItem(item, "文章"),
        ),
      courses: async (params?: { page?: number; size?: number; type?: string }) =>
        mapPage(
          await http.get<PageResult<unknown>>("/content/courses", { query: { page: params?.page ?? 1, size: params?.size ?? 12, type: params?.type } }),
          (item) => mapLibraryItem(item, "课程"),
        ),
      books: async (params?: { page?: number; size?: number; keyword?: string }) =>
        mapPage(
          await http.get<PageResult<unknown>>("/book/list", { query: { page: params?.page ?? 1, size: params?.size ?? 12, keyword: params?.keyword } }),
          (item) => mapLibraryItem(item, "书籍"),
        ),
      communityArticles: async (params?: { page?: number; size?: number }) =>
        mapPage(
          await http.get<PageResult<unknown>>("/article/user/list/published", { query: { page: params?.page ?? 1, size: params?.size ?? 12 } }),
          (item) => mapLibraryItem(item, "社区"),
        ),
      myArticles: async (params?: { page?: number; size?: number }) =>
        mapPage(
          await http.get<PageResult<unknown>>("/article/user/list", { query: { page: params?.page ?? 1, size: params?.size ?? 10 } }),
          (item) => {
            const d = asRecord(item);
            return {
              id: asNumber(d.id),
              title: asString(d.title),
              coverUrl: asString(d.coverUrl ?? d.cover_url, ""),
              status: asNumber(d.status ?? 0, 0),
              viewCount: asNumber(d.viewCount ?? d.view_count, 0),
              likeCount: asNumber(d.likeCount ?? d.like_count, 0),
              commentCount: asNumber(d.commentCount ?? d.comment_count, 0),
              createTime: formatDateTime(d.createTime),
            };
          },
        ),
      articleDetail: async (id: number) => mapArticleDetail(await http.get<unknown>(`/content/article/detail/${id}`)),
      userArticleDetail: async (id: number) => mapArticleDetail(await http.get<unknown>(`/article/user/detail/${id}`)),
      bookDetail: async (id: number) => {
        const data = asRecord(await http.get<unknown>(`/book/${id}`));
        return {
          id: asNumber(data.id),
          title: asString(data.title, "未命名书籍"),
          content: asString(data.description ?? data.intro ?? data.content, ""),
          authorName: asString(data.author ?? data.authorName, "未知作者"),
          coverUrl: asString(data.coverUrl ?? data.cover_url ?? data.cover, ""),
          createTime: formatDateTime(data.createTime),
          viewCount: asNumber(data.viewCount ?? data.view_count),
          commentCount: asNumber(data.commentCount ?? data.comment_count),
          onlineLink: asString(data.address ?? data.onlineLink, ""),
        };
      },
      interact: (articleId: number, type: number) =>
        http.post<string>("/content/article/interact", undefined, { query: { articleId, type } }),
      comments: async (articleId: number, page = 1, size = 20) =>
        asArray(await http.get<unknown[]>(`/content/article/comments/${articleId}`, { query: { page, size } })).map((item) => {
          const d = asRecord(item);
          return {
            id: asNumber(d.id),
            content: asString(d.content),
            nickname: asString(d.nickname, "匿名"),
            headPath: asString(d.headPath ?? d.avatar, ""),
            createTime: formatDateTime(d.createTime),
            likeCount: asNumber(d.likeCount ?? d.like_count),
            liked: asBoolean(d.liked),
            userId: asNumber(d.userId ?? d.user_id),
            replies: asArray(d.replies).map((r: unknown) => {
              const reply = asRecord(r);
              return {
                id: asNumber(reply.id),
                content: asString(reply.content),
                nickname: asString(reply.nickname, "匿名"),
                replyToNickname: asString(reply.replyToNickname ?? reply.replyTo, ""),
                createTime: formatDateTime(reply.createTime),
              };
            }),
          };
        }),
      addComment: (articleId: number, content: string, parentId = 0, replyToUserId?: number) =>
        http.post<string>("/content/article/comment", { articleId, content, parentId, replyToUserId }),
      likeComment: (commentId: number) => http.post<string>(`/content/article/comment/like/${commentId}`),
      articleTags: () => http.get<unknown[]>("/article/tag/list"),
      publishArticle: (payload: { title: string; content: string; coverUrl?: string; tagId?: number }) =>
        http.post<string>("/article/user", payload),
      bookComments: async (bookId: number, page = 1, size = 10) =>
        mapPage(await http.get<PageResult<unknown>>(`/book/${bookId}/comment/list`, { query: { page, size } }), (item) => {
          const d = asRecord(item);
          return {
            id: asNumber(d.id),
            content: asString(d.content),
            nickname: asString(d.userNickname ?? d.nickname, "匿名"),
            userAvatar: asString(d.userAvatar ?? d.avatar ?? d.headPath, ""),
            createTime: formatDateTime(d.createTime),
          };
        }),
      addBookComment: (bookId: number, content: string) =>
        http.post<string>("/book/comment", { bookId, content }),
    },
  };
}

export async function streamAiChat(
  options: HttpClientOptions,
  sessionId: number,
  content: string,
  onChunk: (chunk: string, type?: "content" | "reasoning") => void,
) {
  const token = options.getToken?.();
  const response = await fetch(`${options.baseURL.replace(/\/$/, "")}/ai/chat`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ sessionId, message: content }),
  });

  if (response.status === 401) options.onUnauthorized?.();
  if (!response.ok || !response.body) {
    throw new ApiClientError(`HTTP ${response.status}`, {
      kind: response.status === 401 ? "unauthorized" : response.status === 403 ? "forbidden" : "http",
      status: response.status,
    });
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() ?? "";
    for (const line of lines) {
      const data = line.replace(/^data: ?/, "");
      if (!data) continue;
      try {
        const parsed = JSON.parse(data);
        if (parsed.type && parsed.content) {
          onChunk(parsed.content, parsed.type);
        }
      } catch {
        // Legacy: plain text chunk
        onChunk(data, "content");
      }
    }
  }
  if (buffer) {
    const data = buffer.replace(/^data: ?/, "");
    if (data) {
      try {
        const parsed = JSON.parse(data);
        if (parsed.type && parsed.content) {
          onChunk(parsed.content, parsed.type);
        }
      } catch {
        onChunk(data, "content");
      }
    }
  }
}
