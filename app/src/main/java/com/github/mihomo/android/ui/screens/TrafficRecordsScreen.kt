package com.github.mihomo.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.kr328.clash.core.util.trafficString
import com.github.mihomo.android.data.TrafficBytes
import com.github.mihomo.android.data.TrafficRecordRepository
import com.github.mihomo.android.ui.components.CupertinoNavBar
import com.github.mihomo.android.ui.glass.GlassDefaults
import com.github.mihomo.android.ui.glass.GlassSurface
import com.github.mihomo.android.ui.theme.AppStrings
import com.github.mihomo.android.ui.theme.LocalAppLanguage
import com.github.mihomo.android.ui.theme.LoonBg
import com.github.mihomo.android.ui.theme.LoonTextMuted
import com.github.mihomo.android.ui.theme.LoonTextPrimary
import com.github.mihomo.android.ui.theme.LoonTextSecondary
import com.github.mihomo.android.ui.theme.IosBlue
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TrafficRecordsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    LaunchedEffect(Unit) {
        TrafficRecordRepository.initialize(context)
        while (true) {
            TrafficRecordRepository.refresh(LocalDate.now(ZoneId.systemDefault()))
            delay(60_000)
        }
    }
    val totals by TrafficRecordRepository.totals.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize()
            .background(if (GlassDefaults.enabled) Color.Transparent else LoonBg)
            .statusBarsPadding()
    ) {
        CupertinoNavBar(
            title = AppStrings.get("traffic_records_title", lang),
            showBackButton = true,
            onBack = onBack,
            frosted = true
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
            TrafficPeriodCard(AppStrings.get("traffic_today", lang), totals.today, lang)
            Spacer(Modifier.height(10.dp))
            TrafficPeriodCard(AppStrings.get("traffic_week", lang), totals.week, lang)
            Spacer(Modifier.height(10.dp))
            TrafficPeriodCard(AppStrings.get("traffic_month", lang), totals.month, lang)
            Spacer(Modifier.height(10.dp))
            TrafficPeriodCard(AppStrings.get("traffic_lifetime", lang), totals.lifetime, lang)
            Spacer(Modifier.height(14.dp))
            Text(
                AppStrings.get("traffic_tracking_note", lang),
                color = LoonTextMuted, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun TrafficPeriodCard(title: String, bytes: TrafficBytes, lang: String) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, color = LoonTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TrafficValue("↑ ${AppStrings.get("traffic_up", lang)}", trafficString(bytes.upload))
                TrafficValue("↓ ${AppStrings.get("traffic_down", lang)}", trafficString(bytes.download))
                TrafficValue(AppStrings.get("traffic_total", lang), trafficString(bytes.total), accent = true)
            }
        }
    }
}

@Composable
private fun TrafficValue(label: String, value: String, accent: Boolean = false) {
    Column {
        Text(label, color = LoonTextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = if (accent) IosBlue else LoonTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
