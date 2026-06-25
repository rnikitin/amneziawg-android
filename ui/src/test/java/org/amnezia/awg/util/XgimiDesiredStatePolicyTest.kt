/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

import org.amnezia.awg.backend.Tunnel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XgimiDesiredStatePolicyTest {
    @Test
    fun `user up records desired tunnel`() {
        val update = XgimiDesiredStatePolicy.fromUserResult("home", Tunnel.State.UP)
        assertEquals(true, update.enabled)
        assertEquals("home", update.tunnelName)
    }

    @Test
    fun `user down clears desired tunnel`() {
        val update = XgimiDesiredStatePolicy.fromUserResult("home", Tunnel.State.DOWN)
        assertEquals(false, update.enabled)
        assertNull(update.tunnelName)
    }

    @Test
    fun `toggle result up records desired tunnel`() {
        val update = XgimiDesiredStatePolicy.fromUserResult("office", Tunnel.State.UP)
        assertEquals(true, update.enabled)
        assertEquals("office", update.tunnelName)
    }
}
