package com.kitsune.core.background

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Creates a pending intent that opens the main launcher activity.
 * The generated [jobId] is passed as an extra so the app can deep-link to the review screen
 * when the user taps the notification.
 */
fun Context.openAppPendingIntent(jobId: String): PendingIntent {
    val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("generationJobId", jobId)
    } ?: Intent()

    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    return PendingIntent.getActivity(this, jobId.hashCode(), intent, flags)
}
