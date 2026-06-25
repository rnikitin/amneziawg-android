/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XgimiWatchdogPolicyTest {
    private fun input(
        enabled: Boolean = true,
        desiredVpnEnabled: Boolean = true,
        hasTunnel: Boolean = true,
        tunnelUp: Boolean = true,
        systemVpnUp: Boolean = true,
        handshakeStale: Boolean = false,
        vpnProbeHealthy: Boolean = true,
        lastRestartElapsedMillis: Long = 0,
        nowElapsedMillis: Long = 120_000,
        reconnectCooldownMillis: Long = 60_000,
    ) = XgimiWatchdogPolicy.Input(
        enabled = enabled,
        desiredVpnEnabled = desiredVpnEnabled,
        hasTunnel = hasTunnel,
        tunnelUp = tunnelUp,
        systemVpnUp = systemVpnUp,
        handshakeStale = handshakeStale,
        vpnProbeHealthy = vpnProbeHealthy,
        lastRestartElapsedMillis = lastRestartElapsedMillis,
        nowElapsedMillis = nowElapsedMillis,
        reconnectCooldownMillis = reconnectCooldownMillis,
    )

    @Test
    fun `disabled watchdog does nothing`() {
        assertNull(XgimiWatchdogPolicy.decide(input(enabled = false, tunnelUp = false, systemVpnUp = false)))
    }

    @Test
    fun `manual off does not restart missing vpn`() {
        assertNull(XgimiWatchdogPolicy.decide(input(desiredVpnEnabled = false, tunnelUp = false, systemVpnUp = false)))
    }

    @Test
    fun `missing selected tunnel does nothing`() {
        assertNull(XgimiWatchdogPolicy.decide(input(hasTunnel = false, tunnelUp = false, systemVpnUp = false)))
    }

    @Test
    fun `desired on starts tunnel when backend is down`() {
        assertEquals(XgimiWatchdogPolicy.Decision.START, XgimiWatchdogPolicy.decide(input(tunnelUp = false)))
    }

    @Test
    fun `desired on starts tunnel when system vpn is missing`() {
        assertEquals(XgimiWatchdogPolicy.Decision.START, XgimiWatchdogPolicy.decide(input(systemVpnUp = false)))
    }

    @Test
    fun `stale handshake does not reconnect when vpn probe is healthy`() {
        assertNull(XgimiWatchdogPolicy.decide(input(handshakeStale = true, vpnProbeHealthy = true)))
    }

    @Test
    fun `stale handshake reconnects when vpn probe is bad`() {
        assertEquals(
            XgimiWatchdogPolicy.Decision.RECONNECT,
            XgimiWatchdogPolicy.decide(input(handshakeStale = true, vpnProbeHealthy = false))
        )
    }

    @Test
    fun `cooldown blocks restart flapping`() {
        assertNull(
            XgimiWatchdogPolicy.decide(
                input(
                    tunnelUp = false,
                    lastRestartElapsedMillis = 90_000,
                    nowElapsedMillis = 120_000,
                    reconnectCooldownMillis = 60_000,
                )
            )
        )
    }
}
