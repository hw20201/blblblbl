package com.github.mihomo.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.github.mihomo.android.data.SettingsManager
import com.github.mihomo.android.data.ThemeMode
import com.github.mihomo.android.ui.components.bounceOverscroll
import com.github.mihomo.android.ui.theme.*

@Composable
fun AppearanceScreen(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onLanguageChanged: (String) -> Unit,
    showGroupIcons: Boolean,
    onShowGroupIconsChanged: (Boolean) -> Unit,
    nodeColumns: Int,
    onNodeColumnsChanged: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val lang = LocalAppLanguage.current
    var currentLanguage by remember { mutableStateOf(settingsManager.appLanguage) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoonBg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = AppStrings.get("back", lang),
                    tint = LoonTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = AppStrings.get("settings_appearance", lang),
                color = LoonTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .bounceOverscroll()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Section 1: 主题模式
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = AppStrings.get("settings_theme", lang),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LoonTextSecondary
                )

                val themes = listOf(
                    ThemeMode.SYSTEM to AppStrings.get("theme_system", lang),
                    ThemeMode.LIGHT to AppStrings.get("theme_light", lang),
                    ThemeMode.DARK to AppStrings.get("theme_dark", lang)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = LoonCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LoonCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        themes.forEachIndexed { index, (mode, label) ->
                            val isSelected = themeMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onThemeModeChanged(mode) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) LoonBlue else LoonTextPrimary
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = LoonBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (index < themes.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = LoonCardBorder,
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: 界面语言
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = AppStrings.get("settings_language", lang),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LoonTextSecondary
                )

                val langs = listOf(
                    "SYSTEM" to AppStrings.get("theme_system", lang),
                    "zh" to "简体中文",
                    "en" to "English",
                    "ru" to "Русский"
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = LoonCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LoonCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        langs.forEachIndexed { index, (code, label) ->
                            val isSelected = currentLanguage == code
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentLanguage = code
                                        settingsManager.appLanguage = code
                                        onLanguageChanged(code)
                                        Toast.makeText(
                                            context,
                                            "${AppStrings.get("language_updated", code)}: $label",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        @Suppress("DEPRECATION")
                                        runCatching {
                                            val locale = if (code == "SYSTEM") java.util.Locale.getDefault() else java.util.Locale.forLanguageTag(code)
                                            java.util.Locale.setDefault(locale)
                                            val config = context.resources.configuration
                                            config.setLocale(locale)
                                            context.resources.updateConfiguration(config, context.resources.displayMetrics)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) LoonBlue else LoonTextPrimary
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = LoonBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            if (index < langs.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = LoonCardBorder,
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: 策略与节点排版
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "策略与节点排版",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LoonTextSecondary
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = LoonCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LoonCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "默认每行节点数",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = LoonTextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val cols = listOf(1 to "单列", 2 to "双列", 3 to "三列")
                            cols.forEach { (col, label) ->
                                val isSel = nodeColumns == col
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            1.dp,
                                            if (isSel) LoonBlue else LoonCardBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onNodeColumnsChanged(col) },
                                    color = if (isSel) LoonEditBlueBg else LoonCard
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) LoonBlue else LoonTextPrimary
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = LoonCardBorder,
                            thickness = 0.5.dp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "显示策略组远程图标",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = LoonTextPrimary
                            )
                            Switch(
                                checked = showGroupIcons,
                                onCheckedChange = onShowGroupIconsChanged
                            )
                        }
                    }
                }
            }
        }
    }
}
