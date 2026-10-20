# 局域网协议

## 1. 传输

- TCP，Host 默认监听 `9527`（占用时自动尝试 `9527..9536`）。
- 帧格式：

```
+----------------------+-------------------------------+
| 4 字节大端长度 N      | N 字节 UTF-8 JSON             |
+----------------------+-------------------------------+
```

- `N` 上限 65536（`Protocol.MAX_FRAME_BYTES`）。空帧、超长帧、长度不匹配、`seq < 0` 一律视为协议错误。
- 编解码统一走 `NetworkCodec`，各模块不得私自定义 JSON。

## 2. 信封

```kotlin
@Serializable
data class Msg(
    val seq: Long,       // 发送方单调递增，用于日志/去重
    val type: MsgType,   // 标签
    val payload: String  // 类型相关的内嵌 JSON
)
```

`type` 与 payload 必须匹配；解码时会按 `type` 反序列化校验，失败返回 `BAD_PAYLOAD` / `BAD_FRAME`。

## 3. 消息

| type | payload | 方向 | 说明 |
|---|---|---|---|
| `HELLO` | `protocol, playerName, avatarSeed` | C→H | 兼容握手（等价 JOIN） |
| `JOIN` | `playerName, avatarSeed, protocol` | C→H | 加入/重连；同名抢回离线座位 |
| `ROOM_STATE` | `room, players, yourSeat, hostSeat, phase` | H→C | 座位/房间公开信息（不含手牌） |
| `DEAL` | `round, seed, dealerSeat` | H→C | 新一局开始（动画提示） |
| `BID` | `seat, score(0..3)` | C→H | 叫分；0 为不叫 |
| `PLAY` | `seat, cards: List<Int>` | C→H | 出牌，card 为 `rank*10+suit` |
| `PASS` | `seat` | C→H | 不要 |
| `SNAPSHOT` | `state, seq` | H→C | 全量脱敏状态；客户端按 seq 丢弃旧包 |
| `SYNC_REQ` | `lastKnownSeq` | C→H | 重连后请求补快照 |
| `HEARTBEAT` | `sentAt` | C→H（每 5s） | 保活 |
| `ACK` | `seq` | H→C | 已是最新 / 确认 |
| `ERROR` | `code, message` | H→C | 非法动作、版本不匹配等 |
| `KICK` | `reason` | H→C | 房主移出 |
| `LEAVE` | `seat, reason` | C→H | 主动离开 |

错误码：`WRONG_PHASE`、`NOT_YOUR_TURN`、`BAD_CARDS`、`CANNOT_BEAT`、`PASS_NOT_ALLOWED`、`SEAT_TAKEN`、`NAME_TAKEN`、`ROOM_FULL`、`VERSION_MISMATCH`、`BAD_HANDSHAKE`、`BAD_PAYLOAD`、`BAD_FRAME`、`FRAME_TOO_LARGE`、`SEAT_MISMATCH`、`PROTOCOL_ERROR`。

## 4. 同步与重连

- Host 是唯一 reducer 执行者；客户端发送 Intent，Host 校验后 `reduce`，`seq + 1`。
- 每次状态变化广播 `ROOM_STATE` + 每个座位一份 `SNAPSHOT`。
- 客户端只接受 `payload.seq > lastSeq` 的快照。
- 断线后：

```
Client                                   Host
  │── TCP connect ─────────────────────▶│
  │── JOIN(name) ──────────────────────▶│ 同名离线座位 → SetOnline(true)
  │── SYNC_REQ(lastSeq) ───────────────▶│
  │◀──────── SNAPSHOT(state, seq) ──────│ state.seq > lastSeq
  │◀──────── ACK(state.seq) ────────────│ 已是最新
```

- 心跳：客户端每 5s 发；Host 15s 未收到即断开并 `SetOnline(false)`，由 AI 托管。
- 重连退避：0.5s → 1s → 2s → 4s → 8s（上限 8s），UI 显示“重连中”。
- Host 保留座位直到结算/房主手动移出；牌局中不会把座位让给新玩家。

## 5. 脱敏

`GameState.redactedFor(viewerSeat)`：

- 非 viewer 的手牌清空，仅保留 `cardCount`；
- `bottomCards` 在叫地主完成前清空；
- `lastHand`、`log`、倍数、座位等公开信息保留。

Host 永远只广播脱敏后的状态；客户端状态不可用于作弊。
