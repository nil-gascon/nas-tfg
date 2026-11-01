package com.nilgascon.nas.xarxa
import com.nilgascon.nas.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*
data class MkdirRequest(
    val nom_carpeta: String,
    val path: String = "",
    val mtime: Float? = null,
    val allow_reuse: Boolean = false
)
data class RenameRequest(val path: String, val nou_nom: String)
data class PasswordChangeRequest(val old_password: String, val new_password: String)
interface NasApiService {
    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("username") usuari: String,
        @Field("password") pass: String
    ): LoginResponse
    @GET("api/files/list")
    suspend fun llistarDirectori(
        @Header("Authorization") token: String,
        @Query("path") path: String
    ): LlistatDirectoriResponse
    @DELETE("api/files/delete")
    suspend fun eliminarElement(
        @Header("Authorization") token: String,
        @Query("path") path: String
    ): SimpleResponse
    @Headers("Content-Type: application/json")
    @POST("api/files/mkdir")
    suspend fun crearCarpeta(
        @Header("Authorization") token: String,
        @Body request: MkdirRequest
    ): MkdirResponse
    @Headers("Content-Type: application/json")
    @PUT("api/files/rename")
    suspend fun canviarNom(
        @Header("Authorization") token: String,
        @Body request: RenameRequest
    ): SimpleResponse
    @Multipart
    @POST("api/files/upload")
    suspend fun pujarFitxer(
        @Header("Authorization") token: String,
        @Part("path") path: RequestBody,
        @Part file: MultipartBody.Part,
        @Part("mtime") mtime: Float? = null
    ): SimpleResponse
    @GET("api/files/download")
    @Streaming
    suspend fun descarregarFitxer(
        @Header("Authorization") token: String,
        @Query("path") path: String
    ): okhttp3.ResponseBody
    @GET("api/system/status")
    suspend fun obtenirEstat(
        @Header("Authorization") token: String
    ): StatusResponse
    @GET("api/system/ping")
    suspend fun ping(
        @Header("Authorization") token: String
    ): SimpleResponse
    @GET("api/system/test-download-stream")
    @Streaming
    suspend fun testDownloadStream(
        @Header("Authorization") token: String
    ): okhttp3.ResponseBody
    @Multipart
    @POST("api/system/test-upload")
    suspend fun testUpload(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): SimpleResponse
    @GET("api/system/details")
    suspend fun obtenirDetallsSistema(
        @Header("Authorization") token: String
    ): SystemDetailsResponse
    @POST("api/system/change-password")
    suspend fun canviarContrasenya(
        @Header("Authorization") token: String,
        @Body request: PasswordChangeRequest
    ): SimpleResponse
    @POST("api/system/logs")
    suspend fun rebreLogs(
        @Header("Authorization") token: String,
        @Body logs: List<LogEntry>
    ): SimpleResponse
    @GET("api/system/logs")
    suspend fun llistarLogs(
        @Header("Authorization") token: String
    ): List<LogEntry>
    @GET("api/system/setup-status")
    suspend fun getSetupStatus(): SetupStatusResponse
    @POST("api/system/setup")
    suspend fun initialSetup(@Body request: SetupRequest): LoginResponse
    @GET("api/admin/users")
    suspend fun llistarUsuaris(
        @Header("Authorization") token: String
    ): List<UserOut>
    @POST("api/admin/users")
    suspend fun crearUsuari(
        @Header("Authorization") token: String,
        @Body request: UserCreate
    ): SimpleResponse
    @PUT("api/admin/users/{username}")
    suspend fun editarUsuari(
        @Header("Authorization") token: String,
        @Path("username") oldUsername: String,
        @Body request: UserUpdate
    ): SimpleResponse
    @DELETE("api/admin/users/{username}")
    suspend fun eliminarUsuari(
        @Header("Authorization") token: String,
        @Path("username") username: String
    ): SimpleResponse
}
