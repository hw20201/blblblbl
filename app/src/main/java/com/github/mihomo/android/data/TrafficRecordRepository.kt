package com.github.mihomo.android.data

import android.content.Context
import android.content.SharedPreferences
import com.github.kr328.clash.core.model.Traffic
import com.github.kr328.clash.core.util.rawDownload
import com.github.kr328.clash.core.util.rawUpload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

/** Persistent byte accounting based only on Mihomo's monotonic session totals. */
object TrafficRecordRepository {
    private const val PREFS = "traffic_records_v1"
    private const val BASELINE = "baseline"
    private const val LAST_UP = "last_up"
    private const val LAST_DOWN = "last_down"
    private const val PENDING_UP = "pending_up"
    private const val PENDING_DOWN = "pending_down"
    private const val LIFE_UP = "life_up"
    private const val LIFE_DOWN = "life_down"
    private const val DAY_PREFIX = "day_"
    private val lock = Any()
    private var prefs: SharedPreferences? = null
    private val mutableTotals = MutableStateFlow(TrafficRecordTotals())
    val totals: StateFlow<TrafficRecordTotals> = mutableTotals.asStateFlow()

    fun initialize(context: Context) = synchronized(lock) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mutableTotals.value = summaryLocked(LocalDate.now(ZoneId.systemDefault()))
    }

    /** Call once per service poll with the packed native lifetime counters. */
    fun record(total: Traffic, nowMillis: Long = System.currentTimeMillis()) = synchronized(lock) {
        val p = prefs ?: return
        val up = total.rawUpload().coerceAtLeast(0L)
        val down = total.rawDownload().coerceAtLeast(0L)
        val e = p.edit()
        if (!p.getBoolean(BASELINE, false)) {
            // The native counters may contain traffic predating this feature. Start tracking now.
            e.putBoolean(BASELINE, true).putLong(LAST_UP, up).putLong(LAST_DOWN, down)
                .putLong(PENDING_UP, -1L).putLong(PENDING_DOWN, -1L).apply()
            mutableTotals.value = summaryLocked(LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault()))
            return
        }
        val (upDelta, nextUp, pendingUp) = accountCounter(p.getLong(LAST_UP, up), p.getLong(PENDING_UP, -1L), up)
        val (downDelta, nextDown, pendingDown) = accountCounter(p.getLong(LAST_DOWN, down), p.getLong(PENDING_DOWN, -1L), down)
        val date = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        val key = dayKey(date)
        val dayUp = saturatingAdd(p.getLong("${key}_up", 0L), upDelta)
        val dayDown = saturatingAdd(p.getLong("${key}_down", 0L), downDelta)
        e.putLong(LAST_UP, nextUp).putLong(LAST_DOWN, nextDown)
            .putLong(PENDING_UP, pendingUp).putLong(PENDING_DOWN, pendingDown)
            .putLong("${key}_up", dayUp).putLong("${key}_down", dayDown)
            .putLong(LIFE_UP, saturatingAdd(p.getLong(LIFE_UP, 0L), upDelta))
            .putLong(LIFE_DOWN, saturatingAdd(p.getLong(LIFE_DOWN, 0L), downDelta)).apply()
        mutableTotals.value = summaryLocked(date)
    }

    fun refresh(now: LocalDate = LocalDate.now(ZoneId.systemDefault())) = synchronized(lock) {
        if (prefs != null) mutableTotals.value = summaryLocked(now)
    }


    private fun summaryLocked(date: LocalDate): TrafficRecordTotals {
        val p = prefs ?: return TrafficRecordTotals()
        fun range(start: LocalDate, end: LocalDate): TrafficBytes {
            var up = 0L; var down = 0L; var day = start
            while (!day.isAfter(end)) {
                val key = dayKey(day)
                up = saturatingAdd(up, p.getLong("${key}_up", 0L))
                down = saturatingAdd(down, p.getLong("${key}_down", 0L))
                day = day.plusDays(1)
            }
            return TrafficBytes(up, down)
        }
        val (weekStart, monthStart) = trafficPeriodStarts(date)
        return TrafficRecordTotals(
            today = range(date, date),
            week = range(weekStart, date),
            month = range(monthStart, date),
            lifetime = TrafficBytes(p.getLong(LIFE_UP, 0L), p.getLong(LIFE_DOWN, 0L)),
            trackingStarted = p.getBoolean(BASELINE, false)
        )
    }

    private fun dayKey(date: LocalDate) = DAY_PREFIX + date.toString()
    private fun saturatingAdd(a: Long, b: Long): Long = if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
}


/** Return added bytes plus the safe next baseline for one monotonic native counter. */
internal data class CounterAccounting(val delta: Long, val last: Long, val pendingReset: Long)

/**
 * Ignore a single lower sample as a possible bad read. A second lower sample confirms a core reset;
 * count the current post-reset value once, then continue from it without subtracting negative bytes.
 */
internal fun accountCounter(last: Long, pendingReset: Long, current: Long): CounterAccounting {
    if (current >= last) return CounterAccounting(current - last, current, -1L)
    if (pendingReset < 0L) return CounterAccounting(0L, last, current)
    if (current >= pendingReset) return CounterAccounting(current, current, -1L)
    return CounterAccounting(0L, last, current)
}

internal fun trafficPeriodStarts(date: LocalDate): Pair<LocalDate, LocalDate> =
    date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) to date.withDayOfMonth(1)

data class TrafficBytes(val upload: Long = 0L, val download: Long = 0L) {
    val total: Long get() = if (Long.MAX_VALUE - upload < download) Long.MAX_VALUE else upload + download
}

data class TrafficRecordTotals(
    val today: TrafficBytes = TrafficBytes(),
    val week: TrafficBytes = TrafficBytes(),
    val month: TrafficBytes = TrafficBytes(),
    val lifetime: TrafficBytes = TrafficBytes(),
    val trackingStarted: Boolean = false
)
