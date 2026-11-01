package com.nilgascon.nas.model
import android.net.Uri
data class BackupItem(
    val id: Long,
    val uri: Uri,
    val nom: String,
    val mimeType: String,
    val tipus: String, 
    val sizeBytes: Long,
    val dateTaken: Long?,
    val dateAdded: Long?,
    val dateModified: Long?
)
data class BackupMetadataSidecar(
    val originalId: Long,
    val originalName: String,
    val storedName: String,
    val mimeType: String,
    val tipus: String,
    val sizeBytes: Long,
    val dateTaken: Long?,
    val dateAdded: Long?,
    val dateModified: Long?,
    val sourceUri: String,
    val backupCreatedAt: String
)
data class BackupUiState(
    val isRunning: Boolean = false,
    val currentPhase: String = "",
    val currentFileName: String = "",
    val totalItems: Int = 0,
    val uploadedItems: Int = 0,
    val totalBytes: Long = 0L,
    val uploadedBytes: Long = 0L,
    val progress: Float = 0f,
    val etaSeconds: Long? = null,
    val destinationFolder: String = "",
    val message: String = "",
    val error: String? = null
)