package com.github.arca.android.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsManagerSelectedNodeTest {
    @Test
    fun renameUpdatesOnlyExactMatchesInAffectedGroups() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mihomo_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = SettingsManager(context).apply {
            setSelectedNode("affected-one", "Edge")
            setSelectedNode("affected-two", "Edge")
            setSelectedNode("different-value", "Edge backup")
            setSelectedNode("unaffected", "Edge")
        }

        settings.renameSelectedNodes(
            listOf("affected-one", "affected-two", "different-value", "no-selection"),
            oldName = "Edge",
            newName = "New Edge"
        )

        val reloaded = SettingsManager(context)
        assertEquals("New Edge", reloaded.getSelectedNode("affected-one"))
        assertEquals("New Edge", reloaded.getSelectedNode("affected-two"))
        assertEquals("Edge backup", reloaded.getSelectedNode("different-value"))
        assertEquals("Edge", reloaded.getSelectedNode("unaffected"))
        assertNull(reloaded.getSelectedNode("no-selection"))
    }
}
