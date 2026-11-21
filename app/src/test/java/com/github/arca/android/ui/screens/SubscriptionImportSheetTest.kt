package com.github.arca.android.ui.screens

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arca.android.data.ConfigManager
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.ThemeMode
import com.github.arca.android.data.subscription.*
import com.github.arca.android.ui.glass.ProvideGlassTheme
import com.github.arca.android.ui.theme.MihomoTheme
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicReference

/** UI interaction checks intentionally avoid PixelCopy/native screenshot capture. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubscriptionImportSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        initial: SourceDraft? = null,
        onPreview: suspend (Context, List<SourceDraft>) -> ImportPreview = SubscriptionManager::preview,
        onCommit: (suspend (String, ImportPreview, Boolean, Boolean) -> ProfileItem)? = null,
        onImported: (ProfileItem) -> Unit = {}
    ) {
        compose.setContent {
            MihomoTheme(ThemeMode.LIGHT) {
                ProvideGlassTheme {
                    Box(Modifier.fillMaxSize()) {
                        SubscriptionImportSheet(onDismiss = {}, onImported = onImported, initial = initial,
                            onPreview = onPreview, onCommit = onCommit)
                    }
                }
            }
        }
    }

    @Test fun emptyInputCannotStartInspection() {
        show()
        compose.onNodeWithText("添加配置").assertIsDisplayed()
        compose.onNodeWithText("支持订阅链接、远程配置文件 URL", substring = true).assertExists()
        compose.onNodeWithTag("profile-add-submit").assertIsNotEnabled()
        compose.onNodeWithTag("profile-add-local-file").assertIsDisplayed()
        compose.onNodeWithTag("profile-add-clipboard").assertIsDisplayed()
    }

    @Test fun completeRemotePreviewSuggestsANameAndSavesTheWholeConfigurationByDefault() {
        var draftName: String? = null
        var committedName: String? = null
        var keptComplete: Boolean? = null
        show(initial = SourceDraft("", "https://example.invalid/config.yaml"), onPreview = { _, drafts ->
            draftName = drafts.single().name
            completePreview()
        }, onCommit = { title, _, standalone, _ ->
            committedName = title; keptComplete = standalone
            ProfileItem("fixture", title, null, File("fixture.yaml"))
        })
        inspect()
        compose.onNodeWithTag("profile-add-name").performScrollTo().assertTextContains("Server Name")
        compose.onNodeWithTag("subscription-import-standalone").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("subscription-import-extract").assertIsNotSelected()
        compose.onNodeWithTag("profile-add-submit").performClick()
        compose.runOnIdle {
            assertEquals("", draftName)
            assertEquals("Server Name", committedName)
            assertEquals(true, keptComplete)
        }
    }

    @Test fun explicitNameWinsAndNodeExtractionRemainsAnExplicitChoice() {
        var committedName: String? = null
        var keptComplete: Boolean? = null
        show(initial = SourceDraft("", "https://example.invalid/config.yaml"),
            onPreview = { _, _ -> completePreview() }, onCommit = { title, _, standalone, _ ->
                committedName = title; keptComplete = standalone
                ProfileItem("fixture", title, null, File("fixture.yaml"))
            })
        compose.onNodeWithTag("profile-add-name").performScrollTo().performTextInput("User Name")
        inspect()
        compose.onNodeWithTag("profile-add-name").performScrollTo().assertTextContains("User Name")
        compose.onNodeWithTag("subscription-import-extract").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("profile-add-submit").performClick()
        compose.runOnIdle {
            assertEquals("User Name", committedName)
            assertEquals(false, keptComplete)
        }
    }

    @Test fun sourceAliasFieldIsAbsentAndRemoteNameComesFromThePreview() {
        var submittedSourceName: String? = null
        show(initial = SourceDraft("", "https://example.invalid/config.yaml"), onPreview = { _, drafts ->
            submittedSourceName = drafts.single().name
            completePreview()
        })
        compose.onNodeWithText("来源别名", substring = true).assertDoesNotExist()
        inspect()
        compose.onNodeWithTag("profile-add-name").assertTextContains("Server Name")
        compose.runOnIdle { assertEquals("", submittedSourceName) }
    }

    @Test fun localAndRemoteSourcesMustBeImportedSeparately() {
        val remote = completePreview().sources.single()
        val local = remote.copy(source = remote.source.copy(id = "local", name = "", url = null))
        show(initial = SourceDraft("", "local fixture", remote = false), onPreview = { _, _ ->
            ImportPreview(listOf(local, remote))
        })

        inspectLocal()
        compose.onNodeWithTag("subscription-import-content")
            .performScrollToNode(hasTestTag("subscription-import-error"))
        compose.onNodeWithTag("subscription-import-error").assertTextContains("分开导入", substring = true)
        compose.onNodeWithTag("profile-add-submit").assertIsNotEnabled()
    }

    @Test fun localImportOnlyChangesTheExistingDefaultProfile() {
        val context = compose.activity
        val default = ConfigManager.getOrCreateDefaultProfile(context)
        val beforeIds = ConfigManager.getProfiles(context, forceReload = true).map { it.id }.toSet()
        val beforeFiles = ConfigManager.getProfilesDir(context).listFiles().orEmpty().map { it.canonicalPath }.toSet()
        val node = ImportNode(1, name = "Local fixture", type = "ss", data = Json.parseToJsonElement(
            """{"type":"ss","server":"local.invalid","port":443,"cipher":"aes-128-gcm","password":"fixture"}""").jsonObject)
        val local = ImportPreview(listOf(SourceSnapshot(
            SubscriptionSource("local", "", url = null), "local fixture",
            ImportInspection(format = "mihomo", nodes = listOf(node)))))
        val imported = AtomicReference<ProfileItem?>()
        show(initial = SourceDraft("", "local fixture", remote = false),
            onPreview = { _, drafts ->
                assertEquals("", drafts.single().name)
                local
            },
            onImported = imported::set)

        compose.onNodeWithTag("profile-add-name").assertDoesNotExist()
        inspectLocal()
        compose.onNodeWithTag("profile-add-submit").assertTextContains("导入到默认配置").performClick()
        compose.waitUntil(10_000) { imported.get() != null }

        val result = imported.get()!!
        assertEquals(ConfigManager.DEFAULT_PROFILE_ID, result.id)
        assertEquals(default.file, result.file)
        assertEquals(beforeIds, ConfigManager.getProfiles(context, forceReload = true).map { it.id }.toSet())
        assertEquals(beforeFiles, ConfigManager.getProfilesDir(context).listFiles().orEmpty().map { it.canonicalPath }.toSet())
        assertTrue(result.file.readText().contains("server: local.invalid"))
        assertNull(SubscriptionStore(context).head(result.id))
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
    fun submitAndCancelAreVisibleOnAShortScreen() {
        show()
        compose.onNodeWithTag("profile-add-submit").assertIsDisplayed()
        compose.onNodeWithTag("profile-add-cancel").assertIsDisplayed()
    }

    private fun inspect() {
        compose.onNodeWithTag("profile-add-submit").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("subscription-import-standalone").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun inspectLocal() {
        compose.onNodeWithTag("profile-add-submit").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("可用节点").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun completePreview(): ImportPreview {
        val node = ImportNode(1, name = "Fixture", type = "ss", data = Json.parseToJsonElement(
            """{"name":"Fixture","type":"ss","server":"node.invalid","port":443,"cipher":"aes-128-gcm","password":"fixture"}""").jsonObject)
        return ImportPreview(listOf(SourceSnapshot(
            SubscriptionSource("remote", "Server Name", "https://example.invalid/config.yaml"), "fixture",
            ImportInspection(format = "mihomo", nodes = listOf(node), complete = "fixture", completeValid = true),
            suggestedName = "Server Name")))
    }

    @Test fun queuedSourceCanBeEditedWithoutLosingItsContents() {
        show()
        val fixture = "ss://YWVzLTEyOC1nY206Zml4dHVyZQ@node.invalid:443#Fixture"
        compose.onNodeWithTag("profile-add-url").performScrollTo().performTextInput(fixture)
        compose.onNodeWithTag("subscription-import-content")
            .performScrollToNode(hasText("再加一个来源", substring = true))
        compose.onNodeWithText("再加一个来源", substring = true).performClick()
        compose.onNodeWithContentDescription("编辑来源").performScrollTo().performClick()
        compose.onNodeWithTag("profile-add-url").performScrollTo().assertTextContains(fixture)
        compose.onNodeWithTag("profile-add-submit").assertIsEnabled()
    }
}
