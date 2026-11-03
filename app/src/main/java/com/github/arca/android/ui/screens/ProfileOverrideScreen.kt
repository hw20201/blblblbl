package com.github.arca.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arca.android.core.ClashCore
import com.github.arca.android.data.ConfigManager
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.ProfileParser
import com.github.arca.android.data.ScriptItem
import com.github.arca.android.data.ScriptManager
import com.github.arca.android.data.SettingsManager
import com.github.arca.android.ui.components.*
import com.github.arca.android.ui.glass.GlassDefaults
import com.github.arca.android.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileOverrideScreen(
    profile: ProfileItem,
    onBack: () -> Unit,
    onNavigateToScripts: () -> Unit,
    onProfileUpdated: (ProfileItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allScripts by remember { mutableStateOf(emptyList<ScriptItem>()) }

    LaunchedEffect(Unit) {
        allScripts = withContext(Dispatchers.IO) { ScriptManager.getScripts(context) }
    }
    val selectedScriptIds = remember(profile) { profile.scriptIds.toMutableStateList() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
            .statusBarsPadding()
    ) {
        // Cupertino Navigation Bar
        CupertinoNavBar(
            title = "配置覆写",
            subtitle = profile.name,
            showBackButton = true,
            onBack = onBack,
            actions = {
                TextButton(
                    onClick = {
                        val updated = profile.copy(
                            scriptEnabled = selectedScriptIds.isNotEmpty(),
                            scriptIds = selectedScriptIds.toList()
                        )
                        ConfigManager.updateProfile(context, updated)
                        ProfileParser.clearCache()
                        onProfileUpdated(updated)
                        scope.launch(Dispatchers.IO) {
                            val settings = SettingsManager(context)
                            val profiles = ConfigManager.getProfiles(context)
                            val activeProfile = profiles.firstOrNull { it.id == settings.selectedProfileId } ?: profiles.firstOrNull()
                            if (activeProfile?.id == profile.id && ClashCore.isCoreLoaded) {
                                runCatching {
                                    val configFile = ConfigManager.prepareConfig(context, updated.file)
                                    ClashCore.load(configFile)
                                }
                            }
                        }
                        AppNotice.show("覆写设置已保存")
                        onBack()
                    }
                ) {
                    Text("保存", color = IosBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            frosted = true
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .bounceOverscroll()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CupertinoGroupedSection(
                    header = "配置信息",
                    footer = "选中的覆写脚本将在底层核心加载时自动注入，动态重写该配置的分流规则与代理组。"
                ) {
                    CupertinoCell(
                        title = profile.name,
                        value = if (profile.url != null) "远程订阅" else "本地文件",
                        icon = Icons.Default.FolderZip,
                        iconBg = AppPalette.iconPurple,
                        showChevron = false
                    )
                    CupertinoDivider()
                    CupertinoCell(
                        title = "管理脚本中心",
                        subtitle = "导入、创建或编写脚本代码",
                        icon = Icons.Default.Code,
                        iconBg = AppPalette.iconIndigo,
                        showChevron = true,
                        onClick = onNavigateToScripts
                    )
                }
            }

            item {
                CupertinoGroupedSection(
                    header = "绑定覆写脚本"
                ) {
                    if (allScripts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "暂无可用脚本",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = LoonTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "请点击上方「管理脚本中心」创建或导入脚本",
                                    fontSize = 12.sp,
                                    color = LoonTextSecondary
                                )
                            }
                        }
                    } else {
                        allScripts.forEachIndexed { idx, script ->
                            val isChecked = selectedScriptIds.contains(script.id)
                            CupertinoCell(
                                title = script.name,
                                subtitle = script.type.uppercase(),
                                icon = Icons.Default.Javascript,
                                iconBg = if (isChecked) IosBlue else AppPalette.iconGray,
                                trailing = {
                                    CupertinoSwitch(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedScriptIds.add(script.id)
                                            else selectedScriptIds.remove(script.id)
                                        }
                                    )
                                },
                                onClick = {
                                    if (isChecked) selectedScriptIds.remove(script.id)
                                    else selectedScriptIds.add(script.id)
                                }
                            )
                            if (idx < allScripts.size - 1) {
                                CupertinoDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
