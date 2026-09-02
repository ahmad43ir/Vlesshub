package com.chobgroup.admin_vlesshub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigActionsTest {
    @Test
    fun normalizesHttpsTelegramProxyLink() {
        assertEquals(
            "tg://proxy?server=example.com&port=443&secret=abcdef",
            ConfigActions.normalizeTelegramProxyLink(
                "https://t.me/proxy?server=example.com&port=443&secret=abcdef",
            ),
        )
    }

    @Test
    fun recognizesTelegramProxyLinks() {
        assertTrue(ConfigActions.isTelegramProxyLink("tg://proxy?server=example.com"))
        assertTrue(ConfigActions.isTelegramProxyLink("https://t.me/proxy?server=example.com"))
        assertFalse(ConfigActions.isTelegramProxyLink("vless://example.com"))
    }
}
