# 交付验证记录

- 日期：2026-10-20
- 构建机：Windows + JDK 17 (Corretto 17.0.20) + Gradle 8.9 + Android SDK 35 / Build-Tools 35.0.0
- 真机：OnePlus PLC110，Android 16（API 36），局域网 IP `192.168.3.91`

## 1. 构建与测试

```text
./gradlew :shared:jvmTest :androidApp:assembleRelease :desktopApp:compileKotlin
BUILD SUCCESSFUL

shared:jvmTest → 61 tests, 0 failures
androidApp:assembleRelease → androidApp-release.apk (6.98 MB)
desktopApp:compileKotlin → BUILD SUCCESSFUL
```

签名：

```text
Signer #1 certificate DN: CN=Zerlin, OU=LanParty, O=Zerlin, L=Shanghai, ST=Shanghai, C=CN
SHA-256: 57:80:C8:92:44:8D:B8:CB:B1:AD:E2:3E:28:A1:E5:D3:3F:EB:55:C6:7B:A6:1F:1B:DD:C9:07:3D:D7:2B:7A:0A
```

APK：

```text
package: com.ddz.lanparty
versionName: 1.0.0 (versionCode 1)
minSdk 24 / targetSdk 35
SHA-256: 1473c0eaea107ddf226f6ef4b4a6f8134e63303510f00b4c79b8ec15d8d999d6
```

## 2. 真机验证

| 项目 | 结果 |
|---|---|
| `adb install -r ddz-lanparty-1.0.0-release.apk` | `Success` |
| 启动（launcher / monkey） | 冷启动 238 ms，无 crash |
| 首页 | 木质背景、扇形三张牌、昵称、创建/加入/练习入口正常 |
| 单机练习 | 3 人 AI 自动叫分；叫 3 分成为地主，底牌翻开（♥5 ♠9 大王），倍数 ×6 |
| 出牌 | 点击「提示」自动选牌 → 出牌成功（6 张连对），两家 AI 过牌，轮次正确回到玩家 |
| 创建房间 | NSD 注册成功，大厅显示 `192.168.3.91:9527`、座位 1/3 |
| 局域网加入 | PC 通过裸 TCP 发送 `JOIN`，手机端立即显示 `PC-Test` 入座，`yourSeat=1` |
| 手动地址 | 大厅/加入页均展示 IP:端口，NSD 失败有明确兜底文案 |

未覆盖：三台真机整局对战、Android 客户端之间的 NSD 发现（需要第二台 Android 设备）。
协议/会话层已由 `LanFlowTest`（真实 TCP 回环，两个客户端同时入座 + 发牌 + 快照脱敏）覆盖。

## 3. 截图

见 `docs/screenshots/`：`home.png`、`lobby.png`、`table.png`（真机截图，非模拟器）。
