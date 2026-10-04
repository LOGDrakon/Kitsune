package com.kitsune.feature.chat.list

import android.content.Context
import com.kitsune.feature.chat.R

/** "il y a 3 h" for a timestamp, used by the conversation lists. */
fun formatRelativeTime(millis: Long, context: Context): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    return when {
        diff < 60_000 -> context.getString(R.string.time_just_now)
        diff < 3_600_000 -> context.getString(R.string.time_minutes_ago, diff / 60_000)
        diff < 86_400_000 -> context.getString(R.string.time_hours_ago, diff / 3_600_000)
        diff < 604_800_000 -> context.getString(R.string.time_days_ago, diff / 86_400_000)
        diff < 2_592_000_000 -> context.getString(R.string.time_weeks_ago, diff / 604_800_000)
        else -> context.getString(R.string.time_months_ago, diff / 2_592_000_000)
    }
}
