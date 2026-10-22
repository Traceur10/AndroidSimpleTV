package com.traceur.androidsimpletv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

internal data class AppEntry(val component: ComponentName, val label: String, val icon: Drawable)

@Suppress("DEPRECATION")
internal fun loadTvApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        .filter { it.activityInfo.packageName != context.packageName && it.activityInfo.exported }
        .distinctBy { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        .mapNotNull { runCatching {
            AppEntry(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(pm).toString(), it.loadIcon(pm))
        }.getOrNull() }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
