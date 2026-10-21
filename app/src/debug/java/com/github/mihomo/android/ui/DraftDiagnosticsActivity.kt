package com.github.mihomo.android.ui

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.github.mihomo.android.MihomoApplication
import java.io.File
import java.security.MessageDigest
import java.util.Date

/** Debug-only launch gate; no Compose, RuntimeShader, GPU or JNI required for this page. */
class DraftDiagnosticsActivity : Activity() {
    private val acknowledgements by lazy { getSharedPreferences("draft_diagnostics", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val javaCrash = runCatching {
            File(filesDir, MihomoApplication.CRASH_FILE_NAME).takeIf { it.isFile }
                ?.reader()?.use { reader ->
                    val chars = CharArray(131072)
                    val count = reader.read(chars)
                    if (count > 0) String(chars, 0, count) else ""
                }.orEmpty()
        }.getOrDefault("")
        val exit = if (Build.VERSION.SDK_INT >= 30) runCatching {
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                .getHistoricalProcessExitReasons(packageName, 0, 32)
                .firstOrNull { it.processName == packageName &&
                    (it.reason == ApplicationExitInfo.REASON_CRASH || it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) }
        }.getOrNull() else null
        val javaKey = MessageDigest.getInstance("SHA-256").digest(javaCrash.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val exitTime = exit?.timestamp ?: 0L
        val hasNewJava = javaCrash.isNotEmpty() && acknowledgements.getString("java", "") != javaKey
        val hasNewExit = exitTime > acknowledgements.getLong("exit", 0L)
        if (!hasNewJava && !hasNewExit) {
            launchDraft()
            return
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val report = buildString {
            append("Nexus Glass DRAFT candidate launch report\n")
            append("App: $packageName $version\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}\n")
            if (exit != null) {
                append("Last app crash: ${Date(exitTime)}; reason=${exit.reason}; status=${exit.status}\n")
                append("Description: ${exit.description.orEmpty()}\n")
            }
            append("\n")
            if (javaCrash.isNotEmpty()) append(javaCrash)
            else append("No saved Java exception. A native/system termination may not provide a Java stack.\n")
        }
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        page.addView(TextView(this).apply { text = "草稿包启动诊断"; textSize = 24f })
        page.addView(TextView(this).apply {
            text = "检测到上次启动异常。下面是这个草稿包的记录，不会自动上传。分享前请检查是否含有私密信息。原生崩溃可能只有退出信息，没有完整堆栈。"
            textSize = 15f
        })
        page.addView(ScrollView(this).apply {
            addView(TextView(this@DraftDiagnosticsActivity).apply {
                text = report; textSize = 12f; setTextIsSelectable(true)
                setPadding(0, pad, 0, pad)
            })
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(Button(this).apply {
            text = "分享诊断记录"
            setOnClickListener {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Nexus Glass DRAFT 启动诊断")
                    putExtra(Intent.EXTRA_TEXT, report)
                }, "选择分享位置"))
            }
        })
        page.addView(Button(this).apply {
            text = "复制诊断记录"
            setOnClickListener {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Nexus draft crash", report))
                Toast.makeText(this@DraftDiagnosticsActivity, "已复制", Toast.LENGTH_SHORT).show()
            }
        })
        page.addView(Button(this).apply {
            text = "保留记录并重试启动"
            setOnClickListener {
                acknowledgements.edit().putString("java", javaKey).putLong("exit", exitTime).commit()
                launchDraft()
            }
        })
        setContentView(page)
    }

    private fun launchDraft() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
