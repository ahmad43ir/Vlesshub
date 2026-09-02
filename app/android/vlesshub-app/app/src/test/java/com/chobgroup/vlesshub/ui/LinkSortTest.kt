package com.chobgroup.vlesshub.ui

import com.chobgroup.vlesshub.data.model.VpnServer
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkSortTest {

    private fun server(raw: String, ping: Int?) = VpnServer(
        name = "s-$raw",
        flag = "",
        country = "",
        rawConfig = raw,
        pingMs = ping,
    )

    private fun rawConfigs(servers: List<VpnServer>): List<String> = servers.map { it.rawConfig }

    @Test
    fun returnsDistinctOriginalOrderWhenNotSortingByPing() {
        val servers = listOf(server("a", 300), server("a", 900), server("b", 50), server("c", null))
        assertEquals(
            listOf("a", "b", "c"),
            rawConfigs(applyLinkSort(servers, byPing = false)),
        )
    }

    @Test
    fun sortsReachableFirstByAscendingPing() {
        val servers = listOf(
            server("slow", 500),
            server("fast", 30),
            server("mid", 150),
        )
        assertEquals(
            listOf("fast", "mid", "slow"),
            rawConfigs(applyLinkSort(servers, byPing = true)),
        )
    }

    @Test
    fun putsUntestedAndFailedLinksAfterReachableOnes() {
        val servers = listOf(
            server("untested", null),
            server("failed", -1),
            server("fast", 20),
            server("slow", 600),
        )
        assertEquals(
            listOf("fast", "slow", "failed", "untested"),
            rawConfigs(applyLinkSort(servers, byPing = true)),
        )
    }

    @Test
    fun deduplicatesByRawConfigKeepingFirstOccurrence() {
        val servers = listOf(
            server("a", 100),
            server("a", 50),
            server("b", 200),
            server("a", 10),
        )
        val sorted = applyLinkSort(servers, byPing = true)
        assertEquals(2, sorted.size)
        assertEquals(listOf("a", "b"), rawConfigs(sorted))
    }

    @Test
    fun keepsOriginalRelativeOrderForEqualPings() {
        val servers = listOf(
            server("first", 100),
            server("second", 100),
            server("third", null),
        )
        assertEquals(
            listOf("first", "second", "third"),
            rawConfigs(applyLinkSort(servers, byPing = true)),
        )
    }

    @Test
    fun sortsReachableLinksBeforeUntestedWhenMixed() {
        val servers = listOf(
            server("no-ping-yet", null),
            server("timeout", -1),
            server("slowest", 999),
            server("fastest", 5),
        )
        assertEquals(
            listOf("fastest", "slowest", "timeout", "no-ping-yet"),
            rawConfigs(applyLinkSort(servers, byPing = true)),
        )
    }
}
