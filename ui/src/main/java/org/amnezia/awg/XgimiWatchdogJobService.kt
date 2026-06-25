/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import org.amnezia.awg.util.XgimiWatchdogSettings
import org.amnezia.awg.util.applicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class XgimiWatchdogJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Log.i(TAG, "Job fired")
        applicationScope.launch {
            val shouldStart = try {
                val snapshot = XgimiWatchdogSettings.snapshot()
                snapshot.enabled && snapshot.desiredVpnEnabled
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Unable to read watchdog settings from job", e)
                true
            }
            if (shouldStart)
                XgimiWatchdogService.start(this@XgimiWatchdogJobService, true)
            else
                cancel(this@XgimiWatchdogJobService)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val TAG = "AmneziaWG/XgimiWatchdogJob"
        private const val JOB_ID = 0x5847494d
        private const val FIFTEEN_MINUTES = 15 * 60 * 1000L

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val component = ComponentName(context, XgimiWatchdogJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, component)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(FIFTEEN_MINUTES)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                builder.setRequiresBatteryNotLow(false)
            scheduler.schedule(builder.build())
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }
}
