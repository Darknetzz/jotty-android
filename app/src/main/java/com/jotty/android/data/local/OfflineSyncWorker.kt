package com.jotty.android.data.local

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jotty.android.JottyApp
import com.jotty.android.data.api.ApiClient
import com.jotty.android.util.AppLog
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class OfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? JottyApp ?: return Result.success()
        val settings = app.settingsRepository
        if (!settings.offlineModeEnabled.first()) return Result.success()

        val instances = settings.instances.first()
        if (instances.isEmpty()) return Result.success()

        val db = JottyDatabase.getDatabase(applicationContext)
        instances.forEach { instance ->
            if (instance.serverUrl.isBlank() || instance.apiKey.isBlank()) return@forEach
            runCatching {
                val api = ApiClient.create(instance.serverUrl, instance.apiKey)
                val notesRepo =
                    OfflineNotesRepository(
                        context = applicationContext,
                        database = db,
                        instanceId = instance.id,
                        api = api,
                    )
                val checklistsRepo =
                    OfflineChecklistsRepository(
                        context = applicationContext,
                        database = db,
                        instanceId = instance.id,
                        api = api,
                    )
                try {
                    notesRepo.syncNotes()
                    checklistsRepo.syncChecklists(force = true)
                } finally {
                    notesRepo.close()
                    checklistsRepo.close()
                }
            }.onFailure {
                AppLog.d("OfflineSyncWorker", "Background sync failed for ${instance.id}: ${it.message}")
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "jotty-offline-periodic-sync"

        fun schedule(context: Context) {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            val request =
                PeriodicWorkRequestBuilder<OfflineSyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
