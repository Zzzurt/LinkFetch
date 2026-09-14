# 链取（LinkFetch）

**当前版本：v1.7.3（versionCode 25）**

Android 应用：粘贴或自动识别**某书 / 某音 / 某博 / 某推**链接，一键提取**无水印**图片和视频并保存到相册。

**默认「App 直连」模式：无需服务器、无需任何配置，安装 APK 即可使用。**（某书、某音、某博已验证可直连；某推 走其 syndication 公开接口，需要能访问海外网络。）

> ⚠️ 合规说明：去水印下载涉及平台用户协议与内容版权，请合理使用，勿用于批量爬取或对他人内容进行分发牟利。本项目基于 [Apache-2.0](LICENSE) 许可证开源，© 2026 Zzzurt。

## 项目结构

```
LinkFetch/
├── backend/                 # Python 解析后端（FastAPI，可选备用模式）
│   ├── app/
│   │   ├── main.py          # API 入口：/api/parse、/api/health
│   │   ├── platform.py      # 域名 -> 平台识别
│   │   ├── models.py        # 数据模型与统一错误码
│   │   └── parsers/         # 平台解析器（xhs / douyin / weibo / x）
│   ├── tests/               # pytest 单元测试（覆盖平台识别 / API / 各解析器）
│   ├── requirements.txt
│   ├── Dockerfile
│   └── docker-compose.yml
└── android/                 # Android 客户端（Kotlin + Jetpack Compose）
    └── app/src/
        ├── main/java/com/linkfetch/app/
        │   ├── data/parser/  # App 直连解析器（xhs / douyin / weibo / x）+ 页面 JSON 提取、Live 图
        │   ├── data/         # api 后端 HTTP 客户端、Room 历史、DataStore 设置、download 相册下载
        │   └── ui/           # 首页 / 解析结果 / 历史 / 设置 + 导航 / 组件 / 主题
        └── test/             # JVM 单元测试
```

## 使用

### 安装即用（默认，推荐）

从 [Releases](https://github.com/Zzzurt/LinkFetch/releases/latest) 下载最新的 `LinkFetch-v{x.y.z}.apk` 安装即可，**无需服务器、无需任何配置**。

打开 App 直接粘贴链接或整段分享文案即可解析。部分受限内容可在「设置 → 平台 Cookie」中填入对应平台的 Cookie 提升成功率。

### 从源码构建（开发者）

```bash
cd android
./gradlew.bat :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

克隆仓库后可直接构建 debug 包：当仓库内不存在 `.keystore/debug.keystore`（该目录已 gitignore）时，Gradle 会回退到 Android SDK 自动生成的默认调试密钥，因此无需自备密钥即可编译。

> 发布正式包需要额外配置签名，见下方「签名与密钥」；未配置时 `assembleRelease` 会产出未签名 APK 并给出警告。

### 自建服务器模式（备选）

当某平台直连失效时，可在「设置 → 解析方式」切换为自建服务器：

```bash
cd backend
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt
set API_TOKEN=<自定义口令>          # 必填，详见「安全默认值」
.venv\Scripts\python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

或使用 Docker：`docker compose up -d`。然后在 App 设置中把「后端地址」填成服务器地址，并在「API Token」中填入同一个口令。

> **局域网 http 需要显式放行**：App 默认禁止明文流量，`http://192.168.x.x:8000` 会被拦截。
> 请把该地址加入 `android/app/src/main/res/xml/network_security_config.xml`（文件内有示例），
> 或改用 HTTPS。

## 安全默认值

以下都是**刻意设置的默认行为**，改动前请先了解后果：

| 项 | 默认行为 | 说明 |
| --- | --- | --- |
| 明文流量 | 仅允许本机 / 内网地址使用 `http://`，公网地址必须 HTTPS | Token 与平台 Cookie 属账号级凭证，明文发出即可被同网段嗅探 |
| 后端鉴权 | 未配置 `API_TOKEN` 时拒绝所有解析请求（403/503） | 该接口可带任意 Cookie 代发请求，无鉴权暴露在公网等于开放代理 |
| 跨域 | 默认不开放 CORS | 移动端不依赖同源策略；如需网页调用，用 `CORS_ALLOW_ORIGINS` 列出具体来源 |
| 系统备份 | DataStore（服务器地址 / Token / Cookie）不参与云备份与换机迁移 | 避免凭证随备份外泄；历史记录仍会备份 |
| 设置页显示 | Token 与 Cookie 默认掩码，可点击切换明文；提供「清空全部凭证」 | 防止肩窥与录屏泄露 |

## 签名与密钥

发布版 APK 使用正式发布密钥签名。密钥与口令**均不参与版本控制**（`.gitignore` 已排除 `.keystore/`、`keystore.properties`、`*.keystore`、`*.jks`）。

正式签名信息由 `android/keystore.properties` 提供（克隆仓库者拿不到该文件，属预期）：

```properties
storeFile=../.keystore/linkfetch-release.keystore
storePassword=***
keyAlias=***
keyPassword=***
```

签名方案为 **v2 + v3**（minSdk 26 无需 v1）。启用 v3 是为了将来更换密钥时可用 signing lineage 做平滑迁移。

> ⚠️ **密钥是唯一能更新本应用的东西**，且本项目未接入 Play App Signing，没有任何找回途径。请务必将密钥文件与口令一起离线备份到至少两处。

> ⚠️ **换用正式发布密钥后，此前用调试密钥签名的版本无法覆盖安装。** Android 要求同包名 + 同签名才能更新，需先卸载旧版再安装；应用内的设置（服务器地址 / API Token / 平台 Cookie）与历史记录会被清空，相册中已下载的图片和视频不受影响。

## 错误码

后端与本地直连使用同一套错误码：

| code | 含义 |
| --- | --- |
| `unsupported_link` | 非某书 / 某音 / 某博 / 某推 链接 |
| `parse_failed` | 解析失败、链接失效、页面结构变化 |
| `rate_limited` | 平台风控，稍后重试 |
| `network_error` | 无法连接平台服务器 |

## 测试

```bash
# Android 单元测试（含本地解析器 mock 用例）
cd android && .\gradlew.bat :app:testDebugUnitTest

# 后端测试
cd backend && .venv\Scripts\python -m pytest -q

# 真实网络验证（可选，需要外网）——会真实请求各平台
cd android
set LINKFETCH_REAL_TEST=1
.\gradlew.bat :app:testDebugUnitTest --tests "*RealLinkManualTest"
```

真实网络用例的环境依赖（不满足时**跳过**而不是失败，避免把环境限制误判成解析器回归）：

| 平台 | 前置条件 |
| --- | --- |
| 小红书 / 微博 | 可直连即可 |
| X | 需要能访问海外网络（探测不通过则跳过） |
| 抖音 | 需 `set LINKFETCH_DOUYIN_COOKIE=<浏览器 Cookie>`；免 Cookie 的分享页已不再内嵌作品数据，设备侧实际依赖 WebView 兜底，无法在 JVM 单测中覆盖 |

## 已知限制与说明
- **某推 平台需海外网络**：解析 某推 链接时需要挂代理或使用可访问海外网络的网络环境；某推 链接在 App 内会给出明确错误提示。

- **平台接口易变**：某书 / 某音 / 某博 / 某推 会调整接口与风控策略，直连失败时优先尝试「自建服务器模式」或更新 App。
- **直连模式无热修复**：平台改版需要发布新 APK；服务器模式则只需更新后端。
- **targetSdk 33**：本机工具链为 JDK 11 + Gradle 7.5 + Android SDK 33，工程按此配置。
- **v1 仅单链接解析**：批量解析（多行链接）留作 v2 扩展。

## 更新记录
- **v1.7.3**：UI/可用性专项（共 27 项，除「字符串抽离」外全部落地）。重点：**结果页图片网格改为「相册式」密排**（4dp 圆角 / 2dp 间隙 / 无卡片底，图片连成一片，网格获得 item 复用）；**修正竖屏视频卡下方出现大块空白的布局错误**；结果页单项保存按钮触控目标提升到 48dp 且位置不再随状态跳变；作品标题提权到 20sp；底部操作条在未开始保存时折叠为单行；首页按钮改名「开始解析」并统一「解析 / 保存 / 记录」术语；设置页保存按钮吸底常驻 + 未保存状态提示、解析方式开关即时生效、移除占位的「下载质量」分区；历史页新增常驻「选择」入口并补全读屏语义；深色模式卡片描边与浅色首页 Hero 渐变恢复可见；全局反馈统一为 Snackbar；平板 / 折叠屏限宽居中（600dp）与大字体自适应；无障碍（触控目标 / 对比度 / 语义标签）多项修复。
- **v1.7.2**：**更换为正式发布密钥签名（v2 + v3）**——此前版本由调试密钥签名，从本版起旧版无法覆盖安装，需先卸载再安装（详见「签名与密钥」）；安全加固（详见「安全默认值」）：默认禁止明文流量与凭证参与系统备份、后端默认拒绝无鉴权请求、设置页凭证掩码可一键清空；解析健壮性：**修正抖音在风控（403/429）时提前终止回退链、导致 WebView 兜底永不执行的问题**，失败时给出可执行提示与逐级诊断，X 平台网络失败时明确提示需要海外网络；设置页把服务器模式收进默认关闭的开关；修复若干缺陷：API 28 及以下保存到相册因缺少存储权限而失败、下载异常未收敛导致崩溃、混合媒体顺序下保存/预览指向错误条目、视频播放器实例泄漏、HLS 加密检测漏判、视频被写入 Pictures 目录、克隆仓库后因缺少密钥导致 `assembleDebug` 直接失败。
- **v1.7.1**：UI 优化——底部导航栏紧凑化（未选中仅图标，选中切换为文字标签）；图片预览支持左右滑动与箭头按钮切换图片。
- **v1.7.0**：优化抖音解析兼容性（适配详情页结构变化）。
- **v1.6.9**：优化抖音解析兼容性。
- **v1.6.8**：开启 R8 代码压缩与资源收缩，安装包体积从约 13.4 MB 降至约 2.9 MB；修复 Windows 下仓库位于非系统盘时 KSP 构建失败的问题。
- **v1.6.7**：单条历史记录删除增加确认弹窗，防止误删（与批量删除行为一致）。
- **v1.6.6**：UI 优化——首页品牌渐变区、剪贴板横幅可点击、空态粘贴按钮与键盘回车解析；设置页分区图标与下载质量占位禁用；结果页视频封面遮罩、图片奇数布局、下载进度环与底部条完成态；历史页日期分组、多选底部操作条与平台色封面。
- **v1.6.x**：新增 Live 图（实况 / 动态照片）支持，某书 / 某音 / 某博 的实况图可合成为 Motion Photo 保存（[MotionPhotoWriter](android/app/src/main/java/com/linkfetch/app/data/download/MotionPhotoWriter.kt)）；某推 长视频支持 HLS / VMAP 解析与 vxtwitter / fxtwitter 回退；历史记录支持平台筛选与多选删除。
- **v1.5.0**：新增 某推 平台解析，支持图片原图与最高画质视频；历史记录新增 某推 筛选。
