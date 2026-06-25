/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

import org.amnezia.awg.backend.Tunnel

object XgimiDesiredStatePolicy {
    data class Update(val enabled: Boolean, val tunnelName: String?)

    fun fromUserResult(tunnelName: String, resultingState: Tunnel.State): Update =
        if (resultingState == Tunnel.State.UP)
            Update(enabled = true, tunnelName = tunnelName)
        else
            Update(enabled = false, tunnelName = null)
}
