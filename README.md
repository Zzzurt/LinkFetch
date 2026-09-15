# 链取（LinkFetch）

**当前版本：v1.7.6（versionCode 28）**

Android 应用：粘贴或自动识别**某书 / 某音 / 某博 / 某推**链接，一键提取**无水印**图片和视频并保存到相册。

**默认「App 直连」模式：无需服务器、无需任何配置，安装 APK 即可使用。**（某书、某音、某博已验证可直连；某推 走其 syndication 公开接口，需要能访问海外网络。）

> ⚠️ 合规说明：去水印下载涉及平台用户协议与内容版权，请合理使用，勿用于批量爬取或对他人内容进行分发牟利。本项目基于 [Apache-2.0](LICENSE) 许可证开源，© 2026 Zzzurt。

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

> 发布正式包需要自行配置签名（密钥与口令不随仓库分发）；未配置时 `assembleRelease` 会产出未签名 APK 并给出警告。

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

