package com.linkfetch.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linkfetch.app.BuildConfig
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.ui.components.GhostPill
import com.linkfetch.app.ui.components.InkButton
import com.linkfetch.app.ui.components.LoadingButton
import com.linkfetch.app.ui.components.PageHeader
import com.linkfetch.app.ui.components.ScreenFadeIn
import com.linkfetch.app.ui.components.VerticalSpace
import com.linkfetch.app.ui.theme.MonoError
import com.linkfetch.app.ui.theme.Radii
import com.linkfetch.app.ui.theme.StrokeColors
import com.linkfetch.app.ui.theme.TextColors
import com.linkfetch.app.util.Platform

/** SettingsRepository 的出厂地址；只有改过它才算「用户配置过」 */
private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"

@Composable
fun SettingsScreen(container: AppContainer) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsRepository, container.apiClient) }
        },
    )
    val snackbarHostState = remember { SnackbarHostState() }
    var serverExpanded by remember { mutableStateOf(false) }

    // 保存 / 连接测试反馈统一走 Snackbar
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    ScreenFadeIn(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp),
        ) {
            // 保存动作上移到页头：设置是长页面，原来吸底那条 96dp 的横条占掉整屏高度，
            // 而「未保存」这件事只需要一个会变亮的胶囊就能说清。
            PageHeader(
                title = "设置",
                actions = {
                    GhostPill(
                        text = if (viewModel.saving) "保存中…" else "保存",
                        onClick = viewModel::save,
                        enabled = (viewModel.dirty || viewModel.saving) && !viewModel.saving,
                    )
                },
            )

            VerticalSpace(18)

            // 一段常驻的使用提示：复制链接回来就能用，这是最省事的路径
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radii.card)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(Radii.field)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "复制链接后回到 App",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "自动读取剪贴板，也可以手动粘贴",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextColors.muted,
                    )
                }
            }

            // ---------- 解析方式 ----------
            SettingsSectionLabel("解析方式")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconCell(Icons.Filled.SwapHoriz)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "App 直连解析",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "免登录、免配置，打开即用",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextColors.muted,
                        )
                    }
                    Switch(
                        checked = viewModel.parseMode != "server",
                        onCheckedChange = { direct ->
                            viewModel.onParseModeChange(if (direct) "direct" else "server")
                            if (!direct) serverExpanded = true
                        },
                        colors = monoSwitchColors(),
                    )
                }

                GroupDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { serverExpanded = !serverExpanded }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconCell(Icons.Filled.Dns)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自建服务器",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "直连失效时作为备用通道",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextColors.muted,
                        )
                    }
                    Text(
                        text = when {
                            viewModel.baseUrl.isBlank() -> "未配置"
                            viewModel.baseUrl.trim() == DEFAULT_BASE_URL -> "默认"
                            else -> "已配置"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextColors.muted,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (serverExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (serverExpanded) "收起服务器配置" else "展开服务器配置",
                        tint = TextColors.muted,
                        modifier = Modifier.size(17.dp),
                    )
                }

                AnimatedVisibility(visible = serverExpanded) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                        MonoField(
                            value = viewModel.baseUrl,
                            onValueChange = viewModel::onBaseUrlChange,
                            label = "后端地址",
                            placeholder = "http://10.0.2.2:8000",
                        )
                        VerticalSpace(8)
                        MonoSecretField(
                            value = viewModel.apiToken,
                            onValueChange = viewModel::onApiTokenChange,
                            label = "API Token（可选）",
                        )
                        VerticalSpace(8)
                        Text(
                            text = if (viewModel.isCleartext) {
                                "该地址为明文 http：Token 与 Cookie 将以明文发送，仅建议用于局域网 / 本机；公网地址会被直接拒绝。"
                            } else {
                                "自建解析服务的地址。局域网 http 需在 res/xml/network_security_config.xml 中放行。"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = if (viewModel.isCleartext) MonoError else TextColors.muted,
                        )
                        VerticalSpace(10)
                        LoadingButton(
                            text = if (viewModel.testing) "测试中…" else "测试连接",
                            loading = viewModel.testing,
                            onClick = viewModel::testConnection,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        VerticalSpace(10)
                    }
                }
            }

            // ---------- 平台凭证 ----------
            SettingsSectionLabel(
                title = "平台凭证",
                note = "仅存本机，不参与备份",
            )
            SettingsCard {
                CredentialRow(
                    platform = Platform.XHS,
                    code = "XHS",
                    label = "小红书 Cookie",
                    value = viewModel.xhsCookie,
                    onValueChange = viewModel::onXhsCookieChange,
                )
                GroupDivider()
                CredentialRow(
                    platform = Platform.DOUYIN,
                    code = "DY",
                    label = "抖音 Cookie",
                    value = viewModel.douyinCookie,
                    onValueChange = viewModel::onDouyinCookieChange,
                )
                GroupDivider()
                CredentialRow(
                    platform = Platform.WEIBO,
                    code = "WB",
                    label = "微博 Cookie",
                    value = viewModel.weiboCookie,
                    onValueChange = viewModel::onWeiboCookieChange,
                )
                if (viewModel.xhsCookie.isNotBlank() ||
                    viewModel.douyinCookie.isNotBlank() ||
                    viewModel.weiboCookie.isNotBlank()
                ) {
                    GroupDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = viewModel::clearCredentials)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "清空全部凭证",
                            style = MaterialTheme.typography.bodySmall,
                            color = MonoError,
                        )
                    }
                }
            }

            // 下载质量一栏已取消：直连与保存都按原图原片走，没有可选的档位，
            // 留一个永远「即将上线」的控件只是在暗示一个不存在的承诺。

            VerticalSpace(26)
            Text(
                text = "链取 ${BuildConfig.VERSION_NAME} · 版本 ${BuildConfig.VERSION_CODE}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = TextColors.muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            VerticalSpace(10)
            Text(
                text = "Live 图以 Motion Photo 保存，部分图库仅显示静态图。",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                color = TextColors.muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            VerticalSpace(18)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp),
        )
    }
}

@Composable
private fun SettingsSectionLabel(title: String, note: String? = null) {
    VerticalSpace(22)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
            color = TextColors.muted,
        )
        note?.let {
            Spacer(Modifier.width(9.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                color = TextColors.muted.copy(alpha = 0.62f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radii.card)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, StrokeColors.card, Radii.card),
        content = content,
    )
}

@Composable
private fun GroupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 行首的小方块图标位：32dp、淡灰底，让三组设置行有统一的起点 */
@Composable
private fun IconCell(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(Radii.field)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun monoSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.surface,
    checkedTrackColor = MaterialTheme.colorScheme.onSurface,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = StrokeColors.card,
)

/**
 * 单个平台的凭证行。
 *
 * 收起态只有一行：平台代号 + 平台名 + 状态 + 展开箭头；展开后才渲染输入框。
 * 这样用户扫一眼就知道"哪些平台配过"，而不是逐个点开空框去确认。
 */
@Composable
private fun CredentialRow(
    platform: Platform,
    code: String,
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
                .clickable { expanded = !expanded }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 平台身份用三字母代号，而不是彩色圆标 —— 这一版把颜色全部让给内容
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(Radii.field)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        letterSpacing = 0.6.sp,
                    ),
                    color = TextColors.muted,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = platform.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (configured) "已配置" else "未配置",
                style = MaterialTheme.typography.bodySmall,
                color = TextColors.muted,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起$label" else "展开$label",
                tint = TextColors.muted,
                modifier = Modifier.size(17.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                MonoSecretField(
                    value = value,
                    onValueChange = onValueChange,
                    label = label,
                )
                VerticalSpace(10)
            }
        }
    }
}

/** 普通输入框：浅灰底、无描边，与白卡形成「可输入 / 只读」的层次 */
@Composable
private fun MonoField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        shape = Radii.field,
        colors = monoFieldColors(),
        textStyle = MaterialTheme.typography.bodyMedium,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            )
        },
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = TextColors.muted,
            )
        },
        singleLine = true,
    )
}

/** 敏感字段：默认掩码显示，可切换明文。用于 API Token 与平台 Cookie，避免设置页被肩窥。 */
@Composable
private fun MonoSecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        shape = Radii.field,
        colors = monoFieldColors(),
        textStyle = MaterialTheme.typography.bodyMedium,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            )
        },
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
                    tint = TextColors.muted,
                    modifier = Modifier.size(17.dp),
                )
            }
        },
    )
}

@Composable
private fun monoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
    unfocusedLabelColor = TextColors.muted,
    cursorColor = MaterialTheme.colorScheme.onSurface,
)
