package com.github.mihomo.android.ui

import android.app.Notification
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.graphics.drawable.toBitmap
import com.github.mihomo.android.R
import com.github.mihomo.android.service.MihomoVpnService
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
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

    private fun hasCreamWaveColor(pixel: Int): Boolean =
        Color.red(pixel) > 220 && Color.green(pixel) > 205 &&
            Color.blue(pixel) > 180 && Color.red(pixel) - Color.blue(pixel) > 8

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
                    assertEquals(Color.rgb(7, 22, 41), color)
                    paths++
                }
            }
            assertEquals(1, paths)
        }
    }

    @Test
    @Config(sdk = [31])
    fun splashWaveLeavesSafeMarginForAndroidSplashScaling() {
        val bitmap = requireNotNull(
            RuntimeEnvironment.getApplication().getDrawable(R.drawable.ic_splash_logo)
        ).toBitmap()
        var minX = bitmap.width
        var maxX = -1
        var creamPixels = 0
        var bluePixels = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) > 16 && hasCreamWaveColor(pixel)) {
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
                creamPixels++
            }
            if (Color.alpha(pixel) > 16 && Color.blue(pixel) > 150 &&
                Color.blue(pixel) > Color.red(pixel) * 1.5f) bluePixels++
        }
        assertTrue("Splash must retain the cream-wave art", creamPixels > 100)
        assertEquals("Old blue N artwork must be absent", 0, bluePixels)
        assertTrue("Wave must keep a navy safety border", maxX - minX + 1 < bitmap.width * 0.55)
    }

    @Test
    @Config(sdk = [31])
    fun launcherAndSplashWaveEdgesRetainAntialiasCoverage() {
        val app = RuntimeEnvironment.getApplication()
        val foreground = requireNotNull(BitmapFactory.decodeResource(app.resources, R.mipmap.ic_launcher_foreground))
        fun hasPartialCreamCoverage(bitmap: android.graphics.Bitmap): Boolean {
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (alpha in 1..254 && hasCreamWaveColor(pixel)) return true
            }
            return false
        }
        fun hasAntialiasedCreamOnNavyEdge(bitmap: android.graphics.Bitmap): Boolean {
            val navy = floatArrayOf(7f, 22f, 41f)
            val cream = floatArrayOf(255f, 249f, 233f)
            var blendedEdgePixels = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) != 255) continue
                val rgb = floatArrayOf(Color.red(pixel).toFloat(), Color.green(pixel).toFloat(), Color.blue(pixel).toFloat())
                val delta = FloatArray(3) { cream[it] - navy[it] }
                val lengthSquared = delta.sumOf { (it * it).toDouble() }.toFloat()
                val projection = (0..2).sumOf { ((rgb[it] - navy[it]) * delta[it]).toDouble() }.toFloat() / lengthSquared
                if (projection !in 0.03f..0.97f) continue
                val residual = kotlin.math.sqrt((0..2).sumOf {
                    val difference = rgb[it] - navy[it] - projection * delta[it]
                    (difference * difference).toDouble()
                }).toFloat()
                if (residual < 18f) blendedEdgePixels++
            }
            return blendedEdgePixels > 20
        }
        assertTrue("Adaptive foreground must preserve antialiased wave edges", hasPartialCreamCoverage(foreground))
        val splash = requireNotNull(app.getDrawable(R.drawable.ic_splash_logo)).toBitmap()
        // Splash artwork is composited on an opaque navy tile; test blended RGB edge pixels,
        // not partial alpha (which can only exist at the tile's rounded outer corners).
        assertTrue("Splash artwork must preserve antialiased wave edges", hasAntialiasedCreamOnNavyEdge(splash))
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
    @Config(sdk = [28, 33])
    fun notificationUsesBrandStatusIconAndPreservesOngoingStopActionBehavior() {
        val service = Robolectric.buildService(MihomoVpnService::class.java).create().get()
        val notification = ReflectionHelpers.callInstanceMethod<Notification>(
            service,
            "buildNotification",
            ReflectionHelpers.ClassParameter.from(String::class.java, "Connected")
        )
        assertEquals(R.drawable.ic_zz_notification_nexus_v107, notification.smallIcon.resId)
        assertEquals(service.packageName, notification.smallIcon.resPackage)
        assertEquals("com.github.mihomo.android.draftglass", service.packageName)
        assertEquals(R.mipmap.ic_launcher, service.applicationInfo.icon)
        assertEquals("Arca", service.getString(R.string.app_name))
        assertEquals(service.getString(R.string.app_name), notification.extras.getString(Notification.EXTRA_TITLE))
        assertNotNull(notification.getLargeIcon())
        assertNotNull(notification.extras.get(Notification.EXTRA_LARGE_ICON))
        val cachedBitmap = ReflectionHelpers.callInstanceMethod<android.graphics.Bitmap>(
            service, "getNotificationLargeIcon"
        )
        val expected = requireNotNull(service.getDrawable(R.drawable.ic_notification_brand)).let {
            it.toBitmap(cachedBitmap.width, cachedBitmap.height)
        }
        assertTrue(cachedBitmap.sameAs(expected))
        assertNull(notification.extras.getString(Notification.EXTRA_TEMPLATE))
        val updatedNotification = ReflectionHelpers.callInstanceMethod<Notification>(
            service,
            "buildNotification",
            ReflectionHelpers.ClassParameter.from(String::class.java, "Updated traffic")
        )
        assertEquals(R.drawable.ic_zz_notification_nexus_v107, updatedNotification.smallIcon.resId)
        assertNotNull(updatedNotification.getLargeIcon())
        assertSame(cachedBitmap, ReflectionHelpers.callInstanceMethod<android.graphics.Bitmap>(
            service, "getNotificationLargeIcon"
        ))
        val launchIntent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(service.packageName, launchIntent.component?.packageName)
        assertEquals("com.github.mihomo.android.ui.MainActivity", launchIntent.component?.className)
        assertEquals(Color.rgb(56, 225, 232), notification.color)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(1, notification.actions.size)
        val stopIntent = shadowOf(notification.actions[0].actionIntent).savedIntent
        assertEquals(MihomoVpnService.ACTION_STOP, stopIntent.action)
        assertEquals(MihomoVpnService::class.java.name, stopIntent.component?.className)
    }

    @Test
    @Config(sdk = [28, 33])
    fun foregroundAndTrafficUpdatesUseDedicatedSmallIconAndLargeNArtwork() {
        val service = Robolectric.buildService(MihomoVpnService::class.java).create().get()
        // Keep the transparent white N artwork, but give notifications their own identity.
        assertNotEquals(R.drawable.ic_qs_tile, R.drawable.ic_zz_notification_nexus_v107)
        assertNotEquals(R.drawable.ic_stat_nexus, R.drawable.ic_zz_notification_nexus_v107)
        val resources = service.resources
        assertEquals("ic_zz_notification_nexus_v107",
            resources.getResourceEntryName(R.drawable.ic_zz_notification_nexus_v107))
        resources.getXml(R.drawable.ic_zz_notification_nexus_v107).use { tile ->
            resources.getXml(R.drawable.ic_stat_nexus).use { status ->
                fun paths(xml: org.xmlpull.v1.XmlPullParser): List<Pair<String?, String?>> {
                    val result = mutableListOf<Pair<String?, String?>>()
                    while (xml.next() != XmlPullParser.END_DOCUMENT) {
                        if (xml.eventType == XmlPullParser.START_TAG && xml.name == "path") {
                            result += xml.getAttributeValue(androidNs, "fillColor") to
                                xml.getAttributeValue(androidNs, "pathData")
                        }
                    }
                    return result
                }
                assertEquals(paths(status), paths(tile))
            }
        }
        ReflectionHelpers.callInstanceMethod<Unit>(
            service, "startForegroundServiceNotification",
            ReflectionHelpers.ClassParameter.from(String::class.java, "启动 Nexus 核心")
        )
        val startup = requireNotNull(shadowOf(service).lastForegroundNotification)
        assertEquals(R.drawable.ic_zz_notification_nexus_v107, startup.smallIcon.resId)
        assertNotNull(startup.getLargeIcon())
        assertNotNull(startup.extras.get(Notification.EXTRA_LARGE_ICON))
        assertEquals("启动 Nexus 核心", startup.extras.getString(Notification.EXTRA_TEXT))

        ReflectionHelpers.callInstanceMethod<Unit>(
            service, "updateNotification",
            ReflectionHelpers.ClassParameter.from(String::class.java, "↑ 1 KB/s   ↓ 2 KB/s")
        )
        val manager = service.getSystemService(android.app.NotificationManager::class.java)
        val refreshed = requireNotNull(shadowOf(manager).getNotification(1001))
        assertEquals(R.drawable.ic_zz_notification_nexus_v107, refreshed.smallIcon.resId)
        assertNotNull(refreshed.getLargeIcon())
        assertNotNull(refreshed.extras.get(Notification.EXTRA_LARGE_ICON))
        assertEquals("↑ 1 KB/s   ↓ 2 KB/s", refreshed.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(refreshed.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(refreshed.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(MihomoVpnService.ACTION_STOP,
            shadowOf(refreshed.actions.single().actionIntent).savedIntent.action)
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
