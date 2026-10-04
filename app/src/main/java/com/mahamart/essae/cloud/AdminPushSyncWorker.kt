package com.mahamart.essae.cloud

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mahamart.essae.data.AppDatabase

/**
 * Pulls pending Admin Push updates into Room in the background.
 * It never uploads to Essae or confirms a physical scale upload.
 */
class AdminPushSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.create(applicationContext)
            val pluDao = db.pluDao()
            val auditDao = db.priceChangeAuditDao()
            val sync = StoreAdminPushSync(applicationContext)

            val result = sync.pullAndApply(
                currentPlus = pluDao.getAll(),
                upsert = { pluDao.upsert(it) },
                insertAudit = { auditDao.insert(it) },
                hasAdminAudit = { updateId, pluNo ->
                    auditDao.hasAdminPushAudit(updateId, pluNo)
                }
            )

            result.fold(
                onSuccess = {
                    Log.i(TAG, "Background Admin Push sync completed; applied ${it.size} PLUs")
                    Result.success()
                },
                onFailure = { error ->
                    Log.w(TAG, "Background Admin Push sync failed", error)
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                }
            )
        } catch (error: Exception) {
            Log.e(TAG, "Background Admin Push worker failed", error)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "AdminPushSyncWorker"
        private const val MAX_RETRIES = 5
    }
}
