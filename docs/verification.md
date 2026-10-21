# 交付验证记录

- 日期：2026-10-21（v1.1.0：天地癞子 + 横屏）
- 构建机：Windows + JDK 17 (Corretto 17.0.20) + Gradle 8.9 + Android SDK 35 / Build-Tools 35.0.0
- 真机：OnePlus PLC110，Android 16（API 36），1272×2800 / 560 dpi，局域网 IP `192.168.3.91`

## 1. 构建与测试

```text
./gradlew :shared:jvmTest :androidApp:assembleRelease :desktopApp:compileKotlin
BUILD SUCCESSFUL

shared:jvmTest → 71 tests, 0 failures
androidApp:assembleRelease → androidApp-release.apk (~7 MB)
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
versionName: 1.1.0 (versionCode 2)
minSdk 24 / targetSdk 35
SHA-256: 236e7970c050bd2e60c7687f192142e61a0fe855db9e1b08c850063b55c52b42
```

## 2. 真机验证

| 项目 | 结果 |
|---|---|
| `adb install -r ddz-lanparty-1.1.0-release.apk` | `Success` |
| 启动（launcher / monkey） | 冷启动正常，无 crash |
| 首页玩法选择 | 「经典 / 天地癞子」切换正常，选中态金色描边 |
| 天地癞子发牌 | 显示 `天癞 A`，4 张 A 带金色「癞」角标；底牌未翻 |
| 叫 3 分 | 成为地主，底牌翻开，`地癞 J` 出现；倍数 ×3 |
| 癞子出牌 | 20 张手牌可用「提示」选出合法牌型并出牌；AI 正常过牌/跟牌 |
| 王炸倍数 | 打出小王 + 大王后倍数从 ×3 → ×18（癞子模式王炸 ×6） |
| 横屏 | 首页/牌桌「横屏」按钮切换成功；横屏紧凑布局显示对手、底牌、癞子、手牌、操作按钮，无裁切 |
| 竖屏回切 | 横屏牌桌点「竖屏」后恢复完整竖屏牌桌，牌局状态保留（ViewModel 存活） |
| 创建房间 | NSD 注册成功，大厅显示 `192.168.3.91:9527`、玩法「天地癞子」与座位 1/3；VPN/移动数据下仍优先选择 Wi-Fi 地址 |
| 局域网加入 | PC 通过裸 TCP `JOIN` 入座，手机端显示 `PC-Test`，`yourSeat=1`（v1.0 验证） |

未覆盖：三台真机整局对战、Android 客户端之间的 NSD 发现（需要第二台 Android 设备）。
协议/会话层由 `LanFlowTest`（真实 TCP 回环、两个客户端同时入座 + 发牌 + 快照脱敏）与
`LaiziTest`（癞子状态序列化）覆盖。

## 3. 截图

见 `docs/screenshots/`：

- `home.png` — 首页玩法选择
- `table.png` — 经典牌桌
- `laizi-table.png` — 天地癞子竖屏（天 A / 地 J）
- `laizi-landscape.png` — 天地癞子横屏紧凑布局
