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

## 视频多模态：当前模型不支持

`qwen-audio-3.0-realtime-plus` 是**纯语音**模型（官方用户指南标题即「语音转语音」），
其 `session.update` 字段表中没有图像相关配置，官方全模态文档的上下文限制表里
也没有它——**该模型没有视频能力**。

视频/图像输入属于 **Qwen-Omni 系列**（`qwen3.5-omni-plus-realtime` 等），
见 [全模态（实时多模态语音）](https://platform.qianwenai.com/docs/developer-guides/speech/realtime-multimodal-speech)。
该文档的上下文限制表给出视频能力：plus 保留 50 轮 / 240 秒画面，flash 保留 50 轮 / 120 秒，
视频以抽帧方式输入（建议 1 帧/秒）。

若日后要启用视频，需要：

1. 模型换回 `qwen3.5-omni-plus-realtime`（或 flash）
2. 音色从 5 个 `longan*` 换回 Omni 系列音色（`Ethan`、`Tina` 等，见 [音色列表.md](音色列表.md)）
3. WebSocket 协议下用 `input_image_buffer.append` 发送**整张图**的 base64，
   不要分块；发送前需至少发过一帧音频
4. 前端目前**完全没有把摄像头画面发给模型**：`toggleVideoMode()` 只做本地预览
   （`<video>` 显示），`videoStreamRef` 没有任何一帧进入 WebSocket。
   唯一发图的是手动上传图片的 `handleImageUpload`，且其分块发送方式无官方依据

其余配置（不传 `input_audio_transcription`、`modalities` 顺序）两边通用，不需要回退。

## 前端事件契约易错点

- **文本事件随输出模态切换**。`modalities` 为 `["audio","text"]` 时，文本走
  `response.audio_transcript.delta` / `.done`；只有 `["text"]` 时才是
  `response.text.delta` / `.done`（官方服务端事件文档）。前端必须处理**增量**事件：
  只处理 `.done` 的话，整段文字要等回复生成完才一次性蹦出来，表现为「音频在实时播、
  文字却是整块发过来」。两组事件当前均已接入，增量追加到同一气泡，
  `.done` 用服务端全文收尾。

- **今日历史消息只能加载一次**。`loadTodayMessages` 所在 effect 若被重复触发，
  会把同一批历史消息在界面上叠加多份，表现为「说一句话却看到两条一模一样的回复」。
  此前 `loadTimeInfo` 的依赖数组里放了它自己 set 的 state（`memberType`/`dailyLimit`），
  首次拉取成功后依赖变化导致 effect 重跑，历史消息因此被加载两次。
  现已把该依赖去掉，并在 `loadTodayMessages` 入口加加载标记兜底。
  排查时注意区分：这种重复**只存在于界面**，数据库中只有一条用户消息与一条 AI 消息
  （刷新后若数据库真是两条，界面会显示四条）。

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
