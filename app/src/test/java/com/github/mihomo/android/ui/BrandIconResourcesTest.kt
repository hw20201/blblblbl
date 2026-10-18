package com.github.mihomo.android.ui

import android.app.Notification
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import com.github.mihomo.android.R
import com.github.mihomo.android.service.MihomoVpnService
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class BrandIconResourcesTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    @Test
    @Config(sdk = [28])
    fun launcherIconsLoadAsAdaptiveIconsWithDarkOpaqueBackgroundAndForeground() {
        assertAdaptiveIcons()
        RuntimeEnvironment.getApplication().resources.getXml(R.drawable.ic_launcher_background).use { xml ->
            var paths = 0
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "path") {
                    val color = xml.getAttributeIntValue(androidNs, "fillColor", 0)
                    assertEquals(255, Color.alpha(color))
                    assertEquals(Color.rgb(16, 24, 35), color)
                    paths++
                }
            }
            assertEquals(1, paths)
        }
    }

    @Test
    @Config(sdk = [33])
    fun launcherIconsIncludeMonochromeLayer() {
        assertAdaptiveIcons()
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val icon = RuntimeEnvironment.getApplication().getDrawable(id) as AdaptiveIconDrawable
            assertNotNull("Android 13 monochrome layer missing", icon.monochrome)
        }
    }

    @Test
    @Config(sdk = [28])
    fun statusIconIs24dpTransparentAndUsesOnlyOneWhiteNPath() {
        val app = RuntimeEnvironment.getApplication()
        val drawable = requireNotNull(app.getDrawable(R.drawable.ic_stat_nexus))
        val pixels = (24 * app.resources.displayMetrics.density).roundToInt()
        assertEquals(pixels, drawable.intrinsicWidth)
        assertEquals(pixels, drawable.intrinsicHeight)
        app.resources.getXml(R.drawable.ic_stat_nexus).use { xml ->
            var paths = 0
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "path") {
                    assertEquals(Color.WHITE, xml.getAttributeIntValue(androidNs, "fillColor", 0))
                    assertTrue(xml.getAttributeValue(androidNs, "pathData").startsWith("M3.1,18.2"))
                    paths++
                }
            }
            // A single white N path; there is no filled rectangle or colored background.
            assertEquals(1, paths)
        }
    }

    @Test
    @Config(sdk = [28])
    fun notificationUsesBrandStatusIconAndPreservesOngoingStopActionBehavior() {
        val service = Robolectric.buildService(MihomoVpnService::class.java).create().get()
        val notification = ReflectionHelpers.callInstanceMethod<Notification>(
            service,
            "buildNotification",
            ReflectionHelpers.ClassParameter.from(String::class.java, "Connected")
        )
        assertEquals(R.drawable.ic_stat_nexus, notification.smallIcon.resId)
        assertEquals(Color.rgb(56, 225, 232), notification.color)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(1, notification.actions.size)
        val stopIntent = shadowOf(notification.actions[0].actionIntent).savedIntent
        assertEquals(MihomoVpnService.ACTION_STOP, stopIntent.action)
        assertEquals(MihomoVpnService::class.java.name, stopIntent.component?.className)
    }

    private fun assertAdaptiveIcons() {
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val drawable = RuntimeEnvironment.getApplication().getDrawable(id)
            assertTrue(drawable is AdaptiveIconDrawable)
            val icon = drawable as AdaptiveIconDrawable
            assertNotNull(icon.foreground)
            assertNotNull(icon.background)
        }
    }
}
