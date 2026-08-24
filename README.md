# 链取（LinkFetch）

Android 应用：粘贴或自动识别**小红书 / 抖音 / 微博 / X**链接，一键提取**无水印**图片和视频并保存到相册。

**默认「App 直连」模式：无需服务器、无需任何配置，安装 APK 即可使用。**（小红书、抖音、微博已验证可直连；X 走其 syndication 公开接口，需要能访问海外网络。）

> ⚠️ 合规说明：去水印下载涉及平台用户协议与内容版权，本项目仅限个人学习与自用，请勿用于商用、批量爬取或分发他人内容。

## 项目结构

```
agent-project/
├── backend/                 # Python 解析后端（FastAPI，可选备用模式）
│   ├── app/
│   │   ├── main.py          # API 入口：/api/parse、/api/health
│   │   ├── platform.py      # 域名 -> 平台识别
│   │   ├── models.py        # 数据模型与统一错误码
│   │   └── parsers/         # 平台解析器（xhs / douyin / weibo / x）
│   ├── tests/               # pytest 单元测试（30 个）
│   ├── requirements.txt
│   ├── Dockerfile
│   └── docker-compose.yml
└── android/                 # Android 客户端（Kotlin + Jetpack Compose）
    └── app/src/
        ├── main/java/com/linkfetch/app/
        │   ├── data/parser/  # App 直连解析器（xhs / douyin / weibo）
        │   ├── data/         # 网络、Room 历史、DataStore 设置、相册下载
        │   └── ui/           # 首页 / 解析结果 / 历史 / 设置 + 主题
        └── test/             # JVM 单元测试
```

## 使用

### 安装即用（默认，推荐）

```bash
cd android
./gradlew.bat :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

打开 App 直接粘贴链接或整段分享文案即可解析，无需配置任何东西。部分受限内容可在「设置 → 平台 Cookie」中填入对应平台的 Cookie 提升成功率。

### 自建服务器模式（备选）

当某平台直连失效时，可在「设置 → 解析方式」切换为自建服务器：

```bash
cd backend
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt
.venv\Scripts\python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

或使用 Docker：`docker compose up -d`。然后在 App 设置中把「后端地址」填成服务器地址（真机用局域网 IP 或公网地址）。

## 错误码

后端与本地直连使用同一套错误码：

| code | 含义 |
| --- | --- |
| `unsupported_link` | 非小红书 / 抖音 / 微博 / X 链接 |
| `parse_failed` | 解析失败、链接失效、页面结构变化 |
| `rate_limited` | 平台风控，稍后重试 |
| `network_error` | 无法连接平台服务器 |

## 测试

```bash
# Android 单元测试（含本地解析器 mock 用例）
cd android && .\gradlew.bat :app:testDebugUnitTest

# 后端测试
cd backend && .venv\Scripts\python -m pytest -q

# 真实网络手动验证（可选，需要外网）
cd android && $env:LINKFETCH_REAL_TEST='1'; .\gradlew.bat :app:testDebugUnitTest --tests "*RealLinkManualTest"
```

## 已知限制与说明
- **X 平台需海外网络**：x.com 与 twimg.com 在国内网络不可达，解析 X 链接时需要挂代理或使用可访问海外网络的网络环境；X 链接在 App 内会给出明确错误提示。

- **平台接口易变**：小红书 / 抖音 / 微博 / X 会调整接口与风控策略，直连失败时优先尝试「自建服务器模式」或更新 App。
- **直连模式无热修复**：平台改版需要发布新 APK；服务器模式则只需更新后端。
- **targetSdk 33**：本机工具链为 JDK 11 + Gradle 7.5 + Android SDK 33，工程按此配置。
- **下载质量设置**：当前统一返回平台最高画质，该选项为后续版本预留。
- **v1 仅单链接解析**：批量解析（多行链接）留作 v2 扩展。

## 更新记录
- **v1.5.0**：新增 X (Twitter) 平台解析，支持图片原图与最高画质视频；历史记录新增 X 筛选。
