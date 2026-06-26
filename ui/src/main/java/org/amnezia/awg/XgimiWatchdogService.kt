/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import org.amnezia.awg.activity.TvMainActivity
import org.amnezia.awg.backend.BackendException
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.util.XgimiNetworkProbe
import org.amnezia.awg.util.XgimiWatchdogPolicy
import org.amnezia.awg.util.XgimiWatchdogSettings
import org.amnezia.awg.util.applicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class XgimiWatchdogService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val probe by lazy { XgimiNetworkProbe(applicationContext) }
    private val checkMutex = Mutex()
    private var loopStarted = false
    private var rescheduleOnDestroy = true
    private var lastRestartElapsedMillis = 0L
    private var lastHandshakeCheckElapsedMillis = 0L
    private var lastKnownHandshakeEpochSeconds = -3L
    private var firstSuspiciousHandshakeElapsedMillis = 0L
    private var lastNotificationText = ""

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startForegroundSafely())
            return START_NOT_STICKY
        serviceScope.launch {
            val snapshot = XgimiWatchdogSettings.snapshot()
            if (!snapshot.enabled || !snapshot.desiredVpnEnabled) {
                stopIdle()
                return@launch
            }

            rescheduleOnDestroy = true
            scheduleAlarm()
            XgimiWatchdogJobService.schedule(this@XgimiWatchdogService)
            if (intent?.getBooleanExtra(EXTRA_CHECK_NOW, false) == true)
                checkOnce("intent", snapshot)
            if (!loopStarted) {
                loopStarted = true
                runLoop()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.w(TAG, "Service destroyed")
        serviceScope.cancel()
        if (rescheduleOnDestroy)
            scheduleAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafely(): Boolean {
        try {
            startForeground(NOTIFICATION_ID, buildNotification("watching VPN"))
            return true
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Unable to enter foreground; stopping watchdog", e)
            recordForegroundFailure(e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing foreground service permission/type; stopping watchdog", e)
            recordForegroundFailure(e)
        }
        rescheduleOnDestroy = false
        stopSelf()
        return false
    }

    private fun recordForegroundFailure(e: Throwable) {
        applicationScope.launch {
            XgimiWatchdogSettings.setStatus("watchdog foreground blocked", "foreground", e.javaClass.simpleName)
        }
    }

    private suspend fun runLoop() {
        while (serviceScope.isActive) {
            val snapshot = XgimiWatchdogSettings.snapshot()
            if (!snapshot.enabled || !snapshot.desiredVpnEnabled) {
                stopIdle()
                return
            }
            checkOnce("loop", snapshot)
            delay(snapshot.checkIntervalMillis.coerceAtLeast(MIN_CHECK_INTERVAL_MILLIS))
        }
    }

    private suspend fun stopIdle() {
        rescheduleOnDestroy = false
        cancelAlarm()
        XgimiWatchdogJobService.cancel(this)
        updateStatus("watchdog idle", "stop")
        stopSelf()
    }

    private suspend fun checkOnce(source: String, snapshot: XgimiWatchdogSettings.Snapshot) {
        checkMutex.withLock {
            checkOnceLocked(source, snapshot)
        }
    }

    private suspend fun checkOnceLocked(source: String, snapshot: XgimiWatchdogSettings.Snapshot) {
        val lock = acquireWakeLock()
        try {
            val manager = Application.getTunnelManager()
            val tunnels = manager.getTunnels()
            val tunnel = snapshot.desiredTunnelName?.let { tunnels[it] } ?: manager.lastUsedTunnel
            val backend = Application.getBackend()
            val tunnelUp = tunnel != null && withContext(Dispatchers.IO) {
                try {
                    backend.getState(tunnel) == Tunnel.State.UP
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "Unable to read tunnel state", e)
                    false
                }
            }
            val systemVpnUp = isSystemVpnUp()
            val nowElapsed = SystemClock.elapsedRealtime()
            val handshakeCheckDue = lastHandshakeCheckElapsedMillis == 0L ||
                nowElapsed - lastHandshakeCheckElapsedMillis >= snapshot.healthyHandshakeIntervalMillis
            val handshakeTunnel = if (tunnel != null &&
                tunnelUp &&
                systemVpnUp &&
                handshakeCheckDue
            ) {
                tunnel
            } else {
                null
            }
            if (handshakeTunnel != null) {
                lastKnownHandshakeEpochSeconds = withContext(Dispatchers.IO) {
                    try {
                        backend.getLastHandshake(handshakeTunnel)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.w(TAG, "Unable to read last handshake", e)
                        -2L
                    }
                }
                lastHandshakeCheckElapsedMillis = nowElapsed
                if (lastKnownHandshakeEpochSeconds > 0)
                    firstSuspiciousHandshakeElapsedMillis = 0L
                else if (firstSuspiciousHandshakeElapsedMillis == 0L)
                    firstSuspiciousHandshakeElapsedMillis = nowElapsed
            }
            val nowEpochSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
            val handshakeAgeSeconds = if (lastKnownHandshakeEpochSeconds > 0) nowEpochSeconds - lastKnownHandshakeEpochSeconds else null
            val handshakeHealth = handshakeHealth(handshakeAgeSeconds, nowElapsed, snapshot)
            val handshakeStale = handshakeHealth == HandshakeHealth.SUSPECT
            val probeResult = if (handshakeStale && tunnelUp && systemVpnUp) {
                probe.probeDefault(snapshot.probeHost, snapshot.probePort, snapshot.probeTimeoutMillis)
            } else {
                XgimiNetworkProbe.Result(true, "not needed")
            }
            val decision = XgimiWatchdogPolicy.decide(
                XgimiWatchdogPolicy.Input(
                    enabled = snapshot.enabled,
                    desiredVpnEnabled = snapshot.desiredVpnEnabled,
                    hasTunnel = tunnel != null,
                    tunnelUp = tunnelUp,
                    systemVpnUp = systemVpnUp,
                    handshakeStale = handshakeStale,
                    vpnProbeHealthy = probeResult.healthy,
                    lastRestartElapsedMillis = lastRestartElapsedMillis,
                    nowElapsedMillis = nowElapsed,
                    reconnectCooldownMillis = snapshot.reconnectCooldownMillis,
                )
            )

            when (decision) {
                XgimiWatchdogPolicy.Decision.START -> {
                    Log.w(TAG, "VPN missing, starting last tunnel ${tunnel?.name}")
                    if (tunnel != null) {
                        if (stopIfNoLongerDesired(tunnel.name))
                            return
                        manager.setTunnelStateFromWatchdog(tunnel, Tunnel.State.UP)
                        lastRestartElapsedMillis = nowElapsed
                        updateStatus("watchdog start ${tunnel.name}", "start")
                    }
                }
                XgimiWatchdogPolicy.Decision.RECONNECT -> {
                    Log.w(TAG, "VPN handshake stale, reconnecting tunnel ${tunnel?.name}")
                    if (tunnel != null) {
                        if (stopIfNoLongerDesired(tunnel.name))
                            return
                        manager.setTunnelStateFromWatchdog(tunnel, Tunnel.State.DOWN)
                        delay(750)
                        if (stopIfNoLongerDesired(tunnel.name))
                            return
                        manager.setTunnelStateFromWatchdog(tunnel, Tunnel.State.UP)
                        lastRestartElapsedMillis = nowElapsed
                        lastHandshakeCheckElapsedMillis = 0L
                        lastKnownHandshakeEpochSeconds = -3L
                        firstSuspiciousHandshakeElapsedMillis = 0L
                        updateStatus("watchdog reconnect ${tunnel.name} ${probeResult.detail}", "reconnect")
                    }
                }
                null -> {
                    val status = if (handshakeStale) {
                        "watchdog ok ${tunnel?.name ?: "none"} app=$tunnelUp vpn=$systemVpnUp hs=${handshakeHealth.label} probe=${probeResult.detail}"
                    } else {
                        "watchdog ok ${tunnel?.name ?: "none"} app=$tunnelUp vpn=$systemVpnUp hs=${handshakeHealth.label}"
                    }
                    updateStatus(status, source)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendException) {
            if (e.reason == BackendException.Reason.VPN_NOT_AUTHORIZED) {
                Log.w(TAG, "VPN authorization required; stopping watchdog recovery", e)
                stopBlocked("watchdog needs VPN authorization", "auth", e.reason.name)
            } else {
                Log.e(TAG, "Watchdog backend check failed", e)
                XgimiWatchdogSettings.setStatus("watchdog error", "error", e.javaClass.simpleName)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Watchdog check failed", e)
            XgimiWatchdogSettings.setStatus("watchdog error", "error", e.javaClass.simpleName)
        } finally {
            if (lock.isHeld)
                lock.release()
        }
    }

    private suspend fun stopBlocked(status: String, action: String, error: String) {
        rescheduleOnDestroy = false
        cancelAlarm()
        XgimiWatchdogJobService.cancel(this)
        XgimiWatchdogSettings.setStatus(status, action, error)
        stopSelf()
    }

    private suspend fun stopIfNoLongerDesired(tunnelName: String): Boolean {
        val latest = try {
            XgimiWatchdogSettings.snapshot()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Unable to confirm desired state before recovery", e)
            XgimiWatchdogSettings.setStatus("watchdog desired-state unavailable", "abort", e.javaClass.simpleName)
            stopIdle()
            return true
        }
        val stillDesired = latest.enabled &&
            latest.desiredVpnEnabled &&
            (latest.desiredTunnelName == null || latest.desiredTunnelName == tunnelName)
        if (stillDesired)
            return false
        Log.i(TAG, "Desired VPN changed while recovering; stopping watchdog")
        stopIdle()
        return true
    }

    private fun handshakeHealth(
        handshakeAgeSeconds: Long?,
        nowElapsedMillis: Long,
        snapshot: XgimiWatchdogSettings.Snapshot,
    ): HandshakeHealth {
        if (handshakeAgeSeconds != null)
            return if (handshakeAgeSeconds > snapshot.staleHandshakeSeconds) HandshakeHealth.SUSPECT else HandshakeHealth.KNOWN
        if (lastKnownHandshakeEpochSeconds == 0L) {
            val firstSeen = firstSuspiciousHandshakeElapsedMillis.takeIf { it > 0L } ?: nowElapsedMillis
            val staleMillis = TimeUnit.SECONDS.toMillis(snapshot.staleHandshakeSeconds)
            return if (nowElapsedMillis - firstSeen >= staleMillis) HandshakeHealth.SUSPECT else HandshakeHealth.UNKNOWN
        }
        if (lastKnownHandshakeEpochSeconds == -1L || lastKnownHandshakeEpochSeconds == -2L)
            return HandshakeHealth.SUSPECT
        return HandshakeHealth.UNKNOWN
    }

    private fun acquireWakeLock(): PowerManager.WakeLock {
        val powerManager = getSystemService(PowerManager::class.java)
        return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:XgimiWatchdog").apply {
            acquire(WAKELOCK_TIMEOUT_MILLIS)
        }
    }

    private fun isSystemVpnUp(): Boolean {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        return connectivityManager.allNetworks.any { network ->
            connectivityManager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }

    private suspend fun updateStatus(text: String, action: String) {
        if (text == lastNotificationText)
            return
        lastNotificationText = text
        XgimiWatchdogSettings.setStatus(text, action)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun scheduleAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val triggerAt = SystemClock.elapsedRealtime() + ALARM_INTERVAL_MILLIS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmPendingIntent())
        else
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmPendingIntent())
    }

    private fun cancelAlarm() {
        getSystemService(AlarmManager::class.java).cancel(alarmPendingIntent())
    }

    private fun alarmPendingIntent(): PendingIntent {
        val intent = Intent(this, XgimiWatchdogReceiver::class.java).setAction(XgimiWatchdogReceiver.ACTION_KICK)
        return PendingIntent.getBroadcast(this, 0, intent, pendingIntentFlags())
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return
        val channel = NotificationChannel(CHANNEL_ID, "AmneziaWG watchdog", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val activityIntent = Intent(this, TvMainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0, activityIntent, pendingIntentFlags())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AmneziaWG")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("AmneziaWG")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build()
        }
    }

    companion object {
        private const val TAG = "AmneziaWG/XgimiWatchdog"
        private const val CHANNEL_ID = "xgimi_watchdog"
        private const val NOTIFICATION_ID = 0x584749
        private const val EXTRA_CHECK_NOW = "check_now"
        private const val MIN_CHECK_INTERVAL_MILLIS = 1_000L
        private const val ALARM_INTERVAL_MILLIS = 60_000L
        private const val WAKELOCK_TIMEOUT_MILLIS = 10_000L

        fun start(context: Context, checkNow: Boolean = false) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, XgimiWatchdogService::class.java).putExtra(EXTRA_CHECK_NOW, checkNow)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    appContext.startForegroundService(intent)
                else
                    appContext.startService(intent)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Unable to start watchdog service; scheduling watchdog job", e)
                scheduleFallbackIfDesired(appContext)
            } catch (e: SecurityException) {
                Log.e(TAG, "Unable to start watchdog service; foreground permission/type denied", e)
                recordStartFailure(appContext, e)
            }
        }

        private fun recordStartFailure(context: Context, e: Throwable) {
            context.applicationScope.launch {
                XgimiWatchdogSettings.setStatus("watchdog foreground blocked", "foreground", e.javaClass.simpleName)
            }
        }

        private fun scheduleFallbackIfDesired(context: Context) {
            context.applicationScope.launch {
                val shouldSchedule = try {
                    val snapshot = XgimiWatchdogSettings.snapshot()
                    snapshot.enabled && snapshot.desiredVpnEnabled
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "Unable to read watchdog settings for fallback job", e)
                    true
                }
                if (shouldSchedule)
                    XgimiWatchdogJobService.schedule(context)
                else
                    XgimiWatchdogJobService.cancel(context)
            }
        }

        private fun pendingIntentFlags(): Int =
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private enum class HandshakeHealth(val label: String) {
        KNOWN("known"),
        UNKNOWN("unknown"),
        SUSPECT("suspect"),
    }
}
