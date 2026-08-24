package com.linkfetch.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.linkfetch.app.BuildConfig
import com.linkfetch.app.data.AppContainer
import com.linkfetch.app.ui.components.LoadingButton
import com.linkfetch.app.ui.components.VerticalSpace

@Composable
fun SettingsScreen(container: AppContainer) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsRepository, container.apiClient) }
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        VerticalSpace(8)

        SettingsSection("解析方式") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = viewModel.parseMode == "direct",
                    onClick = { viewModel.onParseModeChange("direct") },
                )
                Column {
                    Text("App 直连解析（推荐）", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "无需服务器，安装即用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = viewModel.parseMode == "server",
                    onClick = { viewModel.onParseModeChange("server") },
                )
                Column {
                    Text("自建服务器解析", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "适合平台直连失效时使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SettingsSection("服务器（自建服务器模式）") {
            OutlinedTextField(
                value = viewModel.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("后端地址") },
                placeholder = { Text("http://10.0.2.2:8000") },
                singleLine = true,
            )
            Text(
                text = "自建解析服务的地址，局域网可用 http://192.168.x.x:8000",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VerticalSpace(8)
            OutlinedTextField(
                value = viewModel.apiToken,
                onValueChange = viewModel::onApiTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Token（可选）") },
                singleLine = true,
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

        SettingsSection("平台 Cookie（可选）") {
            Text(
                text = "部分受限内容需要登录态，填入对应平台 Cookie 可提升解析成功率",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VerticalSpace(8)
            OutlinedTextField(
                value = viewModel.xhsCookie,
                onValueChange = viewModel::onXhsCookieChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("小红书 Cookie") },
                singleLine = true,
            )
            VerticalSpace(8)
            OutlinedTextField(
                value = viewModel.douyinCookie,
                onValueChange = viewModel::onDouyinCookieChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("抖音 Cookie") },
                singleLine = true,
            )
            VerticalSpace(8)
            OutlinedTextField(
                value = viewModel.weiboCookie,
                onValueChange = viewModel::onWeiboCookieChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("微博 Cookie") },
                singleLine = true,
            )
        }

        SettingsSection("下载质量") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = viewModel.quality == "hd",
                    onClick = { viewModel.onQualityChange("hd") },
                )
                Text("高清", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                RadioButton(
                    selected = viewModel.quality == "original",
                    onClick = { viewModel.onQualityChange("original") },
                )
                Text("原图", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "当前版本统一返回平台最高画质，该选项为后续版本预留",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        VerticalSpace(16)
        LoadingButton(
            text = if (viewModel.saving) "保存中…" else "保存设置",
            loading = viewModel.saving,
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth(),
        )
        viewModel.message?.let {
            VerticalSpace(12)
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
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
        VerticalSpace(12)
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    VerticalSpace(8)
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
