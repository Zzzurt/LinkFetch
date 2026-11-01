# 链取 · LinkFetch

**粘贴链接，取回原片。** 从某书 / 某音 / 某博 / 某推 取回无水印的原图与原视频，按天归档，原样保存到相册。

![version](https://img.shields.io/badge/version-1.9.2-0A0A0A?style=flat-square)
![license](https://img.shields.io/badge/license-Apache--2.0-0A0A0A?style=flat-square)
![android](https://img.shields.io/badge/Android-8.0%2B-0A0A0A?style=flat-square)
![mode](https://img.shields.io/badge/direct-no%20server-0A0A0A?style=flat-square)

## 功能

- **四平台**：某书 / 某音 / 某博 / 某推
- **原图原片**：取平台原始文件，不经过二次压缩
- **分享即解析**：从任意 App 分享链接进来，也会自动读取剪贴板
- **后台队列**：批量保存交给系统调度，退出结果页仍会继续
- **历史记录**：本机保存、搜索、按日期分组，支持批量删除
- **主题**：跟随系统 / 浅色 / 深色，启动页与界面同色
- **直连优先**：失败时可自动回退到自建服务器；某书 / 某音 / 某博 支持网页登录授权
- **无广告、无统计、无账号体系**

## 下载

前往 [Releases](https://github.com/Zzzurt/LinkFetch/releases/latest) 下载 `LinkFetch-v1.9.2.apk`（约 2.8 MB），安装即用。版本变化见 [CHANGELOG](CHANGELOG.md)。

- 最低系统 Android 8.0（API 26），默认直连，无需服务器
- 某书 / 某音 / 某博 已验证可直连；某推 需能访问海外网络
- 受限内容可在「设置 → 平台凭证」点「网页登录获取」，登录后自动读取 Cookie

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

## 已知限制

- **某推 需海外网络**，否则会在 App 内给出明确错误提示
- **平台接口易变**：直连失败时优先切换自建服务器或更新 App
- **直连模式无热修复**：平台改版需要发布新 APK

> ⚠️ 去水印下载涉及平台用户协议与内容版权，请合理使用，勿用于批量爬取或分发牟利。

[Apache License 2.0](LICENSE) © 2026 Zzzurt
