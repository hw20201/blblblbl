package com.github.mihomo.android.ui

/** Share the same parent for toolbar, edge-swipe and Android back navigation. */
internal fun configurationParent(
    screen: SubScreen?,
    fromConfigurationCenter: Boolean,
    scriptFromOverride: Boolean
): SubScreen? = when (screen) {
    SubScreen.SCRIPTS -> if (scriptFromOverride) SubScreen.PROFILE_OVERRIDE
        else if (fromConfigurationCenter) SubScreen.CONFIGURATION_CENTER else null
    SubScreen.PROFILE_OVERRIDE -> SubScreen.PROFILES
    SubScreen.PROFILES, SubScreen.RULES, SubScreen.APP_ROUTING ->
        if (fromConfigurationCenter) SubScreen.CONFIGURATION_CENTER else null
    else -> null
}

internal fun configurationDepth(
    screen: SubScreen?,
    fromConfigurationCenter: Boolean,
    scriptFromOverride: Boolean
): Int {
    val childDepth = if (fromConfigurationCenter) 2 else 1
    return when (screen) {
        null -> 0
        SubScreen.CONFIGURATION_CENTER -> 1
        SubScreen.PROFILES, SubScreen.RULES, SubScreen.APP_ROUTING -> childDepth
        SubScreen.PROFILE_OVERRIDE -> childDepth + 1
        SubScreen.SCRIPTS -> if (scriptFromOverride) childDepth + 2 else childDepth
        else -> 1
    }
}
