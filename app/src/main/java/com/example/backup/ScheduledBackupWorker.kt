package com.example.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class ScheduledBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = Firebase.auth.currentUser?.uid ?: return Result.success()
        val manager = GoogleDriveBackupManager(applicationContext)
        val token = manager.trySilentToken() ?: return Result.retry()
        val backupRes = manager.performDriveBackup(accessToken = token, userId = uid)
        return if (backupRes.isSuccess) Result.success() else Result.retry()
    }
}
