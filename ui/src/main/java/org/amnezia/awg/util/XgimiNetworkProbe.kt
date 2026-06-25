/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class XgimiNetworkProbe(context: Context) {
    data class Result(val healthy: Boolean, val detail: String)

    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    suspend fun probeDefault(host: String, port: Int, timeoutMillis: Int): Result {
        invalidInputResult("default", port, timeoutMillis)?.let { return it }
        return probe(host, port, timeoutMillis, null, "default")
    }

    suspend fun probeUnderlying(host: String, port: Int, timeoutMillis: Int): Result {
        invalidInputResult("underlying", port, timeoutMillis)?.let { return it }

        val networks = connectivityManager.allNetworks.mapNotNull { candidate ->
            val capabilities = connectivityManager.getNetworkCapabilities(candidate) ?: return@mapNotNull null
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                candidate to capabilities
            } else {
                null
            }
        }.sortedByDescending { (_, capabilities) ->
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.map { (network, _) ->
            network
        }

        if (networks.isEmpty())
            return Result(false, "no underlying network")

        var lastResult: Result? = null
        networks.forEach { network ->
            val result = probe(host, port, timeoutMillis, network, "underlying")
            if (result.healthy)
                return result
            lastResult = result
        }
        return lastResult ?: Result(false, "underlying failed")
    }

    private fun invalidInputResult(label: String, port: Int, timeoutMillis: Int): Result? {
        if (timeoutMillis <= 0)
            return Result(false, "$label failed: invalid timeout")
        if (port <= 0 || port > 65535)
            return Result(false, "$label failed: invalid port")
        return null
    }

    private suspend fun probe(
        host: String,
        port: Int,
        timeoutMillis: Int,
        network: Network?,
        label: String,
    ): Result = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                network?.bindSocket(socket)
                socket.connect(InetSocketAddress(host, port), timeoutMillis)
            }
            Result(true, "$label ok")
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result(false, "$label failed: ${e.javaClass.simpleName}")
        } catch (e: RuntimeException) {
            Result(false, "$label failed: ${e.javaClass.simpleName}")
        }
    }
}
