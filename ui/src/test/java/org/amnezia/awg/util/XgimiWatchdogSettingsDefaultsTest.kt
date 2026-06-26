/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XgimiWatchdogSettingsDefaultsTest {
    @Test
    fun `defaults match balanced xgimi profile`() {
        assertEquals(true, XgimiWatchdogSettings.Defaults.enabled)
        assertEquals(false, XgimiWatchdogSettings.Defaults.desiredVpnEnabled)
        assertEquals("balanced", XgimiWatchdogSettings.Defaults.preset)
        assertEquals(30_000L, XgimiWatchdogSettings.Defaults.checkIntervalMillis)
        assertEquals(60_000L, XgimiWatchdogSettings.Defaults.healthyHandshakeIntervalMillis)
        assertEquals(60L, XgimiWatchdogSettings.Defaults.staleHandshakeSeconds)
        assertEquals(60_000L, XgimiWatchdogSettings.Defaults.reconnectCooldownMillis)
        assertEquals("1.1.1.1", XgimiWatchdogSettings.Defaults.probeHost)
        assertEquals(53, XgimiWatchdogSettings.Defaults.probePort)
        assertEquals(1_500, XgimiWatchdogSettings.Defaults.probeTimeoutMillis)
        assertEquals(32, XgimiWatchdogSettings.Defaults.maxStatusEvents)
    }

    @Test
    fun `snapshot exposes max status events setting`() {
        val snapshot = XgimiWatchdogSettings.Snapshot(
            enabled = XgimiWatchdogSettings.Defaults.enabled,
            desiredVpnEnabled = XgimiWatchdogSettings.Defaults.desiredVpnEnabled,
            desiredTunnelName = null,
            checkIntervalMillis = XgimiWatchdogSettings.Defaults.checkIntervalMillis,
            healthyHandshakeIntervalMillis = XgimiWatchdogSettings.Defaults.healthyHandshakeIntervalMillis,
            staleHandshakeSeconds = XgimiWatchdogSettings.Defaults.staleHandshakeSeconds,
            reconnectCooldownMillis = XgimiWatchdogSettings.Defaults.reconnectCooldownMillis,
            probeHost = XgimiWatchdogSettings.Defaults.probeHost,
            probePort = XgimiWatchdogSettings.Defaults.probePort,
            probeTimeoutMillis = XgimiWatchdogSettings.Defaults.probeTimeoutMillis,
            maxStatusEvents = XgimiWatchdogSettings.Defaults.maxStatusEvents,
            lastStatus = "",
            lastAction = "",
            lastError = "",
        )

        assertEquals(32, snapshot.maxStatusEvents)
    }

    @Test
    fun `snapshot from empty preferences maps defaults`() {
        val snapshot = XgimiWatchdogSettings.snapshotFrom(emptyPreferences())

        assertEquals(true, snapshot.enabled)
        assertEquals(false, snapshot.desiredVpnEnabled)
        assertNull(snapshot.desiredTunnelName)
        assertEquals(30_000L, snapshot.checkIntervalMillis)
        assertEquals(60_000L, snapshot.healthyHandshakeIntervalMillis)
        assertEquals(60L, snapshot.staleHandshakeSeconds)
        assertEquals(60_000L, snapshot.reconnectCooldownMillis)
        assertEquals("1.1.1.1", snapshot.probeHost)
        assertEquals(53, snapshot.probePort)
        assertEquals(1_500, snapshot.probeTimeoutMillis)
        assertEquals(32, snapshot.maxStatusEvents)
        assertEquals("", snapshot.lastStatus)
        assertEquals("", snapshot.lastAction)
        assertEquals("", snapshot.lastError)
    }

    @Test
    fun `snapshot from preferences maps stored values`() {
        val preferences = mutablePreferencesOf(
            booleanPreferencesKey("xgimi_watchdog_enabled") to false,
            booleanPreferencesKey("xgimi_watchdog_desired_vpn_enabled") to true,
            stringPreferencesKey("xgimi_watchdog_desired_tunnel_name") to "living-room",
            longPreferencesKey("xgimi_watchdog_check_interval_millis") to 12_000L,
            longPreferencesKey("xgimi_watchdog_healthy_handshake_interval_millis") to 65_000L,
            longPreferencesKey("xgimi_watchdog_stale_handshake_seconds") to 240L,
            longPreferencesKey("xgimi_watchdog_reconnect_cooldown_millis") to 90_000L,
            stringPreferencesKey("xgimi_watchdog_probe_host") to "9.9.9.9",
            intPreferencesKey("xgimi_watchdog_probe_port") to 853,
            intPreferencesKey("xgimi_watchdog_max_status_events") to 48,
            stringPreferencesKey("xgimi_watchdog_last_status") to "healthy",
            stringPreferencesKey("xgimi_watchdog_last_action") to "none",
            stringPreferencesKey("xgimi_watchdog_last_error") to "clear",
        )

        val snapshot = XgimiWatchdogSettings.snapshotFrom(preferences)

        assertEquals(false, snapshot.enabled)
        assertEquals(true, snapshot.desiredVpnEnabled)
        assertEquals("living-room", snapshot.desiredTunnelName)
        assertEquals(12_000L, snapshot.checkIntervalMillis)
        assertEquals(65_000L, snapshot.healthyHandshakeIntervalMillis)
        assertEquals(240L, snapshot.staleHandshakeSeconds)
        assertEquals(90_000L, snapshot.reconnectCooldownMillis)
        assertEquals("9.9.9.9", snapshot.probeHost)
        assertEquals(853, snapshot.probePort)
        assertEquals(1_500, snapshot.probeTimeoutMillis)
        assertEquals(48, snapshot.maxStatusEvents)
        assertEquals("healthy", snapshot.lastStatus)
        assertEquals("none", snapshot.lastAction)
        assertEquals("clear", snapshot.lastError)
    }

    @Test
    fun `snapshot maps aggressive preset`() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("xgimi_watchdog_preset") to "aggressive",
        )

        val snapshot = XgimiWatchdogSettings.snapshotFrom(preferences)

        assertEquals(5_000L, snapshot.checkIntervalMillis)
        assertEquals(15_000L, snapshot.healthyHandshakeIntervalMillis)
        assertEquals(15L, snapshot.staleHandshakeSeconds)
        assertEquals(15_000L, snapshot.reconnectCooldownMillis)
        assertEquals(1_000, snapshot.probeTimeoutMillis)
    }

    @Test
    fun `snapshot maps gentle preset`() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("xgimi_watchdog_preset") to "gentle",
        )

        val snapshot = XgimiWatchdogSettings.snapshotFrom(preferences)

        assertEquals(120_000L, snapshot.checkIntervalMillis)
        assertEquals(300_000L, snapshot.healthyHandshakeIntervalMillis)
        assertEquals(300L, snapshot.staleHandshakeSeconds)
        assertEquals(300_000L, snapshot.reconnectCooldownMillis)
        assertEquals(2_000, snapshot.probeTimeoutMillis)
    }

    @Test
    fun `snapshot ignores stale tv integer tuning preferences`() {
        val preferences = mutablePreferencesOf(
            intPreferencesKey("xgimi_watchdog_check_interval_seconds") to 5,
            intPreferencesKey("xgimi_watchdog_stale_handshake_seconds_int") to 600,
            intPreferencesKey("xgimi_watchdog_reconnect_cooldown_seconds") to 300,
            intPreferencesKey("xgimi_watchdog_probe_timeout_millis") to 5_000,
        )

        val snapshot = XgimiWatchdogSettings.snapshotFrom(preferences)

        assertEquals(30_000L, snapshot.checkIntervalMillis)
        assertEquals(60_000L, snapshot.healthyHandshakeIntervalMillis)
        assertEquals(60L, snapshot.staleHandshakeSeconds)
        assertEquals(60_000L, snapshot.reconnectCooldownMillis)
        assertEquals(1_500, snapshot.probeTimeoutMillis)
    }

    @Test
    fun `desired state clears stale tunnel when enabled without tunnel`() {
        val desiredTunnelName = stringPreferencesKey("xgimi_watchdog_desired_tunnel_name")
        val preferences = mutablePreferencesOf(desiredTunnelName to "stale")

        XgimiWatchdogSettings.applyDesiredState(preferences, enabled = true, tunnelName = null)

        assertNull(preferences[desiredTunnelName])
    }

    @Test
    fun `desired state clears stale tunnel when disabled`() {
        val desiredVpnEnabled = booleanPreferencesKey("xgimi_watchdog_desired_vpn_enabled")
        val desiredTunnelName = stringPreferencesKey("xgimi_watchdog_desired_tunnel_name")
        val preferences = mutablePreferencesOf(
            desiredVpnEnabled to true,
            desiredTunnelName to "stale",
        )

        XgimiWatchdogSettings.applyDesiredState(preferences, enabled = false, tunnelName = "some-tunnel")

        assertEquals(false, preferences[desiredVpnEnabled])
        assertNull(preferences[desiredTunnelName])
    }

    @Test
    fun `status fields are capped at two hundred characters`() {
        val lastStatus = stringPreferencesKey("xgimi_watchdog_last_status")
        val lastAction = stringPreferencesKey("xgimi_watchdog_last_action")
        val lastError = stringPreferencesKey("xgimi_watchdog_last_error")
        val preferences = mutablePreferencesOf()
        val longValue = "x".repeat(201)

        XgimiWatchdogSettings.applyStatus(preferences, status = longValue, action = longValue, error = longValue)

        assertEquals("x".repeat(200), preferences[lastStatus])
        assertEquals("x".repeat(200), preferences[lastAction])
        assertEquals("x".repeat(200), preferences[lastError])
    }
}
