/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

object XgimiStartupTunnelPolicy {
    fun chooseTunnelToStart(
        availableTunnelNames: Set<String>,
        activeTunnelNames: Set<String>,
        desiredTunnelName: String?,
        lastUsedTunnelName: String?,
    ): String? {
        if (activeTunnelNames.isNotEmpty())
            return null
        if (desiredTunnelName != null && availableTunnelNames.contains(desiredTunnelName))
            return desiredTunnelName
        if (lastUsedTunnelName != null && availableTunnelNames.contains(lastUsedTunnelName))
            return lastUsedTunnelName
        return null
    }
}
