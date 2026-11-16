package com.github.arca.android.ui.glass

import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.glass.rememberSharedGlassBackdrop
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class GlasmioBackdropSharingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun childAndDockBindingsReuseTheRootBackdropInstance() {
        var rootSource: LayerBackdrop? = null
        var childSource: LayerBackdrop? = null
        var dockSource: LayerBackdrop? = null

        compose.setContent {
            ProvideGlassTheme {
                val rootBackdrop = rememberLayerBackdrop()
                ProvideArcaGlasmioBackdrop(rootBackdrop) {
                    val providedBackdrop = LocalArcaGlasmioBackdrop.current as? LayerBackdrop
                    SideEffect { rootSource = providedBackdrop }
                    ProvideArcaGlasmioBackdrop(rootBackdrop) {
                        val childBackdrop = LocalArcaGlasmioBackdrop.current as? LayerBackdrop
                        SideEffect { childSource = childBackdrop }
                    }
                    ProvideArcaGlasmioBackdrop(rootBackdrop) {
                        val dockBackdrop = LocalArcaGlasmioBackdrop.current as? LayerBackdrop
                        SideEffect { dockSource = dockBackdrop }
                    }
                }
            }
        }
        compose.waitForIdle()

        assertNotNull(rootSource)
        assertSame("child uses the root backdrop", rootSource, childSource)
        assertSame("dock uses the root backdrop", rootSource, dockSource)
    }

    @Test
    fun combinedParentPageBackdropIsSharedWithoutPerChildSources() {
        var combined: Backdrop? = null
        var child: Backdrop? = null
        var dock: Backdrop? = null
        compose.setContent {
            ProvideGlassTheme {
                val wallpaper = rememberLayerBackdrop()
                val page = rememberLayerBackdrop()
                val parentPageBackdrop = rememberSharedGlassBackdrop(wallpaper, page)
                ProvideArcaGlasmioBackdrop(parentPageBackdrop) {
                    val providedBackdrop = LocalArcaGlasmioBackdrop.current
                    SideEffect { combined = providedBackdrop }
                    ProvideArcaGlasmioBackdrop(parentPageBackdrop) {
                        val childBackdrop = LocalArcaGlasmioBackdrop.current
                        SideEffect { child = childBackdrop }
                    }
                    ProvideArcaGlasmioBackdrop(parentPageBackdrop) {
                        val dockBackdrop = LocalArcaGlasmioBackdrop.current
                        SideEffect { dock = dockBackdrop }
                    }
                }
            }
        }
        compose.waitForIdle()

        assertNotNull(combined)
        assertSame("child screens reuse the page composite", combined, child)
        assertSame("dock consumers reuse the same page composite", combined, dock)
    }

    @Test
    fun inactiveSubscreenDoesNotAllocateAnAdditionalSource() {
        var source: LayerBackdrop? = null
        compose.setContent {
            ProvideGlassTheme {
                val candidate = rememberArcaGlasmioBackdrop(enabled = false)
                SideEffect { source = candidate }
            }
        }
        compose.waitForIdle()
        assertNull("main tab rendering needs only the root wallpaper source", source)
    }
}
