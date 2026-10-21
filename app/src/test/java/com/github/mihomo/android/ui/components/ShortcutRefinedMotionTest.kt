package com.github.mihomo.android.ui.components

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.mihomo.android.ui.glass.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/** Production card + controllable Compose frame clock; not a physical GPU/tactile assessment. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutRefinedMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun responseHasSoftCentreRadialTiltCapAndDistinctStyles() {
        val a = shortcutLiveTouchTarget(ShortcutTouchVariant.GentleFloat, 0f, 0f)
        val c = shortcutLiveTouchTarget(ShortcutTouchVariant.BuoyantSpring, 0f, 0f)
        assertTrue(a.offsetYDp < 0f && c.offsetYDp < a.offsetYDp * 2f)
        assertTrue(c.scale > a.scale && a.scale > 1f)
        assertTrue(shortcutReleaseDamping(ShortcutTouchVariant.GentleFloat) > shortcutReleaseDamping(ShortcutTouchVariant.BuoyantSpring))
        for (point in listOf(Offset(-1f,0f),Offset(1f,0f),Offset(0f,-1f),Offset(0f,1f),Offset(1f,1f),Offset(-1f,-1f))) {
            val b = shortcutLiveTouchTarget(ShortcutTouchVariant.FingerTilt,point.x,point.y)
            assertEquals(1f,b.scale,0f); assertEquals(0f,b.offsetYDp,0f)
            assertTrue(hypot(b.rotationX,b.rotationY) <= 6.001f)
            // y-down rotation convention used by the original supplied experiment:
            // local Z = y*sin(rx)-x*sin(ry), negative at contact, positive opposite it.
            val contactZ = point.y * kotlin.math.sin(Math.toRadians(b.rotationX.toDouble())) -
                point.x * kotlin.math.sin(Math.toRadians(b.rotationY.toDouble()))
            assertTrue("contact side recedes, opposite side rises: $point",contactZ < 0)
        }
        val centre=shortcutLiveTouchTarget(ShortcutTouchVariant.FingerTilt,.02f,-.02f)
        assertEquals(0f,centre.rotationX,0f); assertEquals(0f,centre.rotationY,0f)
    }

    @Test fun actualGentleAndBuoyantPlanesLiftAndCReboundsPastRest() = liftAndRebound(false)
    @Test @Config(qualifiers = "w400dp-h800dp-night-mdpi")
    fun darkGentleAndBuoyantPlanesLiftAndCReboundsPastRest() = liftAndRebound(true)

    private fun liftAndRebound(dark: Boolean) {
        val variant=mutableStateOf(ShortcutTouchVariant.GentleFloat)
        var foreground: LayoutCoordinates?=null
        var clicks=0
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTouchVariant provides variant.value,
                LocalGlassTokens provides (if(dark) GlassPalettes.MIDNIGHT else GlassPalettes.CREAM).toTokens()) {
                val cardLens=rememberNavigationLens()
                val dockLens=rememberNavigationLens()
                NexusGlassBackground(
                    modifier=Modifier.fillMaxSize().recordNavigationScene(dockLens),
                    backdropRecorder=Modifier.recordNavigationScene(cardLens)
                ) {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                        LoonShortcutCard(Modifier.size(190.dp,118.dp),"Motion","live glass",icon={
                            Box(Modifier.requiredSize(170.dp,98.dp).onGloballyPositioned { foreground=it })
                        },lens=cardLens,onClick={clicks++})
                    }
                }
            }
        }
        compose.waitForIdle()
        val card=compose.onNodeWithTag("shortcut-card-Motion")
        fun point(x:Float,y:Float):Offset { val c=requireNotNull(foreground); return c.localToRoot(Offset(c.size.width*x,c.size.height*y)) }
        fun centre()=point(.5f,.5f)
        fun width()=(point(1f,.5f)-point(0f,.5f)).getDistance()
        var gentleLift=0f
        for(choice in listOf(ShortcutTouchVariant.GentleFloat,ShortcutTouchVariant.BuoyantSpring)) {
            compose.runOnIdle { variant.value=choice };compose.waitForIdle()
            val rest=centre();val restWidth=width();val hit=card.fetchSemanticsNode().boundsInRoot
            compose.mainClock.autoAdvance=false
            card.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(176);compose.waitForIdle()
            val held=centre();val heldWidth=width()
            assertEquals("fixed hit slot",hit,card.fetchSemanticsNode().boundsInRoot)
            assertTrue("actual plane rises: $choice ${held.y-rest.y}",held.y < rest.y-.8f)
            assertTrue("actual plane gently expands",heldWidth>restWidth+.4f)
            if(choice==ShortcutTouchVariant.GentleFloat) gentleLift=rest.y-held.y
            else assertTrue("C lift visibly exceeds A",rest.y-held.y>gentleLift*2f)
            capture("${if(dark)"dark" else "light"}-$choice-held")
            card.performTouchInput { up() }
            var maxBelow=0f
            val values=mutableListOf<Float>()
            repeat(24) {
                compose.mainClock.advanceTimeBy(16);compose.waitForIdle()
                val delta=centre().y-rest.y;values+=delta;maxBelow=maxOf(maxBelow,delta)
            }
            println("motion $choice release-dy=$values")
            if(choice==ShortcutTouchVariant.BuoyantSpring) {
                assertTrue("C has a real bounded rebound beyond rest ($maxBelow)",maxBelow>.15f)
                assertTrue("C doesn't launch or bounce wildly",maxBelow<1.5f)
                capture("${if(dark)"dark" else "light"}-$choice-return")
            } else assertTrue("A has no distracting release wobble",maxBelow<.15f)
            compose.mainClock.advanceTimeBy(800);compose.waitForIdle()
            assertTrue("returns fully to rest",(centre()-rest).getDistance()<.05f)
            assertEquals(restWidth,width(),.05f)
            compose.mainClock.autoAdvance=true;compose.waitForIdle()
        }
        compose.runOnIdle { assertEquals(2,clicks) }
    }

    @Test fun bApproachesEdgeSmoothlyAndCancelSettlesWithoutClick() {
        var foreground:LayoutCoordinates?=null;var clicks=0
        compose.setContent {
            CompositionLocalProvider(LocalShortcutTouchVariant provides ShortcutTouchVariant.FingerTilt) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    LoonShortcutCard(Modifier.size(190.dp,118.dp),"Edge","",icon={
                        Box(Modifier.requiredSize(170.dp,98.dp).onGloballyPositioned { foreground=it })
                    },onClick={clicks++})
                }
            }
        }
        compose.waitForIdle()
        fun width():Float { val c=requireNotNull(foreground);return (c.localToRoot(Offset(c.size.width.toFloat(),0f))-c.localToRoot(Offset.Zero)).getDistance() }
        val card=compose.onNodeWithTag("shortcut-card-Edge");val restWidth=width();val hit=card.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance=false
        card.performTouchInput { down(Offset(width*.9f,height*.5f)) }
        compose.mainClock.advanceTimeBy(32);compose.waitForIdle();val early=width()
        compose.mainClock.advanceTimeBy(160);compose.waitForIdle();val held=width()
        assertTrue("held edge tilts the real plane",held<restWidth-.1f)
        assertTrue("approach isn't a one-frame snap",early>held+.02f)
        assertEquals(hit,card.fetchSemanticsNode().boundsInRoot)
        capture("B-edge-held")
        card.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
        assertEquals("cancelled pose settles back to rest",restWidth,width(),.75f)
        compose.runOnIdle { assertEquals(0,clicks) }
        compose.mainClock.autoAdvance=true
        card.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1,clicks) }
    }

    private fun capture(name:String) {
        val v=compose.activity.window.decorView
        val image=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)
        var result=-1
        compose.runOnIdle { PixelCopy.request(compose.activity.window,image,{result=it},Handler(Looper.getMainLooper())) }
        compose.waitForIdle();assertEquals(PixelCopy.SUCCESS,result)
        val out=File("build/reports/draft-motion/$name.png");out.parentFile!!.mkdirs()
        out.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it)) };image.recycle()
    }
}
