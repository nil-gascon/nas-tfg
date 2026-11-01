package com.nilgascon.nas.utils
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.nilgascon.nas.model.*
import com.nilgascon.nas.xarxa.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
object BackupManager {
    private val gson = Gson()
    suspend fun executarBackupAutomatic(context: Context) = withContext(Dispatchers.IO) {
        val settings = SettingsRepository(context)
        val token = settings.getAuthToken() ?: return@withContext
        settings.getActiveConnection()?.let { connection ->
            RetrofitClient.updateBaseUrl(connection.getBaseUrl())
        }
        val api = RetrofitClient.instance
        AppLogger.ba("BackupAuto", "Iniciant procés automàtic real...")
        val includePhotos = settings.autoBackupPhotos
        val includeVideos = settings.autoBackupVideos
        val includeAudio = settings.autoBackupAudio
        val includeDocs = settings.autoBackupDocs
        val includeContacts = settings.autoBackupContacts
        val includeSms = settings.autoBackupSms
        if (!includePhotos && !includeVideos && !includeAudio && !includeDocs && !includeContacts && !includeSms) {
            AppLogger.w("BackupAuto", "Res seleccionat per copiar.")
            return@withContext
        }
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val deviceFolder = sanitize("${Build.MANUFACTURER}_${Build.MODEL}")
            val backupFolder = "backup_${timestamp}_automatic"
            val destinationRoot = "backups/mobil/$deviceFolder/$backupFolder"
            AppLogger.ba("BackupAuto", "Còpia en curs a: $destinationRoot")
            val mediaItems = mutableListOf<BackupItem>()
            if (includePhotos) mediaItems += obtenirImatges(context)
            if (includeVideos) mediaItems += obtenirVideos(context)
            if (includeAudio) mediaItems += obtenirAudios(context)
            for (item in mediaItems) {
                val subfolder = when (item.tipus) {
                    "photos" -> "photos"
                    "videos" -> "videos"
                    else -> "audio"
                }
                val targetPath = "$destinationRoot/$subfolder"
                val storedName = "${item.id}_${sanitize(item.nom)}"
                try {
                    pujarUriAlNas(context, token, item.uri, storedName, targetPath, item.dateModified?.let { it / 1000f })
                } catch (e: Exception) {
                    AppLogger.e("BackupAuto", "Error pujant media ${item.nom}: ${e.message}")
                }
            }
            if (includeDocs) {
                val docs = obtenirDocumentsAutomatics()
                for (doc in docs) {
                    val targetPath = if (doc.relativeFolder.isBlank()) "$destinationRoot/documents" else "$destinationRoot/documents/${doc.relativeFolder}"
                    try {
                        pujarUriAlNas(context, token, doc.uri, sanitize(doc.nom), targetPath, doc.sizeBytes.toFloat()) 
                    } catch (e: Exception) {
                        AppLogger.e("BackupAuto", "Error pujant document ${doc.nom}: ${e.message}")
                    }
                }
            }
            if (includeContacts) {
                val contacts = obtenirContactes(context)
                try {
                    pujarDadesJson(context, token, contacts, "contacts.json", "$destinationRoot/contacts")
                } catch (e: Exception) {
                    AppLogger.e("BackupAuto", "Error pujant contactes: ${e.message}")
                }
            }
            if (includeSms) {
                val sms = obtenirSms(context)
                try {
                    pujarDadesJson(context, token, sms, "sms.json", "$destinationRoot/sms")
                } catch (e: Exception) {
                    AppLogger.e("BackupAuto", "Error pujant SMS: ${e.message}")
                }
            }
            AppLogger.s("BackupAuto", "Backup automàtic completat amb tots els fitxers.")
            settings.lastBackupTimestamp = System.currentTimeMillis()
            settings.lastBackupSuccess = true
            settings.lastBackupMessage = "Còpia realitzada correctament."
            if (settings.notifyOnSuccess) {
                NotificationHelper.showNotification(context, "Backup Automàtic", "La còpia s'ha guardat a $backupFolder")
            }
        } catch (e: Exception) {
            AppLogger.e("BackupAuto", "Error en backup real: ${e.message}")
            settings.lastBackupSuccess = false
            settings.lastBackupMessage = e.message ?: "Error"
            if (settings.notifyOnError) {
                NotificationHelper.showNotification(context, "Error en Backup", "No s'ha pogut completar la còpia.", true)
            }
            throw e
        }
    }
    private suspend fun pujarUriAlNas(context: Context, token: String, uri: Uri, fileName: String, targetPath: String, mtime: Float?) {
        val tempFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: return
        try {
            val requestFile = tempFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
            val pathBody = targetPath.toRequestBody("text/plain".toMediaTypeOrNull())
            var darreraExcepcio: Exception? = null
            for (intent in 1..2) {
                try {
                    RetrofitClient.instance.pujarFitxer(token, pathBody, part, mtime)
                    darreraExcepcio = null
                    break
                } catch (e: Exception) {
                    darreraExcepcio = e
                    if (intent < 2) kotlinx.coroutines.delay(1000)
                }
            }
            if (darreraExcepcio != null) throw darreraExcepcio
        } finally {
            tempFile.delete()
        }
    }
    private suspend fun pujarDadesJson(context: Context, token: String, data: Any, fileName: String, targetPath: String) {
        val tempFile = File(context.cacheDir, fileName)
        tempFile.writeText(gson.toJson(data))
        try {
            val requestFile = tempFile.asRequestBody("application/json".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
            val pathBody = targetPath.toRequestBody("text/plain".toMediaTypeOrNull())
            RetrofitClient.instance.pujarFitxer(token, pathBody, part, (System.currentTimeMillis() / 1000f))
        } finally {
            tempFile.delete()
        }
    }
    private fun obtenirImatges(context: Context): List<BackupItem> {
        val result = mutableListOf<BackupItem>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_MODIFIED)
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result.add(BackupItem(id, ContentUris.withAppendedId(collection, id), cursor.getString(nameCol) ?: "img", "image/*", "photos", 0, null, 0, cursor.getLong(modCol) * 1000))
            }
        }
        return result
    }
    private fun obtenirVideos(context: Context): List<BackupItem> {
        val result = mutableListOf<BackupItem>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATE_MODIFIED)
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result.add(BackupItem(id, ContentUris.withAppendedId(collection, id), cursor.getString(nameCol) ?: "vid", "video/*", "videos", 0, null, 0, cursor.getLong(modCol) * 1000))
            }
        }
        return result
    }
    private fun obtenirAudios(context: Context): List<BackupItem> {
        val result = mutableListOf<BackupItem>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_MODIFIED)
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result.add(BackupItem(id, ContentUris.withAppendedId(collection, id), cursor.getString(nameCol) ?: "aud", "audio/*", "audio", 0, null, 0, cursor.getLong(modCol) * 1000))
            }
        }
        return result
    }
    private fun obtenirDocumentsAutomatics(): List<BackupDocumentItem> {
        val result = mutableListOf<BackupDocumentItem>()
        val root = Environment.getExternalStorageDirectory()
        if (!root.exists() || !root.canRead()) return emptyList()
        root.walkTopDown().filter { it.isFile && esDocCompatible(it.name) }.forEach { file ->
            result.add(BackupDocumentItem(Uri.fromFile(file), file.name, "application/octet-stream", file.length(), ""))
        }
        return result
    }
    private fun esDocCompatible(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx") || 
               n.endsWith(".xls") || n.endsWith(".xlsx") || n.endsWith(".txt") ||
               n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z") ||
               n.endsWith(".tar") || n.endsWith(".gz")
    }
    private fun obtenirContactes(context: Context): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)?.use { cursor ->
            val nameCol = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                if (nameCol != -1) list.add(mapOf("name" to (cursor.getString(nameCol) ?: "Sense nom")))
            }
        }
        return list
    }
    private fun obtenirSms(context: Context): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null)?.use { cursor ->
            val bodyCol = cursor.getColumnIndex(Telephony.Sms.BODY)
            while (cursor.moveToNext()) {
                if (bodyCol != -1) list.add(mapOf("body" to (cursor.getString(bodyCol) ?: "")))
            }
        }
        return list
    }
    private fun sanitize(name: String) = name.replace(Regex("""[\\/:*?"<>|]"""), "_")
    private data class BackupDocumentItem(val uri: Uri, val nom: String, val mimeType: String, val sizeBytes: Long, val relativeFolder: String)
}
