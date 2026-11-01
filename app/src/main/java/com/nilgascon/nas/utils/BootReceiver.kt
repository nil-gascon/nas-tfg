package com.nilgascon.nas.utils
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = SettingsRepository(context)
            if (settings.isAutoBackupEnabled) {
                AppLogger.i("System", "Dispositiu reiniciat. Reprogramant backup automàtic...")
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
                val intervalMinuts = when(settings.backupUnit) {
                    "MINUTES" -> settings.backupValue.toLong()
                    "HOURS" -> settings.backupValue.toLong() * 60
                    "DAYS" -> settings.backupValue.toLong() * 1440
                    "WEEKLY" -> 10080L
                    else -> 1440L
                }.coerceAtLeast(15L)
                val request = PeriodicWorkRequestBuilder<BackupWorker>(
                    intervalMinuts, 
                    TimeUnit.MINUTES
                ).setConstraints(constraints).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "backup_automatic",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }
    }
}
