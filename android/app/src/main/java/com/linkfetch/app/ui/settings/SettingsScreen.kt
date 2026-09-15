package com.linkfetch.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linkfetch.app.BuildConfig
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.ui.components.ButtonVariant
import com.linkfetch.app.ui.components.LoadingButton
import com.linkfetch.app.ui.components.PageHeader
import com.linkfetch.app.ui.components.PlatformBadge
import com.linkfetch.app.ui.components.PlatformDot
import com.linkfetch.app.ui.components.ScreenFadeIn
import com.linkfetch.app.ui.components.TintedPanel
import com.linkfetch.app.ui.components.VerticalSpace
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.SuccessGreen
import com.linkfetch.app.ui.theme.TextColors
import com.linkfetch.app.ui.theme.WarningAmber
import com.linkfetch.app.util.Platform

/**
 * 吸底保存条的实测高度：上下各 12dp 内边距 + 状态行 + 8dp + 按钮 40dp ≈ 90dp，取 96dp 留余量。
 * 内容底部留白与 Snackbar 抬升共用这一个值，避免两处各写一个魔数、改一处漏一处。
 */
private val SaveBarHeight = 96.dp

@Composable
fun SettingsScreen(container: AppContainer) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsRepository, container.apiClient) }
        },
    )
    val snackbarHostState = remember { SnackbarHostState() }

    // 保存 / 连接测试反馈统一走 Snackbar
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 整页包 ScreenFadeIn：进入时淡入，去掉页面硬切感（Snackbar 浮层由内部 Column 上方的 Box 承载）
    ScreenFadeIn(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        ) {
            PageHeader(title = "设置")
            VerticalSpace(8)

            // 「使用提示」：原首页新手引导条迁移到这里，作为首个分区常驻（不再是一次性可关闭横幅）。
            // 首屏因此只剩「输入 + 最近记录」两个动作，回到工具该有的样子。
            SettingsSection(
                title = "使用提示",
                icon = Icons.Filled.Info,
            ) {
                Text(
                    text = "复制平台分享链接或整段文案 → 打开 App 自动识别（或粘贴到首页输入框）→ 一键解析 → 在结果页保存到相册。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // 直连是唯一推荐用法；服务器模式收进默认关闭的开关后面，避免占用主视觉
            SettingsSection(
                title = "解析方式",
                // 图标由行内的圆块提供，分区标题不再重复放一个小图标
            ) {
                val serverMode = viewModel.parseMode == "server"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 行图标圆块：统一设置行的视觉密度，比「标题行小图标」更成组
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (serverMode) "自建服务器解析" else "App 直连解析（推荐）",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = if (serverMode) {
                                "请求经自建服务转发，需要在下方配置服务器"
                            } else {
                                "无需服务器，安装即用"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = serverMode,
                        onCheckedChange = { enabled ->
                            viewModel.onParseModeChange(if (enabled) "server" else "direct")
                        },
                    )
                }
                Text(
                    text = "自建服务器适合平台直连失效时使用；开启后需配置地址并保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = viewModel.parseMode == "server") {
                SettingsSection(
                    title = "自建服务器",
                    icon = Icons.Filled.Dns,
                ) {
                    OutlinedTextField(
                        value = viewModel.baseUrl,
                        onValueChange = viewModel::onBaseUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        // 与首页输入框同一套「白底无框」视觉：输入区是淡层面板，
                        // 白块浮在淡层上即可，不需要描边再定义一次边界
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                        ),
                        label = { Text("后端地址") },
                        placeholder = { Text("http://10.0.2.2:8000") },
                        singleLine = true,
                    )
                    val cleartext = viewModel.isCleartext
                    Text(
                        text = if (cleartext) {
                            "该地址为明文 http：Token 与 Cookie 将以明文发送，仅建议用于局域网 / 本机；公网地址会被直接拒绝。"
                        } else {
                            "自建解析服务的地址。局域网 http 需在 res/xml/network_security_config.xml 中放行。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cleartext) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    VerticalSpace(8)
                    SecretField(
                        value = viewModel.apiToken,
                        onValueChange = viewModel::onApiTokenChange,
                        label = "API Token（可选）",
                    )
                    VerticalSpace(8)
                    Row {
                        LoadingButton(
                            text = if (viewModel.testing) "测试中…" else "测试连接",
                            loading = viewModel.testing,
                            onClick = viewModel::testConnection,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SettingsSection(
                title = "平台 Cookie（可选）",
                icon = Icons.Filled.Cookie,
            ) {
                Text(
                    text = "部分受限内容需要登录态，填入对应平台 Cookie 可提升解析成功率。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Cookie 等同账号登录态，保存在应用私有目录且不参与系统备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VerticalSpace(8)
                // 三个平台各占一行「状态行」，点击展开才出现输入框。
                // 此前是三个永远长一样的空输入框 —— 用户无法从界面上判断自己到底填过没有，
                // 而这恰恰决定了"解析失败该不该怪自己没配 Cookie"。
                // 状态由字段是否为空直接判定，不新增任何存储或判断逻辑。
                // 只列这三个平台：X 走 syndication 公开接口，不需要登录态。
                CredentialRow(
                    platform = Platform.XHS,
                    label = "小红书 Cookie",
                    value = viewModel.xhsCookie,
                    onValueChange = viewModel::onXhsCookieChange,
                )
                CredentialRow(
                    platform = Platform.DOUYIN,
                    label = "抖音 Cookie",
                    value = viewModel.douyinCookie,
                    onValueChange = viewModel::onDouyinCookieChange,
                )
                CredentialRow(
                    platform = Platform.WEIBO,
                    label = "微博 Cookie",
                    value = viewModel.weiboCookie,
                    onValueChange = viewModel::onWeiboCookieChange,
                )
                VerticalSpace(4)
                TextButton(onClick = viewModel::clearCredentials) {
                    Text("清空全部凭证", color = MaterialTheme.colorScheme.error)
                }
            }

            VerticalSpace(24)
            Text(
                text = "链取 v${BuildConfig.VERSION_NAME}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            VerticalSpace(8)
            Text(
                text = "Live 图以 Motion Photo 格式保存：Google 相册、小米、OPPO 等图库可直接播放动态效果，部分第三方相册仅显示静态图。",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            // 给吸底保存条留出高度，避免最后这段说明被它盖住
            Spacer(Modifier.height(SaveBarHeight))
        }

        // 保存条吸底常驻：Cookie 区与原先的保存按钮之间隔着整个分区，
        // 填完凭证必须一路滚到底才能保存，忘点就静默丢失。
        SettingsSaveBar(
            dirty = viewModel.dirty,
            saving = viewModel.saving,
            onSave = viewModel::save,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            // 抬到保存条上方，避免与保存状态互相遮挡
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = SaveBarHeight),
        )
    }
}

/** 吸底保存条：常驻显示"是否有未保存的修改" + 保存按钮 */
@Composable
private fun SettingsSaveBar(
    dirty: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (dirty) WarningAmber else SuccessGreen),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = if (dirty) "有未保存的修改" else "设置已保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            LoadingButton(
                text = if (saving) "保存中…" else "保存设置",
                loading = saving,
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = dirty,
                // v1.8：主按钮从实心品牌蓝降为 Tonal，弱化常驻按钮的压迫感；
                // 品牌蓝在首屏与结果页保存动作上仍保留，设置页的保存是低频操作
                variant = ButtonVariant.Tonal,
            )
        }
    }
}

/**
 * 单个平台的凭证行。
 *
 * 收起态只有一行：徽标 + 平台名 + 状态点 + 是否已配置 + 展开箭头；展开后才渲染输入框。
 * 这样用户扫一眼就知道"哪些平台配过"，而不是逐个点开空框去确认。
 *
 * 状态判定直接看 value 是否为空 —— 凭证本来就存在 SettingsRepository 里，不新增状态源。
 */
@Composable
private fun CredentialRow(
    platform: Platform,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val configured = value.isNotBlank()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Radii.field)
                .clickable { expanded = !expanded }
                // 纵向 12dp 让整行触控高度超过 48dp（Material 建议的最小可点尺寸）
                .padding(vertical = Spacing.md, horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlatformBadge(platform, size = 26)
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = platform.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            // v1.8：状态点从语义色（绿/灰）改为平台彩点 —— 配置状态由右侧文字承载，
            // 颜色只承担「这是哪个平台」的身份语义，色觉障碍用户不被颜色误导。
            PlatformDot(platform)
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = if (configured) "已配置" else "未配置",
                style = MaterialTheme.typography.bodySmall,
                color = TextColors.muted,
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起$label" else "展开$label",
                tint = TextColors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(Spacing.sm))
                SecretField(value = value, onValueChange = onValueChange, label = label)
                Spacer(Modifier.height(Spacing.xs))
            }
        }
    }
}

/** 敏感字段：默认掩码显示，可切换明文。用于 API Token 与平台 Cookie，避免设置页被肩窥。 */
@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        // 与后端地址一致：「白底无框」，与上游淡层分组统一
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
        ),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "隐藏$label" else "显示$label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 分区之间 24dp（本项目的区块间距，与首页/结果页一致）、标题到卡片 4dp、卡片内 16dp：
    // 让"换了个话题"在间距上就看得出来。此前分区之间只有 16dp，与卡片内部 16dp 同值，
    // 层级读不出来。
    VerticalSpace(24)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            // 分区标题靠字号与字重拉开层级，不靠颜色。
            // 此前用的是 primary 色 + 与正文同级的 titleSmall：深色下 primary 是 #93C5FD，
            // 大面积浅蓝文字铺在页面上会让整页"到处都在强调"，反而没有重点；
            // 而颜色本来就不该当层级的主要手段 —— 色觉障碍用户会直接失去这条线索。
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
    // v1.8：分区容器从「描边卡片」改为「淡层面板」 —— 设置页是低频长页面，
    // 一屏多张描边卡会让整页像"一堆盒子"；无描边的淡层分组更接近系统设置的安静感。
    // 内部元素（输入框、Switch 行）用 surface 白块承托，层次由「白浮在淡层上」表达。
    TintedPanel {
        Column(modifier = Modifier.padding(Spacing.lg), content = content)
    }
}
