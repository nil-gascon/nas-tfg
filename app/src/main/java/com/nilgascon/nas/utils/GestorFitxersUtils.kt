package com.nilgascon.nas.utils
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
sealed class ElementPujada {
    data class Fitxer(val pathRelatiu: String, val part: MultipartBody.Part, val mtime: Float?) : ElementPujada()
    data class Carpeta(val pathRelatiu: String, val nom: String, val mtime: Float?) : ElementPujada()
}
object GestorFitxersUtils {
    fun uriAMultipart(context: Context, uri: Uri): MultipartBody.Part? {
        val contentResolver = context.contentResolver
        var nomFitxer = "arxiu_pujat"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) nomFitxer = cursor.getString(nameIndex)
        }
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val fileBytes = inputStream.readBytes()
        inputStream.close()
        val requestFile = fileBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", nomFitxer, requestFile)
    }
    fun getMtime(context: Context, uri: Uri): Float? {
        return try {
            val df = if (uri.scheme == "content") DocumentFile.fromSingleUri(context, uri) else null
            val time = df?.lastModified() ?: 0L
            if (time > 0) (time / 1000f) else null
        } catch (e: Exception) {
            null
        }
    }
    fun urisAMultipart(context: Context, uris: List<Uri>): List<MultipartBody.Part> {
        return uris.mapNotNull { uriAMultipart(context, it) }
    }
    fun recollirEstructuraCompleta(context: Context, root: DocumentFile, pathRelatiuPare: String = ""): List<ElementPujada> {
        val resultat = mutableListOf<ElementPujada>()
        val nom = root.name ?: ""
        val mtime = if (root.lastModified() > 0) (root.lastModified() / 1000f) else null
        resultat.add(ElementPujada.Carpeta(pathRelatiuPare, nom, mtime))
        val pathPerAlsFills = if (pathRelatiuPare.isEmpty()) nom else "$pathRelatiuPare/$nom"
        root.listFiles().forEach { file ->
            if (file.isDirectory) {
                resultat.addAll(recollirEstructuraCompleta(context, file, pathPerAlsFills))
            } else if (file.isFile) {
                uriAMultipart(context, file.uri)?.let { part ->
                    val fileMtime = if (file.lastModified() > 0) (file.lastModified() / 1000f) else null
                    resultat.add(ElementPujada.Fitxer(pathPerAlsFills, part, fileMtime))
                }
            }
        }
        return resultat
    }
}
