package com.chobgroup.vlesshub.ui

import com.chobgroup.vlesshub.data.model.VpnServer

fun applyLinkSort(servers: List<VpnServer>, byPing: Boolean): List<VpnServer> {
    val unique = servers.distinctBy { it.rawConfig }
    if (!byPing) return unique
    return unique.sortedWith(
        compareBy<VpnServer> { it.pingMs == null || it.pingMs == -1 }
            .thenBy { it.pingMs ?: Int.MAX_VALUE },
    )
}
