# LinkFetch UI 优化交付说明

> 范围：UI-only。未改动数据层 / 解析 / 下载 / 导航路由 / ViewModel 核心逻辑（仅历史页新增 `selectAllOrClear()` 全选支持）。
> 构建：`gradlew :app:assembleDebug --no-daemon --offline -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process` → **BUILD SUCCESSFUL in 2m 28s**（33 actionable tasks: 8 executed, 25 up-to-date）。

## 交付物

### Release 最新版（v1.6.7 / versionCode 19）

| 物 | 路径 |
| --- | --- |
| **GitHub Release** | https://github.com/Zzzurt/LinkFetch/releases/tag/v1.6.7 |
| Release APK 附件 | `LinkFetch-v1.6.7.apk`（13,399,924 B，已上传，sha256 `5264ba27...`） |
| 本地发布包 | `LinkFetch-v1.6.7.apk`（项目根目录，不入库） |
| Release 构建日志 | 本机构建输出目录（由 `-Plinkfetch.buildDir` 指定，不入库） |

v1.6.7 内容：单条历史记录删除增加确认弹窗（防误删）。aapt 校验 `versionCode=19 versionName=1.6.7`，`assembleRelease` 构建通过（1m15s），提交 `feb2ebc` 已推送 main。

### Release 版（v1.6.6 / versionCode 18）

| 物 | 路径 |
| --- | --- |
| **Release APK（正式分发包）** | `LinkFetch-v1.6.6.apk`（13,398,448 B，项目根目录） |
| Release 构建产物原件 | 构建输出目录 `app/outputs/apk/release/app-release.apk`（不入库） |
| Release 构建日志 | 本机构建输出目录（不入库） |
| Release 冒烟截图（模拟器安装运行） | 本机构建输出目录（不入库） |

Release 校验：
- `aapt dump badging`：`package com.linkfetch.app versionCode=18 versionName=1.6.6 minSdk=26 targetSdk=33`
- `apksigner verify --print-certs`：`CN=Android Debug`（本项目发布约定：复用 debug 证书，可安装；如需上架商店请更换正式 keystore）
- 模拟器（Nexus_4_API_27）安装 + 启动成功；`dumpsys package` 确认 `versionName=1.6.6`

### Debug 版

| 物 | 路径 |
| --- | --- |
| APK（debug，19,241,241 B） | 构建输出目录 `app/outputs/apk/debug/app-debug.apk`（不入库） |
| 构建日志 | 本机构建输出目录（不入库） |
| 真机截图（6 张，已人工复核） | 本机构建输出目录（不入库） |

## 改动清单（9 个文件）

1. **`res/values/colors.xml` / `res/values-night/colors.xml`** — 窗口背景对齐 Compose 主题：浅色 `#F8FAFC`（Slate50）、深色 `#0B1220`（DarkBackground），消除启动/返回的白闪与深色不一致。
2. **`ui/components/Components.kt`**
   - `PlatformBadge`：纯色圆标 → 平台色渐变（深色取降饱和色 `badgeColorDark`）。
   - `GroupCard`：深色模式下加 1dp `outlineVariant` 描边，替代平铺层次。
3. **`ui/home/HomeScreen.kt`**
   - 品牌头部 → 渐变 Hero 卡（primaryContainer → background），收拢顶部视觉。
   - 剪贴板横幅：整条可点击解析（此前文案提示"点击解析"实为无效交互）+ 平台色左边条 + 显式「去解析」按钮。
   - 输入卡：空输入时按钮禁用；空态提供「粘贴」快捷按钮；键盘 Go 键直接解析。
   - 「支持平台」Row → FlowRow，防窄屏/大字体溢出。
4. **`ui/result/VideoPlayerView.kt`** — 黑底直出 → 封面遮罩（点击播放）+ `keepScreenOn` 播放常亮。
5. **`ui/result/ResultScreen.kt`**
   - 视频卡在外层 Box 居中，消除 9:16 与 `heightIn(max=380dp)` 的尺寸冲突。
   - 奇数张图片：最后一张通栏 16:9，打破全 1:1 方块单调。
   - 下载中：卡片底部进度条 → 右下胶囊进度环（含进度）。
   - 保存成功：对勾弹性动画保留 + 新增左下「已保存」角标。
   - 底部操作条：文案统一「保存」（已保存 x/y、全部保存、已全部保存到相册完成态 + 对勾）；完成态禁用按钮。
   - 计数文本应用 tabular-nums（此前 `TabularNums` 常量定义了但从未生效）。
6. **`ui/history/HistoryViewModel.kt`** — 新增 `selectAllOrClear()`（全选/取消全选当前筛选可见项）。
7. **`ui/history/HistoryScreen.kt`**
   - 列表按日期分组：今天 / 昨天 / M月d日。
   - 多选模式：顶部删除图标移除，新增底部操作条（全选|取消全选 / 已选 N 项 / 删除），拇指可达。
   - 封面空态：灰底 → 平台色渐变底 + 徽标。
   - 历史卡片深色描边；「已下载 N」→「已保存 N」。
8. **`ui/settings/SettingsScreen.kt`**
   - 分区加图标（解析方式/服务器/Cookie/下载质量）。
   - 「下载质量」分区：RadioButton 禁用 + 文案降透明度 + 「即将上线」徽标（此前可点击、与"预留"文案矛盾）。
   - 保存/测试反馈：普通 Text → Snackbar（含 `consumeMessage()`）。
   - 设置卡片深色模式改用 surface + 描边。

## 验证

- **编译**：`assembleDebug` 成功（唯一一处编译报错 `FlowRow.verticalArrangement` 在本版 foundation 不存在，已移除该参数后通过）。
- **真机（模拟器 Nexus_4_API_27）截图人工复核**：
  - 浅色：首页（Hero 渐变、禁用态按钮、粘贴图标、平台徽标渐变）、历史空状态、设置页三段（图标 / Cookie / 质量禁用+徽标）。
  - 深色：首页（深色渐变 Hero、描边卡片、平台色降饱和徽标）。
- **未覆盖**：结果页媒体网格与剪贴板横幅需要真实平台链接/剪贴板内容，无法在离线模拟器演示；两处均已通过编译与代码审查确认。历史多选底部条需已有记录触发，逻辑简单且已编译验证。
