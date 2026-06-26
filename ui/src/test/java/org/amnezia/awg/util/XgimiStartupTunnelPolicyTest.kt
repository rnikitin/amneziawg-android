/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XgimiStartupTunnelPolicyTest {
    @Test
    fun `does not start a tunnel when any tunnel is already active`() {
        val tunnelName = XgimiStartupTunnelPolicy.chooseTunnelToStart(
            availableTunnelNames = setOf("home", "office"),
            activeTunnelNames = setOf("office"),
            desiredTunnelName = "home",
            lastUsedTunnelName = "home",
        )

        assertNull(tunnelName)
    }

    @Test
    fun `starts desired tunnel before last used tunnel`() {
        val tunnelName = XgimiStartupTunnelPolicy.chooseTunnelToStart(
            availableTunnelNames = setOf("home", "office"),
            activeTunnelNames = emptySet(),
            desiredTunnelName = "office",
            lastUsedTunnelName = "home",
        )

        assertEquals("office", tunnelName)
    }

    @Test
    fun `starts last used tunnel when no desired tunnel is stored`() {
        val tunnelName = XgimiStartupTunnelPolicy.chooseTunnelToStart(
            availableTunnelNames = setOf("home", "office"),
            activeTunnelNames = emptySet(),
            desiredTunnelName = null,
            lastUsedTunnelName = "home",
        )

        assertEquals("home", tunnelName)
    }

    @Test
    fun `does not start a tunnel that no longer exists`() {
        val tunnelName = XgimiStartupTunnelPolicy.chooseTunnelToStart(
            availableTunnelNames = setOf("office"),
            activeTunnelNames = emptySet(),
            desiredTunnelName = "missing",
            lastUsedTunnelName = "also-missing",
        )

        assertNull(tunnelName)
    }
}
