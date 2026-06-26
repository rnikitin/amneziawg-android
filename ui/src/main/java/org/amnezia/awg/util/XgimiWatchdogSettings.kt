/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.amnezia.awg.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object XgimiWatchdogSettings {
    object Presets {
        const val AGGRESSIVE = "aggressive"
        const val BALANCED = "balanced"
        const val GENTLE = "gentle"
    }

    object Defaults {
        const val enabled = true
        const val desiredVpnEnabled = false
        const val preset = Presets.BALANCED
        const val checkIntervalMillis = 30_000L
        const val healthyHandshakeIntervalMillis = 60_000L
        const val staleHandshakeSeconds = 60L
        const val reconnectCooldownMillis = 60_000L
        const val probeHost = "1.1.1.1"
        const val probePort = 53
        const val probeTimeoutMillis = 1_500
        const val maxStatusEvents = 32
    }

    private data class TimingProfile(
        val checkIntervalMillis: Long,
        val healthyHandshakeIntervalMillis: Long,
        val staleHandshakeSeconds: Long,
        val reconnectCooldownMillis: Long,
        val probeTimeoutMillis: Int,
    )

    data class Snapshot(
        val enabled: Boolean,
        val desiredVpnEnabled: Boolean,
        val desiredTunnelName: String?,
        val checkIntervalMillis: Long,
        val healthyHandshakeIntervalMillis: Long,
        val staleHandshakeSeconds: Long,
        val reconnectCooldownMillis: Long,
        val probeHost: String,
        val probePort: Int,
        val probeTimeoutMillis: Int,
        val maxStatusEvents: Int,
        val lastStatus: String,
        val lastAction: String,
        val lastError: String,
    )

    private val ENABLED = booleanPreferencesKey("xgimi_watchdog_enabled")
    private val DESIRED_VPN_ENABLED = booleanPreferencesKey("xgimi_watchdog_desired_vpn_enabled")
    private val DESIRED_TUNNEL_NAME = stringPreferencesKey("xgimi_watchdog_desired_tunnel_name")
    private val PRESET = stringPreferencesKey("xgimi_watchdog_preset")
    private val CHECK_INTERVAL_MILLIS = longPreferencesKey("xgimi_watchdog_check_interval_millis")
    private val HEALTHY_HANDSHAKE_INTERVAL_MILLIS = longPreferencesKey("xgimi_watchdog_healthy_handshake_interval_millis")
    private val STALE_HANDSHAKE_SECONDS = longPreferencesKey("xgimi_watchdog_stale_handshake_seconds")
    private val RECONNECT_COOLDOWN_MILLIS = longPreferencesKey("xgimi_watchdog_reconnect_cooldown_millis")
    private val PROBE_HOST = stringPreferencesKey("xgimi_watchdog_probe_host")
    private val PROBE_PORT = intPreferencesKey("xgimi_watchdog_probe_port")
    private val MAX_STATUS_EVENTS = intPreferencesKey("xgimi_watchdog_max_status_events")
    private val LAST_STATUS = stringPreferencesKey("xgimi_watchdog_last_status")
    private val LAST_ACTION = stringPreferencesKey("xgimi_watchdog_last_action")
    private val LAST_ERROR = stringPreferencesKey("xgimi_watchdog_last_error")

    val desiredVpnEnabled
        get() = Application.getPreferencesDataStore().data.map {
            it[DESIRED_VPN_ENABLED] ?: Defaults.desiredVpnEnabled
        }

    internal fun applyDesiredState(preferences: MutablePreferences, enabled: Boolean, tunnelName: String?) {
        preferences[DESIRED_VPN_ENABLED] = enabled
        if (enabled && tunnelName != null)
            preferences[DESIRED_TUNNEL_NAME] = tunnelName
        else
            preferences.remove(DESIRED_TUNNEL_NAME)
    }

    internal fun applyStatus(preferences: MutablePreferences, status: String, action: String = "", error: String = "") {
        preferences[LAST_STATUS] = status.take(200)
        preferences[LAST_ACTION] = action.take(200)
        preferences[LAST_ERROR] = error.take(200)
    }

    internal fun snapshotFrom(preferences: Preferences): Snapshot {
        val profile = profileFor(preferences[PRESET])
        return Snapshot(
            enabled = preferences[ENABLED] ?: Defaults.enabled,
            desiredVpnEnabled = preferences[DESIRED_VPN_ENABLED] ?: Defaults.desiredVpnEnabled,
            desiredTunnelName = preferences[DESIRED_TUNNEL_NAME],
            checkIntervalMillis = preferences[CHECK_INTERVAL_MILLIS] ?: profile.checkIntervalMillis,
            healthyHandshakeIntervalMillis = preferences[HEALTHY_HANDSHAKE_INTERVAL_MILLIS]
                ?: profile.healthyHandshakeIntervalMillis,
            staleHandshakeSeconds = preferences[STALE_HANDSHAKE_SECONDS] ?: profile.staleHandshakeSeconds,
            reconnectCooldownMillis = preferences[RECONNECT_COOLDOWN_MILLIS] ?: profile.reconnectCooldownMillis,
            probeHost = preferences[PROBE_HOST] ?: Defaults.probeHost,
            probePort = preferences[PROBE_PORT] ?: Defaults.probePort,
            probeTimeoutMillis = profile.probeTimeoutMillis,
            maxStatusEvents = preferences[MAX_STATUS_EVENTS] ?: Defaults.maxStatusEvents,
            lastStatus = preferences[LAST_STATUS] ?: "",
            lastAction = preferences[LAST_ACTION] ?: "",
            lastError = preferences[LAST_ERROR] ?: "",
        )
    }

    private fun profileFor(preset: String?): TimingProfile = when (preset ?: Defaults.preset) {
        Presets.AGGRESSIVE -> TimingProfile(
            checkIntervalMillis = 5_000L,
            healthyHandshakeIntervalMillis = 15_000L,
            staleHandshakeSeconds = 15L,
            reconnectCooldownMillis = 15_000L,
            probeTimeoutMillis = 1_000,
        )
        Presets.GENTLE -> TimingProfile(
            checkIntervalMillis = 120_000L,
            healthyHandshakeIntervalMillis = 300_000L,
            staleHandshakeSeconds = 300L,
            reconnectCooldownMillis = 300_000L,
            probeTimeoutMillis = 2_000,
        )
        else -> TimingProfile(
            checkIntervalMillis = Defaults.checkIntervalMillis,
            healthyHandshakeIntervalMillis = Defaults.healthyHandshakeIntervalMillis,
            staleHandshakeSeconds = Defaults.staleHandshakeSeconds,
            reconnectCooldownMillis = Defaults.reconnectCooldownMillis,
            probeTimeoutMillis = Defaults.probeTimeoutMillis,
        )
    }

    suspend fun snapshot(): Snapshot {
        return snapshotFrom(Application.getPreferencesDataStore().data.first())
    }

    suspend fun setDesiredState(enabled: Boolean, tunnelName: String?) {
        Application.getPreferencesDataStore().edit {
            applyDesiredState(it, enabled, tunnelName)
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        Application.getPreferencesDataStore().edit {
            it[ENABLED] = enabled
        }
    }

    suspend fun setStatus(status: String, action: String = "", error: String = "") {
        Application.getPreferencesDataStore().edit {
            applyStatus(it, status, action, error)
        }
    }
}
