/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

object XgimiWatchdogPolicy {
    enum class Decision {
        START,
        RECONNECT,
    }

    data class Input(
        val enabled: Boolean,
        val desiredVpnEnabled: Boolean,
        val hasTunnel: Boolean,
        val tunnelUp: Boolean,
        val systemVpnUp: Boolean,
        val handshakeStale: Boolean,
        val vpnProbeHealthy: Boolean,
        val lastRestartElapsedMillis: Long,
        val nowElapsedMillis: Long,
        val reconnectCooldownMillis: Long,
    )

    fun decide(input: Input): Decision? {
        if (!input.enabled || !input.desiredVpnEnabled || !input.hasTunnel)
            return null
        if (input.lastRestartElapsedMillis > 0 && input.nowElapsedMillis - input.lastRestartElapsedMillis < input.reconnectCooldownMillis)
            return null
        if (!input.tunnelUp || !input.systemVpnUp)
            return Decision.START
        if (input.handshakeStale && !input.vpnProbeHealthy)
            return Decision.RECONNECT
        return null
    }
}
