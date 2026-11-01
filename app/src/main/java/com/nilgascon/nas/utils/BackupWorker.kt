package com.nilgascon.nas.utils
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext)
        val token = settings.getAuthToken()
        if (token == null) {
            AppLogger.e("BackupWorker", "No hi ha token d'autenticació. Backup cancel·lat.")
            return Result.failure()
        }
        AppLogger.i("BackupWorker", "Iniciant backup automàtic programat...")
        return try {
            BackupManager.executarBackupAutomatic(applicationContext)
            settings.lastBackupSuccess = true
            settings.lastBackupMessage = "Còpia finalitzada correctament."
            Result.success()
        } catch (e: Exception) {
            AppLogger.e("BackupWorker", "Error en el backup automàtic: ${e.message}")
            settings.lastBackupSuccess = false
            settings.lastBackupMessage = e.message ?: "Error desconegut"
            Result.retry()
        }
    }
}
