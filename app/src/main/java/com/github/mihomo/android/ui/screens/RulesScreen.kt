package com.github.mihomo.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.mihomo.android.core.model.TunnelMode
import com.github.mihomo.android.data.ParsedRule
import com.github.mihomo.android.ui.components.*
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    rules: List<ParsedRule>,
    totalRules: Int,
    tunnelMode: TunnelMode,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lang = LocalAppLanguage.current
    val haptic = rememberIosHaptic()

    var searchQuery by remember { mutableStateOf("") }
    var selectedBehavior by remember { mutableStateOf("ALL") }
    var selectedFilterType by remember { mutableStateOf("ALL") }
    var inspectedRule by remember { mutableStateOf<Pair<Int, ParsedRule>?>(null) }

    val behaviorTabs = listOf("ALL", "PROXY", "DIRECT", "REJECT")
    val filterTypes = remember(rules) { listOf("ALL") + rules.map { it.type.uppercase() }.distinct().sorted() }
    LaunchedEffect(filterTypes) {
        selectedFilterType = validRuleTypeSelection(selectedFilterType, filterTypes)
    }

    val filteredRules = remember(rules, searchQuery, selectedBehavior, selectedFilterType) {
        rules.filter { rule ->
            val matchType = selectedFilterType == "ALL" || rule.type.equals(selectedFilterType, ignoreCase = true)
            val matchBehavior = when (selectedBehavior) {
                "DIRECT" -> rule.target.equals("DIRECT", ignoreCase = true)
                "REJECT" -> rule.target.contains("REJECT", ignoreCase = true)
                "PROXY" -> !rule.target.equals("DIRECT", ignoreCase = true) && !rule.target.contains("REJECT", ignoreCase = true)
                else -> true
            }
            val matchQuery = searchQuery.isBlank() ||
                rule.payload.contains(searchQuery, ignoreCase = true) ||
                rule.target.contains(searchQuery, ignoreCase = true) ||
                rule.type.contains(searchQuery, ignoreCase = true)
            matchType && matchBehavior && matchQuery
        }
    }

    // One edge-to-edge lazy surface owns the search, filters and rule rows. The title
    // row floats above it, so the same page scene continues behind the transparent toolbar
    // while every control scrolls naturally with the rule content.
    Box(Modifier.fillMaxSize().background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("rules-list").bounceOverscroll(),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 50.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "rules-search") {
                ShortcutSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "搜索规则类型、域名、目标策略...",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "rules-behavior-filter") {
                CupertinoSegmentedControl(
                    items = behaviorTabs,
                    selectedItem = selectedBehavior,
                    onItemSelected = { selectedBehavior = it },
                    labelProvider = { tab ->
                        when (tab) {
                            "ALL" -> "全部 (${rules.size})"
                            "PROXY" -> "代理"
                            "DIRECT" -> "直连"
                            "REJECT" -> "拦截"
                            else -> tab
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            if (filterTypes.size > 2) {
                item(key = "rules-type-filter") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filterTypes) { type ->
                            val selected = selectedFilterType == type
                            Surface(
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                                    haptic.selection()
                                    selectedFilterType = type
                                },
                                color = if (selected) LoonTextPrimary.copy(alpha = if (LocalLoonColors.current.isDark) .22f else .09f) else LoonCard.copy(alpha = .72f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = if (type == "ALL") "全部 (${rules.size})" else type,
                                    color = if (selected) LoonTextPrimary else LoonTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (filteredRules.isEmpty()) {
                item(key = "rules-empty") {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FilterList, contentDescription = null, tint = LoonTextMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (rules.isEmpty()) "当前配置未配置任何分流规则\n所有流量将采用默认出站策略" else "未找到符合条件的规则",
                                color = LoonTextSecondary, fontSize = 13.sp, lineHeight = 20.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(filteredRules, key = { index, rule -> "${rule.type}-$index-${rule.payload}" }) { index, rule ->
                    val startsTypeGroup = index == 0 || !filteredRules[index - 1].type.equals(rule.type, ignoreCase = true)
                    val targetColor = when {
                        rule.target.equals("DIRECT", ignoreCase = true) -> IosGreen
                        rule.target.contains("REJECT", ignoreCase = true) -> IosRed
                        else -> IosBlue
                    }
                    val typeColor = when {
                        rule.type.contains("DOMAIN", ignoreCase = true) -> IosBlue
                        rule.type.contains("GEOIP", ignoreCase = true) -> IosOrange
                        rule.type.contains("IP-CIDR", ignoreCase = true) -> Color(0xFF5856D6)
                        rule.type.contains("RULE-SET", ignoreCase = true) -> IosGreen
                        else -> LoonTextSecondary
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        if (startsTypeGroup) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 4.dp, top = if (index == 0) 4.dp else 8.dp, bottom = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(rule.type.uppercase(), color = typeColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
                                Spacer(Modifier.width(8.dp))
                                HorizontalDivider(Modifier.weight(1f), color = LoonCardBorder.copy(alpha = 0.6f), thickness = 0.5.dp)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp), color = LoonCard.copy(alpha = .72f),
                            border = androidx.compose.foundation.BorderStroke(0.75.dp, Color.White.copy(alpha = .7f)),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).iosPressable {
                                haptic.selection()
                                inspectedRule = Pair(index + 1, rule)
                            }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Text("#${index + 1}", fontSize = 11.sp, color = LoonTextMuted, fontFamily = FontFamily.Monospace, modifier = Modifier.width(34.dp))
                                    Text(rule.type, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = typeColor)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = rule.payload.ifBlank { "MATCH ALL" },
                                        fontSize = if (rule.payload.isBlank()) 12.sp else 13.5.sp,
                                        fontWeight = if (rule.payload.isBlank()) FontWeight.Normal else FontWeight.Medium,
                                        color = if (rule.payload.isBlank()) LoonTextMuted else LoonTextPrimary,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (rule.noResolve) Text("no-resolve", fontSize = 10.sp, color = LoonTextMuted, modifier = Modifier.padding(end = 8.dp))
                                    Surface(shape = CircleShape, color = targetColor.copy(alpha = 0.12f)) {
                                        Text(rule.target.ifBlank { "DIRECT" }, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                            color = targetColor, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).statusBarsPadding()) {
            CupertinoNavBar(
                title = AppStrings.get("rules_screen_title", lang),
                subtitle = "${tunnelMode.displayName} · 共 $totalRules 条规则",
                showBackButton = true,
                onBack = onBack,
                frosted = true
            )
        }
    }

    // =========================================================================
    // Rule Inspection Bottom Sheet (Flat iOS layout without nested boxes)
    // =========================================================================
    if (inspectedRule != null) {
        val (idx, rule) = inspectedRule!!
        val fullRuleText = if (rule.payload.isNotBlank()) {
            if (rule.noResolve) "${rule.type},${rule.payload},${rule.target},no-resolve"
            else "${rule.type},${rule.payload},${rule.target}"
        } else {
            "${rule.type},${rule.target}"
        }

        ModalBottomSheet(
            onDismissRequest = { inspectedRule = null },
            containerColor = LoonCard,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "规则明细 (#$idx)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LoonTextPrimary
                )
                Text(
                    text = "底层路由分流匹配链条",
                    fontSize = 12.sp,
                    color = LoonTextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                )

                // Flat detail rows with dividers
                val details = listOf(
                    "匹配模式" to rule.type,
                    "匹配载荷" to rule.payload.ifBlank { "MATCH (匹配全部)" },
                    "出站策略" to rule.target,
                    "跳过 DNS 解析" to if (rule.noResolve) "是 (no-resolve)" else "否"
                )

                details.forEachIndexed { i, (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 14.sp, color = LoonTextSecondary)
                        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LoonTextPrimary)
                    }
                    if (i < details.size - 1) {
                        HorizontalDivider(color = LoonCardBorder, thickness = 0.5.dp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("规则表达式", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LoonTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = fullRuleText,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = LoonTextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(LoonBg)
                        .padding(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(fullRuleText))
                            Toast.makeText(context, "已复制完整规则", Toast.LENGTH_SHORT).show()
                            inspectedRule = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("复制规则", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { inspectedRule = null },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, LoonCardBorder),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("关闭", color = LoonTextPrimary)
                    }
                }
            }
        }
    }
}

internal fun validRuleTypeSelection(selected: String, available: List<String>): String =
    selected.takeIf { it in available } ?: "ALL"
