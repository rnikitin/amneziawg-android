/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class XgimiWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ALLOWED_ACTIONS) {
            Log.d(TAG, "Ignoring ${intent.action}")
            return
        }
        Log.i(TAG, "Received ${intent.action}")
        XgimiWatchdogService.start(context, true)
    }

    companion object {
        const val ACTION_KICK = "org.amnezia.awg.action.XGIMI_WATCHDOG_KICK"
        private const val TAG = "AmneziaWG/XgimiWatchdogReceiver"
        private val ALLOWED_ACTIONS = setOf(
            ACTION_KICK,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT,
        )
    }
}
