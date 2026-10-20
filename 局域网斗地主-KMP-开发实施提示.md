# 局域网斗地主 · KMP 开发实施提示词

> **用途**：本文档是给代码助手（AI Coding Assistant）的完整实施指令。请严格按「工程规范 → 模块契约 → 里程碑」顺序执行，每完成一个里程碑即自查验收清单。

---

## 1. 项目定位

- **产品**：零服务器局域网斗地主，Android 为首期入口，架构上预留 Desktop / iOS。
- **网络模型**：设备间点对点。一台设备为 **Host（房主，权威服务器）**，其余为 Client。Host 权威计算牌局，广播状态快照。
- **核心原则**：`shared` 模块 100% 纯 Kotlin 逻辑（规则/状态机/AI/协议），平台能力一律 `expect/actual` 隔离；UI 不得直接包含业务规则。

---

## 2. 工程结构与多模块划分

```
root/
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle/libs.versions.toml          # 版本目录（唯一依赖版本来源）
├─ gradle.properties
├─ .github/workflows/build.yml        # CI
├─ shared/                            # KMP 模块（本期核心）
│  ├─ build.gradle.kts
│  └─ src/
│     ├─ commonMain/kotlin/com/ddz/shared/
│     │  ├─ game-core/                # 规则、牌型、GameState、reducer
│     │  ├─ ai-engine/                # 启发式 + MCTS
│     │  ├─ network/                  # TCP 长连接、消息编解码、心跳重连
│     │  └─ discovery/                # expect: 服务发现接口
│     ├─ androidMain/                 # actual: discovery(NSD) + TCP socket
│     ├─ desktopMain/                 # actual: discovery(jmDNS) 预留
│     └─ iosMain/                     # actual: discovery(NetService/Bonjour) 预留
├─ androidApp/                        # 首期 UI 入口（Compose Multiplatform for Android）
├─ desktopApp/                        # 预留，M5 验证可编译
└─ iosApp/                            # 预留，M5 验证可编译
```

### 2.1 模块职责契约

| 模块 | 职责 | 禁止事项 |
|---|---|---|
| `game-core` | 牌型识别、出牌校验、叫/抢地主、底牌、倍数、结算、`GameState`、reducer | 不得依赖任何平台/网络/AI 代码 |
| `ai-engine` | 拆牌策略、出牌估值、MCTS | 不得持有可变全局状态；输入输出均为不可变数据 |
| `network` | `expect/actual` 之上的 TCP 会话、消息帧、心跳、快照恢复 | 不得包含游戏规则 |
| `discovery` | `expect/actual` 服务广播/发现（NSD/mDNS） | 不得承载游戏消息 |
| `androidApp` | Compose UI、动效、导航 | 不得在 UI 层重算规则，只消费 StateFlow |

### 2.2 `gradle/libs.versions.toml`（版本目录规范）

```toml
[versions]
kotlin = "2.1.0"
agp = "8.7.0"
composeMultiplatform = "1.7.3"
coroutines = "1.9.0"
serialization = "1.7.3"
ktor = "3.0.1"          # 仅备用；TCP 用 kotlinx.io + 原生 socket，按里程碑 3 方案

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

### 2.3 `shared/build.gradle.kts` 要点

```kotlin
kotlin {
    androidTarget()
    jvm("desktop")                       // desktopApp 复用
    // iosX64(); iosArm64(); iosSimulatorArm64()   // M5 再启用
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
```

---

## 3. 游戏规则内核 `game-core`

```kotlin
// 不可变状态（data class，@Serializable）
data class GameState(
    val phase: Phase,                 // Waiting / Dealing / Bidding / Playing / Settling
    val players: List<Player>,        // 固定座位 0..2
    val bottomCards: List<Card>,
    val landlordSeat: Int?,
    val currentSeat: Int,
    val lastHand: Hand?,
    val passStreak: Int,
    val multiplier: Int,
    val seq: Long,                    // 单调递增状态版本号（同步关键）
)

// 纯函数 reducer：所有状态迁移的唯一入口
fun reduce(state: GameState, action: GameAction): GameState
```

**牌型系统**（必做清单）：

- `Card(rank: Rank, suit: Suit)`；`Rank` 含 3..10, J, Q, K, A, 2, 小王, 大王；大小王为癞子之外的固定最大牌。
- 牌型枚举：`SINGLE, PAIR, TRIPLE, TRIPLE_ONE, TRIPLE_TWO, STRAIGHT, DOUBLE_STRAIGHT, AIRPLANE, AIRPLANE_WINGS, FOUR_TWO, FOUR_TWO_PAIR, BOMB, ROCKET`。
- `HandTypeMatcher.parse(cards): HandType?` → `Hand(cards, type, length, mainRank)`；`canBeat(new, last): Boolean`。
- 发牌：54 张均分 17×3 + 3 底牌；叫分/抢地主流程（1/2/3 分或抢规则二选一，文档固化一种）；倍数规则：底牌翻倍、炸弹/王炸翻倍、春天翻倍。

**测试要求**：`game-core` 每个牌型 ≥3 个正例 + 2 个反例的单元测试（commonTest，可跑 JVM）。

---

## 4. 状态机与同步（StateFlow + 快照）

- `shared` 暴露 `GameEngine`：内部持有 `MutableStateFlow<GameState>`，对外 `StateFlow<GameState>`，**唯一**写入路径是 `reduce()`。
- 每个 `GameAction`（DEAL / BID / PLAY / PASS / TIMEOUT）产生的新状态必须携带 `seq = old.seq + 1`。
- Host 是唯一执行 reducer 的节点；Client 只发送 `Intent`，Host 执行后广播 `Snapshot(state, seq)`。
- **断线重连快照恢复**：Host 为每个座位维护 `lastAckSeq`；重连客户端发送 `SYNC_REQ(lastKnownSeq)`，Host 直接回全量 `Snapshot`；若 `state.seq <= lastKnownSeq` 说明已是最新，回 ACK 即可。快照即权威，无需增量回放（斗地主状态小，全量 < 4KB）。

---

## 5. 网络层 `network` + `discovery`

### 5.1 服务发现（`_doudizhu._tcp`）

```kotlin
// discovery/commonMain — expect
expect class RoomDiscovery {
    suspend fun advertise(room: RoomInfo)          // Host: 注册 NSD/mDNS 服务
    suspend fun discover(): Flow<DiscoveredRoom>   // Client: 监听 _doudizhu._tcp
    fun stop()
}

// discovery/androidMain — actual：NsdManager
// registerService(serviceInfo { serviceType = "_doudizhu._tcp", port, ... })
// discoverServices(..., PROTOCOL_DNS_SD, listener)
```

- `RoomInfo(name: String, hostIp: String, port: Int, playerCount: Int, needsPassword: Boolean)` — 全部可序列化，作为 NSD TXT record。
- **兜底**：发现失败或列表为空时，UI 提供「手动输入 IP:Port」入口（见 §7）。

### 5.2 Host 权威 TCP 长连接

- Host 监听固定端口（默认 `9527`），每客户端一条 TCP 长连接，读写分离协程。
- 帧格式：**长度前缀（4B, Int BE）+ UTF-8 JSON 体**，上限 64KB。
- Client 每 5s 发 `HEARTBEAT`；Host 15s 未收到即判定掉线（`onTimeout(seat)` → 引擎走 TIMEOUT 或托管 AI）。
- 重连：客户端指数退避重连（0.5s→1s→2s→4s，上限 8s），断线期间 UI 显示「重连中」；Host 保留座位与状态直至 `settling` 或手动踢出。

### 5.3 消息协议（kotlinx.serialization，含 seq/type/payload）

```kotlin
@Serializable
sealed interface Msg {
    val seq: Long          // 消息序号（幂等/去重/排序）
    val type: MsgType      // 枚举标签，便于日志与分发
    val payload: String    // JSON 内嵌，@Serializable 多态
}

// payload 示例
@Serializable data class PlayPayload(val seat: Int, val cards: List<Int>)   // card = rank*10+suit 编码
@Serializable data class SnapshotPayload(val state: GameState, val seq: Long)
@Serializable data class JoinPayload(val playerName: String, val avatarSeed: Int)
```

- 必含消息：`HELLO / JOIN / ROOM_STATE / DEAL / BID / PLAY / PASS / SNAPSHOT / SYNC_REQ / HEARTBEAT / ERROR / KICK`。
- 编解码统一 `NetworkCodec.encode(msg)/decode(bytes)`，`type` 与多态 discriminator 一致，禁止各模块私自定义 JSON。
- 反序列化必须容错：未知消息类型 → `ERROR(UNKNOWN_TYPE)` 并断连，防止脏数据拖垮 Host。

---

## 6. AI 引擎 `ai-engine`（分层设计）

### 第 1 层（必做，默认）：启发式

- **拆牌**：最小化手数拆分（贪心先拆炸弹/连子/对子，再单张兜底）
- **估值**：follow(跟牌)/pass/lead 三分类打分
  - 能否一次走完 / 是否压队友 / 是否拆炸弹 / 剩余牌力差
- 保证 < 20ms 出牌，同步线程即可

### 第 2 层（可选，高难度）：MCTS

- 入口 `AiEngine.search(state, budgetMs)`：限时（默认 800ms，可配置）
- 仅对「候选动作 ≤ N」的分支展开随机 playout，迭代次数受时间预算硬约束
- 必须在 `Dispatchers.Default` 后台线程执行，通过 `withContext` 返回
- 严禁阻塞 UI 或 Host 主循环：Host 调用时并行等待，超时即用启发式结果

**接口与托管**：

- `interface AiStrategy { suspend fun decide(state: GameState): PlayIntent }`；`HeuristicAi`（默认）、`MctsAi(budgetMs)`（可选难度）。
- 掉线托管：`GameEngine` 检测座位超时 → 注入 `HeuristicAi` 代打，恢复后交还控制权。

---

## 7. Android UI（Compose Multiplatform）

### 7.1 视觉规范

- **深色木质 + 绒布质感**：`darkColorScheme` 主背景 `#1B1210`（深胡桃木），桌面区域 `#1E5B3A`（赌场绿绒）叠加噪点/径向渐变；卡片白 `#F5F1E8`，王牌金 `#D4AF37`。
- 木纹用 `Brush.linearGradient` 多层叠加 + 低透明度条纹实现，**不使用位图资源**（跨平台友好）。
- 卡牌圆角 12dp、1dp 描边、微微投影；大小王渐变高亮。

### 7.2 布局与动效

- **扇形布局**：自定义 `Layout` 或 `Canvas` 按角度排布手牌，中心在底部中央，每张牌旋转 `-18°..18°`，间距随张数自适应；选中牌上浮 16dp + 描边高亮。
- 动效清单（`Animatable` / `updateTransition` / `AnimatedVisibility`）：
  - 发牌：从左下角飞入，错峰 60ms/张，`spring` 阻尼。
  - 出牌：手牌飞出至桌面中央后淡入桌面牌区，剩余手牌重排（`animateContentSize`）。
  - 炸弹：全屏轻微震动 + 橙色闪光覆盖层 300ms + 缩放冲击波。
  - 地主：底牌三张翻开 + 「地主」徽标弹入。
- 音效（可选，M4）：出牌/炸弹/胜利三档，`MediaPlayer`，设置可关。

### 7.3 联机大厅流程

```
首页 → [创建房间] → 大厅(等待中, 显示房间名/IP/端口, NSD 已广播)
     → [加入房间] → 扫描列表(_doudizhu._tcp) → 点击加入
                    ↘ 无结果 → 手动输入 IP:Port → 连接
     → 房间内 3 人齐 → Host 开始 → 发牌动画 → 对局
```

- `LobbyViewModel` 持有 `RoomDiscovery` + `TcpSession`，UI 只观察 `uiState: StateFlow`；连接状态机：`Idle → Discovering → Connecting → Connected → InGame → Reconnecting`。
- 手动输入 IP 兜底：`OutlinedTextField` + 「连接」按钮，粘贴 `host:port` 自动解析，错误给出明确提示（超时/拒绝/版本不匹配）。

---

## 8. 里程碑与交付（M1–M5）

| 里程碑 | 目标 | 交付物 | 验收标准 |
|---|---|---|---|
| **M1** | 可运行的空壳 + 工程骨架 | 多模块编译通过；`androidApp` 深色木质首页；版本目录/CI 跑通 | `./gradlew build` + CI 绿；首页有扇形卡牌静态展示 |
| **M2** | 单机核心规则 | `game-core` 全规则 + reducer + 3 个启发式 AI 本地对战（3 个 AI 同桌） | 牌型/胜负/倍数单测 ≥80% 覆盖；日志可复盘一局 |
| **M3** | 局域网联机 | NSD 发现 + Host 权威 TCP + 协议 + 心跳 + 断线重连快照恢复 + 大厅 UI + 手动 IP | 两台真机：创建/加入/打完一局；杀客户端 10s 内重连恢复；断网走 AI 托管 |
| **M4** | 动效与体验 | 发牌/出牌/炸弹动效、音效、托管提示、设置（难度/AI 预算） | 60fps 主流机型；无明显掉帧；动画可跳过 |
| **M5** | 跨平台与收尾 | `desktopApp`/`iosApp` 可编译可进大厅（iOS 允许只到大厅）；回归测试 + 文档 | 三平台 CI 构建通过；README 含局域网玩法说明 |

> 每个里程碑独立可演示，禁止跨里程碑提交半成品（feature flag 亦不优先）。

---

## 9. 风险与对策

| 风险 | 现象 | 对策 |
|---|---|---|
| **NSD/mDNS 不稳定** | 部分国产 ROM 后台限制 NSD、发现延迟/丢包、服务名重复 | ① 房间名加随机后缀避免冲突；② 发现列表持续刷新（不去重只更新心跳时间，5s 过期剔除）；③ 手动 IP 兜底为一级功能而非补救；④ 关键路径提供「同一 Wi-Fi 检测」提示 |
| **MCTS 算力** | 低端机超时、发热、阻塞 Host | ① 时间预算硬上限（≤800ms）且线程隔离（`Dispatchers.Default` + `withTimeout` 兜底）；② 仅高难度启用；③ rollout 上限迭代次数；④ 超时/异常一律回退启发式 |
| **CMP 跨平台兼容** | expect/actual 遗漏、iOS 资源/手势差异 | ① 平台代码全部收敛到 `expect` 接口；② CI 三平台编译即失败即拦截；③ UI 组件禁用 Android 专属 API（用 CMP 通用组件）；④ iOS 首期只要求大厅可编译 |
| **状态同步** | seq 乱序、丢消息导致各端牌面不一致 | ① 全量 Snapshot + seq 单调校验，客户端 `state.seq < current` 直接丢弃；② Host 权威唯一 reducer，Client 零计算；③ 重连即全量恢复；④ 协议层消息去重（按 seq） |
| **一机多网卡/热点** | 广播与连接网卡不一致连不上 | NSD 注册在服务端口同时，UI 展示 Host 全部局域网 IP 供手动连接 |

---

## 10. 测试策略

- **commonTest**（JVM 可跑）：牌型解析/比较、reducer 全流程（发牌→叫分→出牌→结算）、倍数计算、AI 启发式（不拆炸弹场景、队友让牌）、协议编解码往返、心跳/重连状态机（`runTest` + 虚拟时钟）。
- **androidInstrumentedTest**（可选）：NSD 双机互发现在 CI 不强制，本地手动验收。
- 覆盖率目标：`game-core` ≥ 80%，`network` 编解码/状态机 ≥ 70%。
- 每个 bugfix 必须先写失败测试再修（回归优先）。

---

## 11. CI：GitHub Actions

`.github/workflows/build.yml` 要点：

- 触发：`push` / `pull_request`；JDK 17，Gradle 缓存（`actions/setup-java` + `gradle/actions/setup-gradle`）。
- Job `build`：`./gradlew :shared:jvmTest :androidApp:assembleDebug`。
- Job `verify`：`./gradlew lint`（Kotlin/Android lint）与 `ktlint`（格式），失败即红。
- Job `desktop`（M5 起）：`:desktopApp:packageDistributionForCurrentOS`。
- Job `ios`（M5 起，macOS runner）：`:shared:compileKotlinIosArm64`（仅编译验证）。
- 产物：上传 debug APK 为 artifact，便于里程碑评审。

---

## 12. 编码规范与验收清单

- Kotlin 官方风格（`kotlin.code.style=official`）+ ktlint；公共 API 必须有 KDoc；`data class`/`sealed interface` 建模状态；**禁止**可变全局单例持有游戏状态。
- 每个 expect 必须有全部平台 actual；新增平台能力先写接口再写实现。
- 提交规范：`feat(scope): ...` / `fix(scope): ...`，一个里程碑内按模块拆小 PR。
- **里程碑验收三问**：① 真机/真网可演示？② 核心单测全绿？③ 无 TODO/FIXME 遗留（已登记 issue 除外）？
