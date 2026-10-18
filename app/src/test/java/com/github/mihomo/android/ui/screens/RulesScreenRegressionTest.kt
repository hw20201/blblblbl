package com.github.mihomo.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class RulesScreenRegressionTest {
    @Test
    fun selectedTypeResetsWhenLiveRulesRemoveThatType() {
        assertEquals("ALL", validRuleTypeSelection("DOMAIN", listOf("ALL", "IP-CIDR")))
    }

    @Test
    fun selectedTypeSurvivesRefreshWhenItIsStillAvailable() {
        assertEquals("DOMAIN", validRuleTypeSelection("DOMAIN", listOf("ALL", "DOMAIN", "IP-CIDR")))
    }

    @Test
    fun allSelectionAlwaysRemainsValid() {
        assertEquals("ALL", validRuleTypeSelection("ALL", listOf("ALL", "DOMAIN")))
    }
}
