package com.github.mihomo.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.github.mihomo.android.ui.components.bounceOverscroll
import com.github.mihomo.android.ui.theme.*
import com.github.mihomo.android.ui.components.CupertinoNavBar
import com.github.mihomo.android.ui.components.CupertinoSegmentedControl
import com.github.mihomo.android.ui.components.rememberHapticAction
import com.github.mihomo.android.ui.components.hapticClickable

internal fun validRuleTypeSelection(selection: String, availableTypes: List<String>): String =
    if (selection == "ALL" || selection in availableTypes) selection else "ALL"

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
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterType by remember { mutableStateOf("ALL") }
    var selectedBehavior by remember { mutableStateOf("ALL") }

    val filterTypes = remember(rules) {
        val types = rules.map { it.type.uppercase() }.distinct().sorted()
        listOf("ALL") + types
    }
    LaunchedEffect(filterTypes) {
        selectedFilterType = validRuleTypeSelection(selectedFilterType, filterTypes)
    }

    val filteredRules = remember(rules, searchQuery, selectedFilterType, selectedBehavior) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoonBg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp)
            .navigationBarsPadding()
    ) {
        // Cupertino navigation bar; the rule-type filter and search remain available below.
        CupertinoNavBar(
            title = AppStrings.get("rules_screen_title", lang),
            subtitle = "${tunnelMode.displayName} · 共 $totalRules 条",
            showBackButton = true,
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(12.dp))

        CupertinoSegmentedControl(
            items = listOf("ALL", "PROXY", "DIRECT", "REJECT"),
            selectedItem = selectedBehavior,
            onItemSelected = { selectedBehavior = it },
            labelProvider = { behavior ->
                when (behavior) {
                    "ALL" -> "全部"
                    "PROXY" -> "代理"
                    "DIRECT" -> "直连"
                    "REJECT" -> "拦截"
                    else -> behavior
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("搜索规则、域名、目标策略...", fontSize = 13.sp, color = LoonTextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = LoonTextMuted, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = rememberHapticAction({ searchQuery = "" }), modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "清除", tint = LoonTextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LoonCard,
                unfocusedContainerColor = LoonCard,
                focusedBorderColor = LoonBlue,
                unfocusedBorderColor = LoonCardBorder,
                focusedTextColor = LoonTextPrimary,
                unfocusedTextColor = LoonTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Type Row
        if (filterTypes.size > 2) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filterTypes) { type ->
                    val isSelected = selectedFilterType == type
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) LoonEditBlueBg else LoonCard,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) LoonBlue else LoonCardBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .hapticClickable { selectedFilterType = type }
                    ) {
                        Text(
                            text = if (type == "ALL") "全部 (${rules.size})" else type,
                            color = if (isSelected) LoonBlue else LoonTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Rules List
        if (filteredRules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = LoonTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (rules.isEmpty()) "当前配置未配置任何规则\n所有流量将采用直接出站策略" else "未找到匹配的分流规则",
                        color = LoonTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .bounceOverscroll(allowTop = false, allowBottom = true),
            ) {
                itemsIndexed(filteredRules) { index, rule ->
                    val badgeColor = when {
                        rule.type.contains("DOMAIN", ignoreCase = true) -> LoonBlue
                        rule.type.contains("GEOIP", ignoreCase = true) -> Color(0xFFF59E0B)
                        rule.type.contains("IP-CIDR", ignoreCase = true) -> Color(0xFF8B5CF6)
                        rule.type.contains("RULE-SET", ignoreCase = true) -> Color(0xFF10B981)
                        else -> Color(0xFF6B7280)
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = LoonCard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .hapticClickable {
                                val textToCopy = if (rule.payload.isNotBlank()) "${rule.type},${rule.payload},${rule.target}" else "${rule.type},${rule.target}"
                                clipboardManager.setText(AnnotatedString(textToCopy))
                                Toast.makeText(context, "已复制规则: $textToCopy", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    fontSize = 11.sp,
                                    color = LoonTextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(36.dp)
                                )

                                Text(
                                    text = rule.type,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    modifier = Modifier.padding(end = 10.dp)
                                )

                                if (rule.payload.isNotBlank()) {
                                    Text(
                                        text = rule.payload,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = LoonTextPrimary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Text("MATCH ALL", fontSize = 12.sp, color = LoonTextMuted, modifier = Modifier.weight(1f))
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (rule.noResolve) {
                                    Text(
                                        text = "no-resolve",
                                        fontSize = 10.sp,
                                        color = LoonTextMuted,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }

                                Text(
                                    text = rule.target.ifBlank { "DIRECT" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (rule.target.uppercase()) {
                                        "DIRECT" -> Color(0xFF10B981)
                                        "REJECT" -> RedDanger
                                        else -> LoonBlue
                                    }
                                )
                            }
                        }
                    }
                    if (index < filteredRules.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(start = 14.dp),
                            color = LoonSeparator,
                            thickness = 0.5.dp
                        )
                    }
                    }
                }
            }
        }
    }
}
