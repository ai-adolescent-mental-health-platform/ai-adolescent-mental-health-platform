# 小爱倾听 Realtime 接入现状

**最后更新**：2026-09-15
**当前模型**：`qwen-audio-3.0-realtime-plus`

## 为什么需要这份文档

仓库里的 [接入多模态参考文档.md](接入多模态参考文档.md) 与 [音色列表.md](音色列表.md) 描述的是
**上一代模型 `qwen3.5-omni-plus-realtime`**，其音色表、`session.update` 字段与默认值均已不适用。
迁移时若照旧文档改会踩坑，故在此记录当前实际配置。

## 当前配置

| 项 | 值 |
| --- | --- |
| WebSocket URL | `wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=qwen-audio-3.0-realtime-plus` |
| 鉴权 | `Authorization: Bearer $DASHSCOPE_API_KEY` |
| 业务空间 | 请求头 `X-DashScope-WorkSpace`，取 `dashscope.api.workspace` |
| 音色 | `longanqian`（默认音色） |
| 输出模态 | `["audio", "text"]` |
| 输入音频格式 | `pcm`，16kHz / 16bit / 单声道 |
| 输出音频格式 | `pcm`，24kHz / 16bit / 单声道 |
| 转写 | **不传** `input_audio_transcription`，服务端默认自动转录 |

建上游连接的代码有两处，**改配置时两处都要动**：

- `websocket/OmniRealtimeWebSocketProxy.java` — 冷启动路径（实际生效）
- `websocket/HotSessionManager.java` — 热会话池路径（当前为死代码，见下）

## 与上一代模型的关键差异

1. **音色体系完全不同**。新模型仅支持 5 个系统音色：`longanqian`（默认）、`longanlingxin`、
   `longanlingxi`、`longanxiaoxin`、`longanlufeng`。旧的 `Ethan`、`Tina` 等全部无效。
   且 `session.voice` **只在首次 `session.update` 时生效**，后续传入会被忽略。
2. **不支持 `input_audio_transcription` 配置**。该字段不在 `session.update` 的字段表中，
   而服务端会校验参数。输入转写默认即开启，`session.created` 中可见
   `"input_audio_transcription": {"model": "qwen3-asr-flash-realtime"}`。
3. **不支持 `semantic_vad`**。新模型只支持 `server_vad` 与 `null`（push-to-talk）；
   `semantic_vad` 仅 Qwen3.5-Omni-Realtime 系列支持。
4. **`turn_detection` 只在首次发送音频之前（IDLE）允许修改**。音频一旦开始推送就无法再改，
   因此会话初始化必须在上游 WebSocket 建好之后、推音频之前送达。

## 音频发送约定

- 只接受**裸 PCM**，不能夹 WAV/RIFF 头（服务端会把那 44 字节头当音频样本解码）
- 官方建议每 **20~40ms** 发送一帧；前端 `createScriptProcessor(512, 1, 1)` 在 16kHz 下为 32ms
- 浏览器可能忽略 `AudioContext` 的 `sampleRate` 请求，若非 16kHz 需告警（已在前端处理）

## 待实测确认

以下均来自官方文档，**尚未在本环境实测**。首次联调时请逐条确认：

- [ ] 握手成功，`session.created` 的 `model` 字段为 `qwen-audio-3.0-realtime-plus`
- [ ] `session.update` 返回 `session.updated`（若返回 `error`，优先查音色值与 `modalities` 顺序）
- [ ] `conversation.item.input_audio_transcription.completed` 仍带 `transcript`
      —— 若该事件不触发，用户气泡不显示，**且** `ai_xiaoai_message` 中用户侧消息会静默丢失
      （前后端都依赖它，且后端没有任何告警）
- [ ] `response.audio.delta` 有数据，24kHz 播放正常
- [ ] `response.done` 的 `status` 为 `completed`

## 已知未修的既有问题

- **热会话池是死代码**：`HotSessionManager.MIN_HOT_SESSIONS = 0`，`warmUpSessions()` 循环 0 次，
  心跳补池条件 `currentSize < 0` 永不成立，池永远为空 → `getHotSession()` 恒返回 null。
  因此代理始终走 `createNewSession` 冷启动，该文件里所有 `session.update` 改动当前不生效。
- **模型常量重复三处**：`OmniRealtimeWebSocketProxy`、`HotSessionManager`、
  `XiaoaiConstants`（后者无任何引用）。改模型需手工同步，易漂移，建议后续统一到 `XiaoaiConstants`。
- **`session.update` 竞态**：代理只在 `NEW_SESSION_MAP` 有值时才转发前端消息，而该 Map 在上游
  `onOpen` 才填充。前端在 `ws.onopen` 立刻发 `session.update`，此时上游多半还没连上，
  消息会被静默丢弃（仅一行 `log.warn`）。因 `turn_detection` 只在推音频前可改，此竞态后果较重。
- **前端断线重连不清理音频节点**：`ws.onclose` 重连前未调 `cleanupAudioNodes()`。

## 外部文档

- WebSocket API：https://platform.qianwenai.com/docs/api-reference/qwen-audio-realtime/websocket-api
- 客户端事件：https://platform.qianwenai.com/docs/api-reference/qwen-audio-realtime/client-events
- 服务端事件：https://platform.qianwenai.com/docs/api-reference/qwen-audio-realtime/server-events
