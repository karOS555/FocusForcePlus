package com.focusforceplus.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.focusforceplus.app.MainActivity
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.ui.navigation.AppModule
import com.focusforceplus.app.ui.navigation.SettingsRoutes
import com.focusforceplus.app.util.NotificationStyle.applyBrand
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A backup every night into a folder the user picked, for example one that
 * Syncthing or a cloud app keeps in sync. One file per day; the newest [keep]
 * stay, older ones are deleted. Only files this feature wrote are ever touched.
 *
 * Android's storage access framework does the work: the user grants access to
 * one folder once, and no storage permission is involved.
 */
@Singleton
class AutoBackup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val backupManager: BackupManager,
) {
    /** Takes over the folder the user picked and starts the nightly backups. */
    suspend fun useFolder(tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(tree, flags)
        settings.autoBackup.first().folderUri
            ?.takeIf { it != tree.toString() }
            ?.let { old -> releaseFolder(Uri.parse(old)) }
        settings.saveAutoBackupFolder(tree.toString())
        schedule(context)
    }

    suspend fun turnOff() {
        settings.autoBackup.first().folderUri?.let { releaseFolder(Uri.parse(it)) }
        settings.saveAutoBackupFolder(null)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    /** Writes today's backup now. Returns null on success or what went wrong. */
    suspend fun runNow(): String? = withContext(Dispatchers.IO) {
        val state = settings.autoBackup.first()
        val tree = state.folderUri?.let(Uri::parse) ?: return@withContext "No folder chosen."
        val error = runCatching {
            val (json, _) = backupManager.buildBackup()
            val name = FILE_PREFIX + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + FILE_SUFFIX
            val existing = children(tree)
            val target = existing.firstOrNull { it.second == name }?.first
                ?: DocumentsContract.createDocument(context.contentResolver, folderDocument(tree), "application/json", name)
                ?: error("The folder did not accept a new file.")
            context.contentResolver.openOutputStream(target, "wt")?.use {
                it.write(json.toString(2).toByteArray(Charsets.UTF_8))
            } ?: error("The backup file could not be written.")
            // Names carry the date, so sorting them puts the newest first.
            (existing.filter { it.second != name } + (target to name))
                .filter { it.second.startsWith(FILE_PREFIX) && it.second.endsWith(FILE_SUFFIX) }
                .sortedByDescending { it.second }
                .drop(state.keep)
                .forEach { (uri, _) -> runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) } }
        }.exceptionOrNull()?.let { e ->
            if (e is SecurityException) "FocusForce+ lost access to the folder. Choose it again."
            else e.message ?: "The backup failed."
        }
        settings.saveAutoBackupResult(System.currentTimeMillis(), error)
        if (error == null) clearFailureNotice()
        error
    }

    /**
     * Says so when the nightly backup gave up. Without this a lost folder went
     * unnoticed until someone looked into the settings, which is usually the day
     * the backup is needed.
     */
    fun postFailureNotice(error: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BACKUP, "Backups", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When an automatic backup could not be saved"
                group = NotificationStyle.GROUP_REMINDERS
            }
        )
        val open = PendingIntent.getActivity(
            context, RC_OPEN,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_ROUTE, SettingsRoutes.AUTO_BACKUP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_BACKUP)
            .applyBrand(context, AppModule.SETTINGS, "Backup")
            .setContentTitle("Last night's backup was not saved")
            .setContentText(error)
            .setStyle(NotificationCompat.BigTextStyle().bigText(error))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID_FAILED, notification) }
    }

    private fun clearFailureNotice() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_FAILED)
    }

    /** A readable name for the chosen folder. */
    fun folderName(tree: String?): String? = tree?.let {
        runCatching {
            val uri = Uri.parse(it)
            context.contentResolver.query(
                folderDocument(uri),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    private fun folderDocument(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun children(tree: Uri): List<Pair<Uri, String>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return context.contentResolver.query(childrenUri, columns, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    add(DocumentsContract.buildDocumentUriUsingTree(tree, id) to name)
                }
            }
        }.orEmpty()
    }

    private fun releaseFolder(tree: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    companion object {
        const val UNIQUE_NAME = "auto_backup"
        private const val CHANNEL_BACKUP = "backup_status"
        // Not 970_001: that id belongs to the sleep wind-down notice, and one would
        // replace the other.
        private const val NOTIFICATION_ID_FAILED = 980_001
        private const val RC_OPEN = 980_002
        const val FILE_PREFIX = "focusforceplus-backup-"
        const val FILE_SUFFIX = ".json"

        /** Once a day, at night, when the battery is fine. */
        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val next = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        }
    }
}

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun autoBackup(): AutoBackup
    }

    override suspend fun doWork(): Result {
        val backup = EntryPointAccessors
            .fromApplication(applicationContext, WorkerEntryPoint::class.java)
            .autoBackup()
        val error = backup.runNow()
        // A lost folder will not come back by retrying; anything else may.
        return when {
            error == null -> Result.success()
            runAttemptCount < 3 && !error.startsWith("FocusForce+ lost access") -> Result.retry()
            else -> {
                backup.postFailureNotice(error)
                // A periodic job that fails is still run again tomorrow.
                Result.failure()
            }
        }
    }
}
