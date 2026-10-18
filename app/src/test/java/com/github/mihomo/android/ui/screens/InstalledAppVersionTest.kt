package com.github.mihomo.android.ui.screens

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class InstalledAppVersionTest {
    @Test
    @Config(sdk = [28])
    fun `version display uses installed package name and code`() {
        val context = RuntimeEnvironment.getApplication()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        assertEquals("${packageInfo.versionName} (${packageInfo.versionCode})", getInstalledAppVersion(context))
        assertEquals(false, getInstalledAppVersion(context).contains("1.0.1-Beta.3"))
    }
}
