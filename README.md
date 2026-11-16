# 链取 · LinkFetch

**粘贴链接，归档原片。** 把社交平台上的公开图片与视频按原画质取回，按天归档到本机相册。

![version](https://img.shields.io/badge/version-1.9.2-0A0A0A?style=flat-square)
![license](https://img.shields.io/badge/license-Apache--2.0-0A0A0A?style=flat-square)
![android](https://img.shields.io/badge/Android-8.0%2B-0A0A0A?style=flat-square)
![mode](https://img.shields.io/badge/direct-no%20server-0A0A0A?style=flat-square)

## 功能

- **一键归档**：粘贴链接或整段分享文案，解析出原画质图片与视频
- **分享即解析**：从任意 App 分享链接进来，也会自动读取剪贴板
- **后台队列**：批量保存交给系统调度，退出结果页仍会继续
- **历史记录**：本机保存、搜索、按日期分组，支持批量删除
- **主题**：跟随系统 / 浅色 / 深色，启动页与界面同色
- **直连优先**：失败时可自动回退到自建服务器
- **无广告、无统计、无账号体系**

## 技术栈

- **客户端**：Kotlin + Jetpack Compose（Material 3）、Navigation-Compose、Room、WorkManager、OkHttp、Coil，单 Activity + ViewModel 单向数据流
- **后端（可选）**：FastAPI + Pydantic，pytest 覆盖解析器与 API 契约
- **工程化**：GitHub Actions 跑单元测试、lint 与 `assembleRelease`；前后端共享解析响应契约 fixture，`test_contract.py` 与 `ParseContractTest.kt` 双向校验字段兼容性

## 安全与隐私

- 平台凭证仅保存在本机，不写入日志、不上传
- 后端默认拒绝无鉴权运行，强制 Bearer Token；未配置口令时返回 503 而非静默放开
- 短链重定向逐跳校验并限制响应体大小，整次解析 90 秒总时限，容器以非 root 运行
- 客户端强制 HTTPS，仅对本机回环与平台媒体 CDN 放行明文

## 下载

前往 [Releases](https://github.com/Zzzurt/LinkFetch/releases/latest) 下载 `LinkFetch-v1.9.2.apk`（约 2.8 MB），安装即用。版本变化见 [CHANGELOG](CHANGELOG.md)。

- 最低系统 Android 8.0（API 26），默认直连，无需服务器
- 个别平台在特定网络环境下不可用，App 内会给出明确错误提示
- 部分内容需要在「设置 → 平台凭证」内通过平台网页登录，凭证仅保存在本机

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

- **平台页面结构易变**：直连失效时优先切换自建服务器或更新 App
- **直连模式无热修复**：平台改版需要发布新 APK

## 使用边界

本项目用于个人学习与对自有或已获授权内容的本地备份。请遵守各平台服务条款与著作权法规，不要用于商业分发或大规模采集。

[Apache License 2.0](LICENSE) © 2026 Zzzurt