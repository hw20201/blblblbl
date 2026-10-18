package com.github.mihomo.android.ui.screens

import android.content.Context

/** Returns the version metadata for the package that is actually installed. */
internal fun getInstalledAppVersion(context: Context): String {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName?.takeIf { it.isNotBlank() } ?: "未知"
    return "$versionName (${packageInfo.versionCode})"
}
