package com.xinyuzhilian.aiadolescentmentalhealthsystem.constant;

/**
 * 小爱倾听师常量定义
 */
public class XiaoaiConstants {

    private XiaoaiConstants() {
        // 私有构造函数，防止实例化
    }

    // ==================== 时长限制（秒） ====================

    /** 默认每日免费时长：5分钟 */
    public static final int DEFAULT_DAILY_LIMIT = 300;

    /** VIP 每日时长限制：30分钟 */
    public static final int VIP_DAILY_LIMIT = 1800;

    /** SVIP 每日时长限制：60分钟 */
    public static final int SVIP_DAILY_LIMIT = 3600;

    // ==================== 会员类型 ====================

    /** 非会员 */
    public static final int MEMBER_TYPE_NORMAL = 0;

    /** VIP会员 */
    public static final int MEMBER_TYPE_VIP = 1;

    /** SVIP会员 */
    public static final int MEMBER_TYPE_SVIP = 2;

    // ==================== 会话状态 ====================

    /** 会话进行中 */
    public static final int SESSION_STATUS_ACTIVE = 1;

    /** 会话已结束 */
    public static final int SESSION_STATUS_ENDED = 0;

    // ==================== 结束原因 ====================

    /** 用户主动结束 */
    public static final String END_REASON_MANUAL = "manual";

    /** 超时自动结束 */
    public static final String END_REASON_TIMEOUT = "timeout";

    /** 错误结束 */
    public static final String END_REASON_ERROR = "error";

    /** 会员过期结束 */
    public static final String END_REASON_EXPIRED = "expired";

    // ==================== WebSocket 配置 ====================

    /** 阿里云 WebSocket URL 模板 */
    public static final String ALIYUN_WS_URL_TEMPLATE = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=%s";

    /** 主模型名称 */
    public static final String PRIMARY_MODEL = "qwen-audio-3.0-realtime-plus";

    /** 心跳间隔（秒） */
    public static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    /** 最大文本消息大小（字节） */
    public static final int MAX_TEXT_MESSAGE_SIZE = 5 * 1024 * 1024;

    /** 最大二进制消息大小（字节） */
    public static final int MAX_BINARY_MESSAGE_SIZE = 5 * 1024 * 1024;

    // ==================== 音频配置 ====================

    /** 音频采样率 */
    public static final int AUDIO_SAMPLE_RATE = 16000;

    /** 音频通道数 */
    public static final int AUDIO_CHANNELS = 1;

    /** 音频分块大小 */
    public static final int AUDIO_CHUNK_SIZE = 16 * 1024;
}
