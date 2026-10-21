package com.github.mihomo.android.ui

import android.app.Application
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.github.mihomo.android.MihomoApplication
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DraftDiagnosticsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val crash get() = File(app.filesDir, MihomoApplication.CRASH_FILE_NAME)
    @Before fun clear() {
        crash.delete()
        app.getSharedPreferences("draft_diagnostics", 0).edit().clear().commit()
    }
    @After fun clean() { clear() }
    @Test fun appIconStartsMainDirectlyAndKeepsExplicitDiagnosticsShortcut() {
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
        assertNotNull(launch)
        assertEquals(MainActivity::class.java.name, launch!!.component?.className)
        val info = app.packageManager.getActivityInfo(launch.component!!,
            android.content.pm.PackageManager.GET_META_DATA)
        val resource = info.metaData.getInt("android.app.shortcuts")
        assertTrue(resource != 0)
        val parser = app.resources.getXml(resource)
        var diagnostic = false
        while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "intent") {
                diagnostic = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "targetClass") == DraftDiagnosticsActivity::class.java.name
            }
        }
        assertTrue("Long-press shortcut retains crash-report entry", diagnostic)
    }
    @Test fun freshInstallLaunchesOrdinaryAppWithoutInterruption() {
        val activity = Robolectric.buildActivity(DraftDiagnosticsActivity::class.java).setup().get()
        assertTrue(activity.isFinishing)
        assertEquals(MainActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }
    @Test fun javaCrashIsViewableAndShareOnlyHappensAfterTap() {
        crash.writeText("java.lang.IllegalStateException: diagnostic test marker")
        val activity = Robolectric.buildActivity(DraftDiagnosticsActivity::class.java).setup().get()
        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
        val page = activity.findViewById<android.widget.FrameLayout>(android.R.id.content).getChildAt(0) as LinearLayout
        val report = (page.getChildAt(2) as android.widget.ScrollView).getChildAt(0) as TextView
        assertTrue(report.text.contains("diagnostic test marker"))
        (page.getChildAt(3) as Button).performClick()
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val payload = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, payload.action)
        assertTrue(payload.getStringExtra(Intent.EXTRA_TEXT)!!.contains("diagnostic test marker"))
        (page.getChildAt(5) as Button).performClick()
        assertEquals(MainActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        assertTrue("Retry must not delete the crash evidence", crash.isFile)
        val again = Robolectric.buildActivity(DraftDiagnosticsActivity::class.java).setup().get()
        assertTrue(again.isFinishing)
    }
}
