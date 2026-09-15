package com.linkfetch.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linkfetch.app.BuildConfig
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.ui.components.LoadingButton
import com.linkfetch.app.ui.components.PageHeader
import com.linkfetch.app.ui.components.ScreenFadeIn
import com.linkfetch.app.ui.components.VerticalSpace
import com.linkfetch.app.ui.theme.Spacing
import com.linkfetch.app.ui.theme.SuccessGreen
import com.linkfetch.app.ui.theme.WarningAmber

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

            // 直连是唯一推荐用法；服务器模式收进默认关闭的开关后面，避免占用主视觉
            SettingsSection(
                title = "解析方式",
                icon = Icons.Filled.SwapHoriz,
            ) {
                val serverMode = viewModel.parseMode == "server"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                SecretField(
                    value = viewModel.xhsCookie,
                    onValueChange = viewModel::onXhsCookieChange,
                    label = "小红书 Cookie",
                )
                VerticalSpace(8)
                SecretField(
                    value = viewModel.douyinCookie,
                    onValueChange = viewModel::onDouyinCookieChange,
                    label = "抖音 Cookie",
                )
                VerticalSpace(8)
                SecretField(
                    value = viewModel.weiboCookie,
                    onValueChange = viewModel::onWeiboCookieChange,
                    label = "微博 Cookie",
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
            VerticalSpace(6)
            Text(
                text = "Live 图以 Motion Photo 格式保存：Google 相册、小米、OPPO 等图库可直接播放动态效果，部分第三方相册仅显示静态图。",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            // 给吸底保存条留出高度，避免最后这段说明被它盖住
            VerticalSpace(96)
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
                .padding(bottom = 96.dp),
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
            )
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
    // 分区之间 16dp、分区内部 8~12dp：让"换了个话题"在间距上就看得出来，
    // 此前分区间距与分区内间距同为 8dp，几块设置糊成一片
    VerticalSpace(16)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            },
        ),
        border = if (isSystemInDarkTheme()) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
