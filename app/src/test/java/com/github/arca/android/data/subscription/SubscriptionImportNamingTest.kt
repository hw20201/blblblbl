package com.github.arca.android.data.subscription

import org.junit.Assert.*
import org.junit.Test

class SubscriptionImportNamingTest {
    private fun snapshot(name: String = "", suggestedName: String = "", complete: Boolean = true) = SourceSnapshot(
        SubscriptionSource("fixture", name, "https://example.invalid/config.yaml"),
        "proxies: []", ImportInspection(completeValid = complete), fetchedAt = 123L, suggestedName = suggestedName)

    @Test fun inferredNameSurvivesSnapshotSerializationAndIsAvailableToPreview() {
        val original = snapshot(suggestedName = "Remote Configuration")
        val stored = subscriptionJson.encodeToString(SourceSnapshot.serializer(), original)
        val restored = subscriptionJson.decodeFromString<SourceSnapshot>(stored)
        assertEquals("Remote Configuration", restored.suggestedName)
        assertEquals("Remote Configuration", ImportPreview(listOf(restored)).suggestedName)
    }

    @Test fun oldSnapshotsWithoutSuggestedNameUseTheirExistingSourceLabel() {
        val old = """{"source":{"id":"fixture","name":"Existing Label","url":"https://example.invalid/config.yaml"},"text":"proxies: []","inspection":{},"fetchedAt":123}"""
        val restored = subscriptionJson.decodeFromString<SourceSnapshot>(old)
        assertEquals("", restored.suggestedName)
        assertEquals(123L, restored.attemptedAt)
        assertEquals("Existing Label", ImportPreview(listOf(restored)).suggestedName)
    }

    @Test fun assignedSourceLabelTakesPrecedenceOverAnOlderSuggestion() {
        val preview = ImportPreview(listOf(snapshot("User Alias", "Server Title")))
        assertEquals("User Alias", preview.suggestedName)
    }

    @Test fun onlyOneCompleteSourceOffersStandaloneImport() {
        val complete = snapshot("Remote")
        assertTrue(ImportPreview(listOf(complete)).supportsStandalone)
        assertFalse(ImportPreview(listOf(complete, complete.copy(source = complete.source.copy(id = "other")))).supportsStandalone)
        assertFalse(ImportPreview(listOf(snapshot(complete = false))).supportsStandalone)
        assertFalse(ImportPreview(emptyList()).supportsStandalone)
    }
}
