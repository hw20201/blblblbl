package com.github.mihomo.android.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Shared semantic roles, not a font-scale override. All sizes follow system accessibility settings. */
object SettingsTypography {
    val RowTitle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val Description = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val Value = Description
    val SectionHeader = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val NavigationTitle = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
    val NavigationLabel = TextStyle(fontSize = 11.sp, lineHeight = 15.sp)
}
