# 链取 · LinkFetch

**粘贴链接，取回原片。** 从某书 / 某音 / 某博 / 某推 取回无水印的原图与原视频，按天归档，原样保存到相册。

![version](https://img.shields.io/badge/version-1.8.3-0A0A0A?style=flat-square)
![license](https://img.shields.io/badge/license-Apache--2.0-0A0A0A?style=flat-square)
![android](https://img.shields.io/badge/Android-8.0%2B-0A0A0A?style=flat-square)
![mode](https://img.shields.io/badge/direct-no%20server-0A0A0A?style=flat-square)

## 界面

<table>
<tr>
<td align="center"><img src="https://github.com/Zzzurt/LinkFetch/releases/download/v1.8.3/home.png" width="200" alt="首页"><br><sub>首页</sub></td>
<td align="center"><img src="https://github.com/Zzzurt/LinkFetch/releases/download/v1.8.3/history.png" width="200" alt="历史"><br><sub>历史</sub></td>
<td align="center"><img src="https://github.com/Zzzurt/LinkFetch/releases/download/v1.8.3/result.png" width="200" alt="结果"><br><sub>解析结果</sub></td>
</tr>
<tr>
<td align="center"><img src="https://github.com/Zzzurt/LinkFetch/releases/download/v1.8.3/settings.png" width="200" alt="设置"><br><sub>设置</sub></td>
<td align="center"><img src="https://github.com/Zzzurt/LinkFetch/releases/download/v1.8.3/dark.png" width="200" alt="深色"><br><sub>深色模式</sub></td>
<td align="center"><img src="https://github.com/Zzzurt/LinkFetch/releases/download/v1.8.3/empty.png" width="200" alt="空状态"><br><sub>空状态</sub></td>
</tr>
</table>

## 功能

- **四平台**：某书 / 某音 / 某博 / 某推
- **原图原片**：取平台原始文件，不经过二次压缩
- **粘贴即读**：自动识别剪贴板与链接来源
- **按天归档**：历史记录本机保存，可批量选择删除
- **深色模式**：同一套层级，夜间自动切换
- **无广告、无统计、无账号体系**

## 下载

前往 [Releases](https://github.com/Zzzurt/LinkFetch/releases/latest) 下载 `LinkFetch-v1.8.3.apk`（约 3.0 MB），安装即用。

- 最低系统 Android 8.0（API 26），默认直连，无需服务器
- 某书 / 某音 / 某博 已验证可直连；某推 需能访问海外网络
- 受限内容可在「设置 → 平台凭证」填入 Cookie 提升成功率

## 使用

### 安装即用

打开 App，粘贴链接或整段分享文案即可解析；支持单张保存或「保存全部」。

### 自建服务器（可选）

直连失效时，在「设置 → 解析方式」切换为自建服务器：

```bash
cd backend
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt
set API_TOKEN=<自定义口令>
.venv\Scripts\python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

然后填写「后端地址」与同一个「API Token」。局域网 `http://` 需先加入 `android/app/src/main/res/xml/network_security_config.xml`。

### 从源码构建

```bash
cd android
./gradlew.bat :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

未配置 `.keystore/debug.keystore` 时会回退到 SDK 默认调试密钥，无需自备签名即可编译。

## 已知限制

- **某推 需海外网络**，否则会在 App 内给出明确错误提示
- **平台接口易变**：直连失败时优先切换自建服务器或更新 App
- **直连模式无热修复**：平台改版需要发布新 APK

> ⚠️ 去水印下载涉及平台用户协议与内容版权，请合理使用，勿用于批量爬取或分发牟利。

[Apache License 2.0](LICENSE) © 2026 Zzzurt
