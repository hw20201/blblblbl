package com.github.arca.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.github.arca.android.data.ManagedConfig
import com.github.arca.android.ui.components.*
import com.github.arca.android.ui.glass.FROSTED_PAGE_TITLE_FILL_ALPHA
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.glass.GlassTuningGroup
import com.github.arca.android.ui.glass.recordNavigationScene
import com.github.arca.android.ui.glass.rememberNavigationLens
import com.github.arca.android.ui.theme.*

private fun targetLabel(target: String): String = when (target) {
    "DIRECT" -> "直连"
    "REJECT" -> "拦截"
    else -> "代理"
}

/**
 * Managed split config, drawn exactly like the other settings pages: one
 * LazyColumn of [GlassTuningGroup] sections with [CupertinoCell] rows,
 * [ArcaGlasmioSwitch] toggles and [CupertinoSegmentedControl] target picks.
 * Changes apply immediately through [onConfigChanged]; the parent persists
 * the config, rewrites the built-in default profile and reloads the core.
 */
@Composable
fun ManagedConfigScreen(
    config: ManagedConfig,
    onConfigChanged: (ManagedConfig) -> Unit,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    embeddedPanel: Boolean = false
) {
    val titleScene = rememberNavigationLens()

    fun update(transform: (ManagedConfig) -> ManagedConfig) = onConfigChanged(transform(config))

    Box(
        Modifier.fillMaxSize()
            .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
            .testTag("managed-config-page-root")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .then(if (GlassDefaults.enabled) Modifier.recordNavigationScene(titleScene) else Modifier)
                .testTag("managed-config-list")
                .bounceOverscroll(),
            state = listState,
            contentPadding = PaddingValues(
                top = if (embeddedPanel) 8.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp,
                bottom = if (embeddedPanel) 24.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "managed-services") {
                ManagedConfigGroup("managed-services-group", "服务分流", "选择每类服务走代理、直连或拦截") {
                    ManagedConfig.SERVICE_DEFS.forEachIndexed { index, def ->
                        val option = config.serviceOption(def.name)
                        CupertinoCell(
                            modifier = Modifier.testTag("managed-group-${def.name.lowercase()}-toggle"),
                            title = def.name,
                            subtitle = def.subtitle,
                            trailing = {
                                ArcaGlasmioSwitch(
                                    checked = option.enabled,
                                    onCheckedChange = { enabled ->
                                        update { it.withService(def.name, option.copy(enabled = enabled)) }
                                    }
                                )
                            }
                        )
                        if (option.enabled) {
                            CupertinoIndentedDivider(startIndent = 16.dp)
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text("目标", fontSize = 15.5.sp, color = LoonTextPrimary)
                                Spacer(Modifier.height(10.dp))
                                CupertinoSegmentedControl(
                                    items = def.options,
                                    selectedItem = option.target,
                                    onItemSelected = { target ->
                                        update { it.withService(def.name, option.copy(target = target)) }
                                    },
                                    labelProvider = ::targetLabel,
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("managed-group-${def.name.lowercase()}-target")
                                )
                            }
                        }
                        if (index < ManagedConfig.SERVICE_DEFS.lastIndex) CupertinoIndentedDivider(startIndent = 16.dp)
                    }
                }
            }

            item(key = "managed-blocking") {
                ManagedConfigGroup("managed-blocking-group", "拦截", "QUIC 与广告拦截") {
                    CupertinoCell(
                        modifier = Modifier.testTag("managed-block-quic-toggle"),
                        title = "拦截 QUIC",
                        subtitle = "阻止 UDP 443 的 QUIC 流量，强制回落到 TCP",
                        trailing = {
                            ArcaGlasmioSwitch(
                                checked = config.blockQuic,
                                onCheckedChange = { enabled -> update { it.copy(blockQuic = enabled) } }
                            )
                        }
                    )
                    CupertinoIndentedDivider(startIndent = 16.dp)
                    CupertinoCell(
                        modifier = Modifier.testTag("managed-block-ads-toggle"),
                        title = "广告拦截",
                        subtitle = "拦截广告与追踪域名，命中时直接拒绝",
                        trailing = {
                            ArcaGlasmioSwitch(
                                checked = config.blockAds,
                                onCheckedChange = { enabled -> update { it.copy(blockAds = enabled) } }
                            )
                        }
                    )
                }
            }

            item(key = "managed-note") {
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "改动会立即写回内置默认配置；当前使用的就是内置默认配置时，核心会重新加载。内置默认配置被手动改过规则后，托管配置将不再写回。",
                        color = LoonTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Button(
                        onClick = { onConfigChanged(ManagedConfig.default()) },
                        modifier = Modifier.fillMaxWidth().testTag("managed-reset-default"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.14f),
                            contentColor = LoonTextPrimary
                        )
                    ) { Text("恢复默认") }
                }
            }
        }

        if (!embeddedPanel) Box(
            Modifier.fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .zIndex(1f)
                .testTag("managed-config-pinned-header")
        ) {
            CupertinoNavBar(
                title = "托管配置",
                showBackButton = true,
                onBack = onBack,
                frosted = true,
                titleFrostedFillAlpha = FROSTED_PAGE_TITLE_FILL_ALPHA,
                titleLens = titleScene
            )
        }
    }
}

/** The same disclosure surface and motion as the main settings groups. */
@Composable
private fun ManagedConfigGroup(
    tag: String,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassTuningGroup(
        title = title,
        subtitle = subtitle,
        tag = tag,
        initiallyExpanded = true,
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        content()
    }
}
