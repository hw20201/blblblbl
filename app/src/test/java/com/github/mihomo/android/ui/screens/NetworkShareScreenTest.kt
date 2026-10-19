package com.github.mihomo.android.ui.screens

import com.github.mihomo.android.data.LanCredentials
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkShareScreenTest {
    @Test fun proxyUrlEncodesUsernameAndPasswordSeparately() {
        assertEquals("http://user%40name:p%3Aa%2Fss@192.168.1.2:7890",
            buildLanProxyUrl(LanCredentials("user@name", "p:a/ss"), "192.168.1.2", "7890"))
    }
}
