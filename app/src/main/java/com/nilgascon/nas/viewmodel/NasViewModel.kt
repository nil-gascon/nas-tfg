package com.nilgascon.nas.viewmodel
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.nilgascon.nas.model.*
import com.nilgascon.nas.xarxa.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.documentfile.provider.DocumentFile
import android.os.Environment
import androidx.work.*
import com.nilgascon.nas.utils.*
import java.util.concurrent.TimeUnit
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
class NasViewModel(private val application: android.app.Application) : ViewModel() {
    private val api get() = RetrofitClient.instance
    private val gson = Gson()
    private val settings = SettingsRepository(application)
    private data class BackupDocumentItem(
        val uri: Uri,
        val nom: String,
        val mimeType: String,
        val sizeBytes: Long,
        val relativeFolder: String
    )
    private data class ContactBackupEntry(
        val contactId: Long,
        val displayName: String,
        val phones: List<String>,
        val emails: List<String>
    )
    private data class SmsBackupEntry(
        val smsId: Long,
        val address: String?,
        val body: String?,
        val date: Long,
        val type: Int,
        val read: Int
    )
    private val _files = MutableStateFlow<List<String>>(emptyList())
    val files: StateFlow<List<String>> = _files
    private val _status = MutableStateFlow<StatusResponse?>(null)
    val status: StateFlow<StatusResponse?> = _status
    private val _systemDetails = MutableStateFlow<SystemDetailsResponse?>(null)
    val systemDetails: StateFlow<SystemDetailsResponse?> = _systemDetails
    private val _performanceStats = MutableStateFlow(PerformanceStats())
    val performanceStats: StateFlow<PerformanceStats> = _performanceStats
    private val _rutaActual = MutableStateFlow("")
    val rutaActual: StateFlow<String> = _rutaActual
    private val _contingutDirectori = MutableStateFlow<List<ArxiuNas>>(emptyList())
    val contingutDirectori: StateFlow<List<ArxiuNas>> = _contingutDirectori
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading
    data class UploadUiState(
        val isUploading: Boolean = false,
        val progress: Float = 0f,
        val currentFile: String = "",
        val totalFiles: Int = 0,
        val uploadedFiles: Int = 0
    )
    private val _uploadState = MutableStateFlow(UploadUiState())
    val uploadState: StateFlow<UploadUiState> = _uploadState
    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState
    private var jwtToken = ""
    private var dashboardJob: Job? = null
    private var systemDetailsJob: Job? = null
    private var folderJob: Job? = null
    private var backupJob: Job? = null
    private var reachabilityJob: Job? = null
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn
    private val _userRole = MutableStateFlow(settings.getUserRole())
    val userRole: StateFlow<String> = _userRole
    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError
    private val _isSetupNeeded = MutableStateFlow(false)
    val isSetupNeeded: StateFlow<Boolean> = _isSetupNeeded
    private val _usuarisGestio = MutableStateFlow<List<UserOut>>(emptyList())
    val usuarisGestio: StateFlow<List<UserOut>> = _usuarisGestio
    private val _savedConnections = MutableStateFlow(settings.getSavedConnections())
    val savedConnections: StateFlow<List<NasConnection>> = _savedConnections
    private val _activeNas = MutableStateFlow(settings.getActiveConnection())
    val activeNas: StateFlow<NasConnection?> = _activeNas
    private val _isNasAccessible = MutableStateFlow<Boolean?>(null)
    val isNasAccessible: StateFlow<Boolean?> = _isNasAccessible
    private val _logsRemots = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsRemots: StateFlow<List<LogEntry>> = _logsRemots
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    init {
        _activeNas.value?.let { 
            val forcedUrl = it.getBaseUrl()
            RetrofitClient.updateBaseUrl(forcedUrl)
            AppLogger.i("System", "Iniciant amb NAS: ${it.name} (Forçant HTTPS: $forcedUrl)")
        }
        iniciarMonitorXarxa()
        verificarAccessibilitat()
        iniciarLoopAccessibilitat()
    }
    private fun iniciarMonitorXarxa() {
        val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                verificarAccessibilitat()
            }
            override fun onLost(network: Network) {
                verificarAccessibilitat()
            }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
    }
    private fun iniciarLoopAccessibilitat() {
        reachabilityJob?.cancel()
        reachabilityJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                verificarAccessibilitatSync()
                delay(1000)
            }
        }
    }
    override fun onCleared() {
        super.onCleared()
        networkCallback?.let {
            val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
        }
        reachabilityJob?.cancel()
    }
    fun verificarAccessibilitat() {
        viewModelScope.launch(Dispatchers.IO) {
            verificarAccessibilitatSync()
        }
    }
    private fun verificarAccessibilitatSync() {
        val currentNas = _activeNas.value ?: return
        try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(currentNas.ip, currentNas.port.toInt()), 1500)
            socket.close()
            _isNasAccessible.value = true
        } catch (e: Exception) {
            _isNasAccessible.value = false
        }
    }
    fun afegirNas(nom: String, ip: String, port: String, useHttps: Boolean = true) {
        val nova = NasConnection(name = nom, ip = ip, port = port, useHttps = useHttps)
        val llista = settings.getSavedConnections().toMutableList()
        llista.add(nova)
        settings.saveConnections(llista)
        _savedConnections.value = llista
        if (_activeNas.value == null) seleccionarNas(nova)
    }
    fun eliminarNas(connection: NasConnection) {
        val llista = settings.getSavedConnections().filter { it.id != connection.id }
        settings.saveConnections(llista)
        _savedConnections.value = llista
        if (_activeNas.value?.id == connection.id) {
            val seguent = llista.firstOrNull()
            if (seguent != null) seleccionarNas(seguent) else {
                _activeNas.value = null
                settings.activeNasId = null
            }
        }
    }
    fun seleccionarNas(connection: NasConnection) {
        val forcedUrl = connection.getBaseUrl()
        settings.activeNasId = connection.id
        _activeNas.value = connection
        RetrofitClient.updateBaseUrl(forcedUrl)
        AppLogger.i("System", "NAS canviat a: ${connection.name} (Forçant HTTPS: $forcedUrl)")
        verificarAccessibilitat()
    }
    fun programarBackupAutomatic(
        activat: Boolean, 
        valor: Int, 
        unitat: String,
        fotos: Boolean,
        videos: Boolean,
        audio: Boolean,
        docs: Boolean,
        contactes: Boolean,
        sms: Boolean
    ) {
        val valorFinal = if (unitat == "MINUTES") valor.coerceAtLeast(15) else valor
        settings.isAutoBackupEnabled = activat
        settings.backupValue = valorFinal
        settings.backupUnit = unitat
        settings.autoBackupPhotos = fotos
        settings.autoBackupVideos = videos
        settings.autoBackupAudio = audio
        settings.autoBackupDocs = docs
        settings.autoBackupContacts = contactes
        settings.autoBackupSms = sms
        val workManager = WorkManager.getInstance(application)
        if (activat) {
            val intervalMinuts = when(unitat) {
                "MINUTES" -> valorFinal.toLong()
                "HOURS" -> valorFinal.toLong() * 60
                "DAYS" -> valorFinal.toLong() * 1440
                "WEEKLY" -> 10080L
                else -> 1440L
            }.coerceAtLeast(15L)
            AppLogger.i("BackupAuto", "Programant tasca: cada $valorFinal $unitat ($intervalMinuts min)")
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) 
                .setRequiresBatteryNotLow(true) 
                .build()
            val request = PeriodicWorkRequestBuilder<BackupWorker>(intervalMinuts, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag("backup_tag")
                .build()
            workManager.enqueueUniquePeriodicWork(
                "backup_automatic",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            AppLogger.s("BackupAuto", "Tasca programada correctament al sistema.")
        } else {
            AppLogger.w("BackupAuto", "Cancel·lant tasca programada.")
            workManager.cancelUniqueWork("backup_automatic")
        }
    }
    fun getSettings() = settings
    fun getAuthToken(): String = jwtToken
    fun clearLoginError() {
        _loginError.value = null
    }
    fun doLogin(usuari: String, pass: String) {
        viewModelScope.launch {
            try {
                _loginError.value = null
                _isLoggingIn.value = true
                AppLogger.i("Auth", "Intentant iniciar sessió per a: $usuari")
                val response = api.login(usuari.trim(), pass)
                jwtToken = "Bearer ${response.access_token}"
                settings.setAuthToken(jwtToken)
                val role = response.role ?: "user"
                _userRole.value = role
                settings.setUserRole(role)
                try {
                    val status = api.getSetupStatus()
                    _isSetupNeeded.value = !status.setup_completed
                } catch (e: Exception) {
                    _isSetupNeeded.value = false
                }
                _isLoggedIn.value = true
                _isLoggingIn.value = false
                AppLogger.s("Auth", "Sessió iniciada correctament")
                refrescarDashboard()
            } catch (e: HttpException) {
                _isLoggingIn.value = false
                _isLoggedIn.value = false
                _loginError.value = if (e.code() == 401) {
                    "Usuari o contrasenya incorrectes."
                } else if (e.code() == 503) {
                    _isSetupNeeded.value = true
                    "El sistema requereix configuració inicial."
                } else {
                    "Error del servidor (${e.code()})."
                }
            } catch (e: Exception) {
                _isLoggingIn.value = false
                _isLoggedIn.value = false
                _loginError.value = "No s'ha pogut iniciar sessió. Torna-ho a provar."
            }
        }
    }
    fun verificarSetupStatus() {
        viewModelScope.launch {
            try {
                val status = api.getSetupStatus()
                _isSetupNeeded.value = !status.setup_completed
            } catch (e: Exception) {
                _isSetupNeeded.value = false
            }
        }
    }
    fun completarSetup(adminUser: String, adminPass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.initialSetup(SetupRequest(adminUser, adminPass))
                jwtToken = "Bearer ${response.access_token}"
                settings.setAuthToken(jwtToken)
                val role = response.role ?: "admin"
                _userRole.value = role
                settings.setUserRole(role)
                _isSetupNeeded.value = false
                _isLoggedIn.value = true 
                AppLogger.s("Setup", "Configuració inicial completada i sessió iniciada.")
                refrescarDashboard()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Error durant el setup")
            }
        }
    }
    fun carregarUsuaris() {
        viewModelScope.launch {
            try {
                _usuarisGestio.value = api.llistarUsuaris(jwtToken)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun crearUsuari(u: String, p: String, r: String) {
        viewModelScope.launch {
            try {
                api.crearUsuari(jwtToken, UserCreate(u, p, r))
                carregarUsuaris()
                AppLogger.s("Admin", "Usuari creat: $u")
            } catch (e: Exception) {
                AppLogger.e("Admin", "Error creant usuari: ${e.message}")
            }
        }
    }
    fun editarUsuari(oldUsername: String, u: String, p: String?, r: String) {
        viewModelScope.launch {
            try {
                api.editarUsuari(jwtToken, oldUsername, UserUpdate(u, if (p.isNullOrBlank()) null else p, r))
                carregarUsuaris()
                AppLogger.s("Admin", "Usuari actualitzat: $u")
            } catch (e: Exception) {
                AppLogger.e("Admin", "Error editant usuari: ${e.message}")
            }
        }
    }
    fun eliminarUsuari(u: String) {
        viewModelScope.launch {
            try {
                api.eliminarUsuari(jwtToken, u)
                carregarUsuaris()
                AppLogger.s("Admin", "Usuari eliminat: $u")
            } catch (e: Exception) {
                AppLogger.e("Admin", "Error eliminant usuari: ${e.message}")
            }
        }
    }
    fun refrescarDashboard() {
        viewModelScope.launch {
            try {
                _status.value = api.obtenirEstat(jwtToken)
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 403) {
                    if (_isSetupNeeded.value != true) {
                        _isLoggedIn.value = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun logout() {
        jwtToken = ""
        settings.setAuthToken("")
        settings.setUserRole("user")
        _isLoggedIn.value = false
        _userRole.value = "user"
        aturarAutoRefreshDashboard()
        aturarAutoRefreshSystemDetails()
        AppLogger.i("Auth", "Sessió tancada per l'usuari")
    }
    fun carregarDetallsSistema() {
        viewModelScope.launch {
            try {
                _systemDetails.value = api.obtenirDetallsSistema(jwtToken)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun obrirCarpeta(ruta: String = "") {
        viewModelScope.launch {
            try {
                val resp = api.llistarDirectori(jwtToken, ruta)
                _rutaActual.value = resp.ruta_actual
                _contingutDirectori.value = resp.contingut
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun sincronitzarLogs() {
        if (jwtToken.isEmpty()) return
        val pendents = AppLogger.getPendingLogs()
        if (pendents.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                api.rebreLogs(jwtToken, pendents)
                AppLogger.clearPendingLogs(pendents)
            } catch (e: Exception) {
            }
        }
    }
    fun carregarLogsRemots() {
        if (jwtToken.isEmpty()) return
        viewModelScope.launch {
            try {
                _logsRemots.value = api.llistarLogs(jwtToken)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun iniciarAutoRefreshDashboard() {
        dashboardJob?.cancel()
        dashboardJob = viewModelScope.launch {
            while (isActive) {
                try {
                    _status.value = api.obtenirEstat(jwtToken)
                    sincronitzarLogs()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1000)
            }
        }
    }
    fun aturarAutoRefreshDashboard() {
        dashboardJob?.cancel()
        dashboardJob = null
    }
    fun iniciarAutoRefreshSystemDetails() {
        systemDetailsJob?.cancel()
        systemDetailsJob = viewModelScope.launch {
            while (isActive) {
                try {
                    _systemDetails.value = api.obtenirDetallsSistema(jwtToken)
                    _status.value = api.obtenirEstat(jwtToken)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1000)
            }
        }
    }
    fun aturarAutoRefreshSystemDetails() {
        systemDetailsJob?.cancel()
        systemDetailsJob = null
    }
    fun iniciarAutoRefreshCarpeta(ruta: String) {
        folderJob?.cancel()
        folderJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val resp = api.llistarDirectori(jwtToken, ruta)
                    _rutaActual.value = resp.ruta_actual
                    _contingutDirectori.value = resp.contingut
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1000)
            }
        }
    }
    fun aturarAutoRefreshCarpeta() {
        folderJob?.cancel()
        folderJob = null
    }
    fun esborrarElement(rutaCompleta: String) {
        viewModelScope.launch {
            try {
                api.eliminarElement(jwtToken, rutaCompleta)
                obrirCarpeta(_rutaActual.value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun crearCarpeta(nomCarpeta: String) {
        viewModelScope.launch {
            try {
                val request = MkdirRequest(nom_carpeta = nomCarpeta, path = _rutaActual.value)
                api.crearCarpeta(jwtToken, request)
                obrirCarpeta(_rutaActual.value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun canviarNomElement(rutaCompleta: String, nouNom: String) {
        viewModelScope.launch {
            try {
                val request = RenameRequest(path = rutaCompleta, nou_nom = nouNom)
                api.canviarNom(jwtToken, request)
                obrirCarpeta(_rutaActual.value)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun descarregarFitxer(context: Context, arxiu: ArxiuNas) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppLogger.i("Download", "Iniciant descàrrega de: ${arxiu.nom}")
                val response = api.descarregarFitxer(jwtToken, arxiu.ruta_completa)
                val body = response.byteStream()
                
                val nomFinal = if (arxiu.es_carpeta) "${arxiu.nom}.zip" else arxiu.nom
                
                // Configurem el MediaStore per guardar-lo a la carpeta pública de Downloads
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, nomFinal)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (arxiu.es_carpeta) "application/zip" else "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                
                if (uri != null) {
                    resolver.openOutputStream(uri).use { output ->
                        if (output != null) {
                            body.copyTo(output)
                        }
                    }
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Fitxer guardat a Downloads: $nomFinal", android.widget.Toast.LENGTH_LONG).show()
                    }
                } else {
                    throw Exception("No s'ha pogut crear el fitxer a Downloads")
                }

                AppLogger.s("Download", "Descàrrega completada: $nomFinal")
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error en la descàrrega: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
                AppLogger.e("Download", "Error descarregant: ${e.message}")
            }
        }
    }
    fun canviarContrasenya(old: String, new: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                api.canviarContrasenya(jwtToken, PasswordChangeRequest(old, new))
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Error desconegut")
            }
        }
    }
    fun executarTestRendiment() {
        viewModelScope.launch(Dispatchers.IO) {
                _performanceStats.value = PerformanceStats(isRunning = true, phase = TestPhase.PING, message = "Mesurant latència...", maxSpeedMbps = 100.0)
            try {
                val pings = mutableListOf<Long>()
                repeat(10) { i ->
                    val start = System.currentTimeMillis()
                    api.ping(jwtToken)
                    val lat = System.currentTimeMillis() - start
                    pings.add(lat)
                    _performanceStats.value = _performanceStats.value.copy(
                        progress = (i + 1) / 30f,
                        latencyMs = lat
                    )
                    delay(100)
                }
                val avgPing = pings.average().toLong()
                val jitter = pings.zipWithNext { a, b -> Math.abs(a - b) }.average().toLong()
                _performanceStats.value = _performanceStats.value.copy(
                    latencyMs = avgPing,
                    jitterMs = jitter,
                    phase = TestPhase.DOWNLOAD,
                    message = "Provant descàrrega..."
                )
                val startDown = System.currentTimeMillis()
                val responseDown = api.testDownloadStream(jwtToken)
                val inputStream = responseDown.byteStream()
                val buffer = ByteArray(64 * 1024)
                var totalBytesDown = 0L
                var lastCheckTime = startDown
                var lastBytes = 0L
                val history = mutableListOf<Double>()
                while (System.currentTimeMillis() - startDown < 10000 && isActive) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    totalBytesDown += read
                    val now = System.currentTimeMillis()
                    if (now - lastCheckTime >= 500) {
                        val intervalSec = (now - lastCheckTime) / 1000.0
                        val intervalBytes = totalBytesDown - lastBytes
                        val currentMbps = (intervalBytes * 8.0 / (1024 * 1024)) / intervalSec
                        history.add(currentMbps)
                        if (history.size > 20) history.removeAt(0)
                        _performanceStats.value = _performanceStats.value.copy(
                            currentSpeedMbps = currentMbps,
                            maxSpeedMbps = Math.max(_performanceStats.value.maxSpeedMbps, currentMbps),
                            downloadFinalMbps = (totalBytesDown * 8.0 / (1024 * 1024)) / ((now - startDown) / 1000.0),
                            progress = 0.33f + (0.33f * ((now - startDown) / 10000f)),
                            history = history.toList()
                        )
                        lastCheckTime = now
                        lastBytes = totalBytesDown
                    }
                }
                inputStream.close()
                _performanceStats.value = _performanceStats.value.copy(
                    phase = TestPhase.UPLOAD,
                    message = "Provant pujada...",
                    currentSpeedMbps = 0.0,
                    maxSpeedMbps = 100.0,
                    history = emptyList()
                )
                val startUp = System.currentTimeMillis()
                val uploadHistory = mutableListOf<Double>()
                var totalBytesUp = 0L
                lastCheckTime = startUp
                lastBytes = 0L
                val uploadStream = object : okhttp3.RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                    override fun writeTo(sink: okio.BufferedSink) {
                        val dummy = ByteArray(16 * 1024)
                        while (System.currentTimeMillis() - startUp < 10000 && isActive) {
                            sink.write(dummy)
                            totalBytesUp += dummy.size
                            val now = System.currentTimeMillis()
                            if (now - lastCheckTime >= 500) {
                                val intervalSec = (now - lastCheckTime) / 1000.0
                                val intervalBytes = totalBytesUp - lastBytes
                                val currentMbps = (intervalBytes * 8.0 / (1024 * 1024)) / intervalSec
                                uploadHistory.add(currentMbps)
                                if (uploadHistory.size > 20) uploadHistory.removeAt(0)
                                _performanceStats.value = _performanceStats.value.copy(
                                    currentSpeedMbps = currentMbps,
                                    maxSpeedMbps = Math.max(_performanceStats.value.maxSpeedMbps, currentMbps),
                                    uploadFinalMbps = (totalBytesUp * 8.0 / (1024 * 1024)) / ((now - startUp) / 1000.0),
                                    progress = 0.66f + (0.34f * ((now - startUp) / 10000f)),
                                    history = uploadHistory.toList()
                                )
                                lastCheckTime = now
                                lastBytes = totalBytesUp
                            }
                        }
                    }
                }
                val body = MultipartBody.Part.createFormData("file", "speedtest_up.bin", uploadStream)
                api.testUpload(jwtToken, body)
                _performanceStats.value = _performanceStats.value.copy(
                    phase = TestPhase.FINISHED,
                    progress = 1.0f,
                    isRunning = false,
                    message = "Test finalitzat"
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _performanceStats.value = _performanceStats.value.copy(
                    isRunning = false,
                    message = "Error: ${e.message}"
                )
            }
        }
    }
    fun pujarFitxer(fitxerPart: MultipartBody.Part, mtime: Float? = null) {
        viewModelScope.launch {
            _uploadState.value = UploadUiState(isUploading = true, currentFile = fitxerPart.headers?.get("Content-Disposition") ?: "Fitxer")
            try {
                val pathBody = _rutaActual.value.toRequestBody("text/plain".toMediaTypeOrNull())
                api.pujarFitxer(jwtToken, pathBody, fitxerPart, mtime)
                AppLogger.s("Drive", "Fitxer pujat: ${fitxerPart.headers?.get("Content-Disposition")}")
                obrirCarpeta(_rutaActual.value)
            } catch (e: Exception) {
                AppLogger.e("Drive", "Error pujant fitxer: ${e.message}")
                e.printStackTrace()
            } finally {
                _uploadState.value = UploadUiState(isUploading = false)
            }
        }
    }
    fun pujarFitxersMultiples(parts: List<MultipartBody.Part>, mtimes: List<Float> = emptyList()) {
        viewModelScope.launch {
            _uploadState.value = UploadUiState(isUploading = true, totalFiles = parts.size, uploadedFiles = 0)
            try {
                val pathBody = _rutaActual.value.toRequestBody("text/plain".toMediaTypeOrNull())
                parts.forEachIndexed { index, part ->
                    _uploadState.value = _uploadState.value.copy(
                        currentFile = part.headers?.get("Content-Disposition") ?: "Fitxer",
                        uploadedFiles = index,
                        progress = index.toFloat() / parts.size
                    )
                    val mt = if (index < mtimes.size) mtimes[index] else null
                    api.pujarFitxer(jwtToken, pathBody, part, mt)
                }
                AppLogger.s("Drive", "${parts.size} fitxers pujats correctament")
                obrirCarpeta(_rutaActual.value)
            } catch (e: Exception) {
                AppLogger.e("Drive", "Error en pujada múltiple: ${e.message}")
                e.printStackTrace()
            } finally {
                _uploadState.value = UploadUiState(isUploading = false)
            }
        }
    }
    fun pujarEstructuraCompleta(elements: List<ElementPujada>) {
        viewModelScope.launch {
            _uploadState.value = UploadUiState(isUploading = true, totalFiles = elements.size, uploadedFiles = 0)
            try {
                val rutaBase = _rutaActual.value
                val mappedPaths = mutableMapOf<String, String>()
                elements.forEachIndexed { index, element ->
                    _uploadState.value = _uploadState.value.copy(
                        currentFile = when(element) {
                            is ElementPujada.Fitxer -> element.part.headers?.get("Content-Disposition") ?: "Fitxer"
                            is ElementPujada.Carpeta -> element.nom
                        },
                        uploadedFiles = index,
                        progress = index.toFloat() / elements.size
                    )
                    when(element) {
                        is ElementPujada.Carpeta -> {
                            val rutaPareOriginal = element.pathRelatiu
                            val req = MkdirRequest(nom_carpeta = element.nom, path = if (rutaBase.isEmpty()) rutaPareOriginal else "$rutaBase/$rutaPareOriginal", mtime = element.mtime)
                            val resp = api.crearCarpeta(jwtToken, req)
                            val pathCreatAlNas = if (rutaBase.isEmpty()) resp.nom_final else "${element.pathRelatiu}/${resp.nom_final}".trim('/')
                            val pathRelatiuOriginalSencer = if (element.pathRelatiu.isEmpty()) element.nom else "${element.pathRelatiu}/${element.nom}"
                            val pathRelatiuNouSencer = if (element.pathRelatiu.isEmpty()) resp.nom_final else "${element.pathRelatiu}/${resp.nom_final}"
                            mappedPaths[pathRelatiuOriginalSencer] = pathRelatiuNouSencer
                        }
                        is ElementPujada.Fitxer -> {
                            val rutaPareNova = mappedPaths[element.pathRelatiu] ?: element.pathRelatiu
                            val rutaDesti = if (rutaBase.isEmpty()) rutaPareNova else "$rutaBase/$rutaPareNova"
                            val pathBody = rutaDesti.toRequestBody("text/plain".toMediaTypeOrNull())
                            api.pujarFitxer(jwtToken, pathBody, element.part, element.mtime)
                        }
                    }
                }
                AppLogger.s("Drive", "Carpeta i subcarpetes pujades correctament")
                obrirCarpeta(_rutaActual.value)
            } catch (e: Exception) {
                AppLogger.e("Drive", "Error pujant estructura: ${e.message}")
                e.printStackTrace()
            } finally {
                _uploadState.value = UploadUiState(isUploading = false)
            }
        }
    }
    fun iniciarBackupMedia(
        context: Context,
        incloureFotos: Boolean,
        incloureVideos: Boolean,
        incloureAudios: Boolean,
        incloureDocuments: Boolean,
        incloureContactes: Boolean,
        incloureSms: Boolean
    ) {
        backupJob?.cancel()
        createdFoldersCache.clear()
        backupJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val appContext = context.applicationContext
                if (!incloureFotos && !incloureVideos && !incloureAudios &&
                    !incloureDocuments && !incloureContactes && !incloureSms
                ) {
                    _backupState.value = BackupUiState(
                        isRunning = false,
                        message = "No has seleccionat cap tipus de fitxer per al backup."
                    )
                    return@launch
                }
                val mediaItems = mutableListOf<BackupItem>()
                val documentItems = mutableListOf<BackupDocumentItem>()
                if (incloureFotos) mediaItems += obtenirImatges(appContext)
                if (incloureVideos) mediaItems += obtenirVideos(appContext)
                if (incloureAudios) mediaItems += obtenirAudios(appContext)
                if (incloureDocuments) {
                    documentItems += obtenirDocumentsAutomatics()
                }
                val contactes = if (incloureContactes) obtenirContactesPerBackup(appContext) else emptyList()
                val sms = if (incloureSms) obtenirSmsPerBackup(appContext) else emptyList()
                val contactesBytes = if (incloureContactes) gson.toJson(contactes).toByteArray().size.toLong() else 0L
                val smsBytes = if (incloureSms) gson.toJson(sms).toByteArray().size.toLong() else 0L
                val totalItems =
                    mediaItems.size +
                            documentItems.size +
                            (if (incloureContactes) 1 else 0) +
                            (if (incloureSms) 1 else 0)
                val totalBytes =
                    mediaItems.sumOf { it.sizeBytes } +
                            documentItems.sumOf { it.sizeBytes } +
                            contactesBytes +
                            smsBytes
                if (totalItems == 0) {
                    _backupState.value = BackupUiState(
                        isRunning = false,
                        message = "No s'han trobat fitxers dels tipus seleccionats."
                    )
                    return@launch
                }
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val backupCreatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
                val deviceFolder = sanitize("${Build.MANUFACTURER}_${Build.MODEL}")
                val backupFolder = "backup_$timestamp"
                val destinationRoot = "backups/mobil/$deviceFolder/$backupFolder"
                _backupState.value = BackupUiState(
                    isRunning = true,
                    currentPhase = "Preparant backup...",
                    totalItems = totalItems,
                    totalBytes = totalBytes,
                    destinationFolder = destinationRoot,
                    message = "Cercant fitxers...",
                    error = null
                )
                val startMs = System.currentTimeMillis()
                var uploadedItems = 0
                var uploadedBytes = 0L
                fun actualitzarProgress(nomActual: String, bytesAfegits: Long) {
                    uploadedItems++
                    uploadedBytes += bytesAfegits
                    val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1)
                    val speedBytesPerSec = uploadedBytes * 1000.0 / elapsedMs
                    val remainingBytes = (totalBytes - uploadedBytes).coerceAtLeast(0L)
                    val eta = if (speedBytesPerSec > 0.0) {
                        (remainingBytes / speedBytesPerSec).toLong()
                    } else {
                        null
                    }
                    _backupState.value = _backupState.value.copy(
                        uploadedItems = uploadedItems,
                        uploadedBytes = uploadedBytes,
                        progress = if (totalBytes > 0L) {
                            uploadedBytes.toFloat() / totalBytes.toFloat()
                        } else {
                            uploadedItems.toFloat() / totalItems.toFloat()
                        },
                        etaSeconds = eta,
                        currentFileName = nomActual,
                        message = "Pujat $uploadedItems de $totalItems"
                    )
                }
                for (item in mediaItems) {
                    val subfolder = when (item.tipus) {
                        "photos" -> "photos"
                        "videos" -> "videos"
                        else -> "audio"
                    }
                    val targetPath = "$destinationRoot/$subfolder"
                    ensureRemotePathExists(targetPath)
                    val storedName = "${item.id}_${sanitize(item.nom)}"
                    _backupState.value = _backupState.value.copy(
                        currentPhase = "Pujant ${item.tipus}...",
                        currentFileName = item.nom,
                        message = "Pujant ${item.nom}"
                    )
                    pujarUriOriginal(appContext, item, storedName, targetPath)
                    val sidecar = BackupMetadataSidecar(
                        originalId = item.id,
                        originalName = item.nom,
                        storedName = storedName,
                        mimeType = item.mimeType,
                        tipus = item.tipus,
                        sizeBytes = item.sizeBytes,
                        dateTaken = item.dateTaken,
                        dateAdded = item.dateAdded,
                        dateModified = item.dateModified,
                        sourceUri = item.uri.toString(),
                        backupCreatedAt = backupCreatedAt
                    )
                    pujarMetadataJson(appContext, sidecar, "$storedName.metadata.json", targetPath)
                    actualitzarProgress(item.nom, item.sizeBytes)
                }
                for (doc in documentItems) {
                    val targetPath = if (doc.relativeFolder.isBlank()) {
                        "$destinationRoot/documents"
                    } else {
                        "$destinationRoot/documents/${doc.relativeFolder}"
                    }
                    ensureRemotePathExists(targetPath)
                    _backupState.value = _backupState.value.copy(
                        currentPhase = "Pujant documents...",
                        currentFileName = doc.nom,
                        message = "Pujant ${doc.nom}"
                    )
                    pujarDocumentOriginal(
                        context = appContext,
                        item = doc,
                        storedName = sanitize(doc.nom),
                        targetPath = targetPath
                    )
                    actualitzarProgress(doc.nom, doc.sizeBytes)
                }
                if (incloureContactes) {
                    _backupState.value = _backupState.value.copy(
                        currentPhase = "Exportant contactes...",
                        currentFileName = "contacts.json",
                        message = "Pujant contactes"
                    )
                    val targetPath = "$destinationRoot/contacts"
                    ensureRemotePathExists(targetPath)
                    pujarJsonGenerat(
                        context = appContext,
                        anyData = contactes,
                        fileName = "contacts.json",
                        targetPath = targetPath
                    )
                    actualitzarProgress("contacts.json", contactesBytes)
                }
                if (incloureSms) {
                    _backupState.value = _backupState.value.copy(
                        currentPhase = "Exportant SMS...",
                        currentFileName = "sms.json",
                        message = "Pujant SMS"
                    )
                    val targetPath = "$destinationRoot/sms"
                    ensureRemotePathExists(targetPath)
                    pujarJsonGenerat(
                        context = appContext,
                        anyData = sms,
                        fileName = "sms.json",
                        targetPath = targetPath
                    )
                    actualitzarProgress("sms.json", smsBytes)
                }
                _backupState.value = _backupState.value.copy(
                    isRunning = false,
                    currentPhase = "Completat",
                    progress = 1f,
                    etaSeconds = 0,
                    message = "Backup completat correctament."
                )
                AppLogger.s("Backup", "Backup manual completat amb èxit.")
            } catch (e: CancellationException) {
                _backupState.value = _backupState.value.copy(
                    isRunning = false,
                    currentPhase = "Aturat",
                    message = "Backup cancel·lat."
                )
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _backupState.value = _backupState.value.copy(
                    isRunning = false,
                    error = e.message ?: "Error desconegut durant el backup",
                    currentPhase = "Error"
                )
                AppLogger.e("Backup", "Error en backup manual: ${e.message}")
            }
        }
    }
    fun cancelBackup() {
        backupJob?.cancel()
        backupJob = null
        _backupState.value = _backupState.value.copy(
            isRunning = false,
            currentPhase = "Aturat",
            message = "Backup cancel·lat."
        )
    }
    private val createdFoldersCache = mutableSetOf<String>()
    private suspend fun ensureRemotePathExists(fullPath: String) {
        if (createdFoldersCache.contains(fullPath)) return
        val parts = fullPath.split("/").filter { it.isNotBlank() }
        var current = ""
        for (i in parts.indices) {
            val part = parts[i]
            val pathRelatiu = current
            val fullCurrent = if (pathRelatiu.isEmpty()) part else "$pathRelatiu/$part"
            if (!createdFoldersCache.contains(fullCurrent)) {
                val allowReuse = i != 3 
                crearCarpetaSiNoExiste(pathRelatiu, part, allowReuse)
                createdFoldersCache.add(fullCurrent)
            }
            current = fullCurrent
        }
    }
    private suspend fun crearCarpetaSiNoExiste(path: String, nom: String, allowReuse: Boolean = true) {
        try {
            api.crearCarpeta(jwtToken, MkdirRequest(nom_carpeta = nom, path = path, allow_reuse = allowReuse))
        } catch (e: HttpException) {
        }
    }
    private suspend fun pujarUriOriginal(
        context: Context,
        item: BackupItem,
        storedName: String,
        targetPath: String
    ) {
        val tempFile = copyUriToTempFile(context, item.uri, storedName)
        try {
            val mediaType = item.mimeType.toMediaTypeOrNull()
                ?: "application/octet-stream".toMediaTypeOrNull()
            val requestFile = tempFile.asRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
            val pathBody = targetPath.toRequestBody("text/plain".toMediaTypeOrNull())
            val mtime = item.dateModified?.let { it / 1000f }
            api.pujarFitxer(jwtToken, pathBody, part, mtime)
        } finally {
            tempFile.delete()
        }
    }
    private suspend fun pujarMetadataJson(
        context: Context,
        sidecar: BackupMetadataSidecar,
        fileName: String,
        targetPath: String
    ) {
        val tempFile = File(context.cacheDir, fileName)
        tempFile.writeText(gson.toJson(sidecar), Charsets.UTF_8)
        try {
            val requestFile = tempFile.asRequestBody("application/json".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
            val pathBody = targetPath.toRequestBody("text/plain".toMediaTypeOrNull())
            api.pujarFitxer(jwtToken, pathBody, part)
        } finally {
            tempFile.delete()
        }
    }
    private fun copyUriToTempFile(context: Context, uri: Uri, fileName: String): File {
        val tempFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("No s'ha pogut obrir el fitxer: $uri")
        return tempFile
    }
    private fun sanitize(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
    private fun obtenirImatges(context: Context): List<BackupItem> {
        val resultat = mutableListOf<BackupItem>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.ImageColumns.DATE_TAKEN
        )
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_TAKEN)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val nom = cursor.getString(nameCol) ?: "sense_nom"
                val mime = cursor.getString(mimeCol) ?: "image/*"
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(addedCol)
                val dateModified = cursor.getLong(modifiedCol)
                val dateTaken = if (!cursor.isNull(takenCol)) cursor.getLong(takenCol) else null
                val uri = ContentUris.withAppendedId(collection, id)
                resultat.add(
                    BackupItem(
                        id = id,
                        uri = uri,
                        nom = nom,
                        mimeType = mime,
                        tipus = "photos",
                        sizeBytes = size,
                        dateTaken = dateTaken,
                        dateAdded = dateAdded,
                        dateModified = dateModified
                    )
                )
            }
        }
        return resultat
    }
    private fun obtenirVideos(context: Context): List<BackupItem> {
        val resultat = mutableListOf<BackupItem>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.VideoColumns.DATE_TAKEN
        )
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATE_TAKEN)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val nom = cursor.getString(nameCol) ?: "sense_nom"
                val mime = cursor.getString(mimeCol) ?: "video/*"
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(addedCol)
                val dateModified = cursor.getLong(modifiedCol)
                val dateTaken = if (!cursor.isNull(takenCol)) cursor.getLong(takenCol) else null
                val uri = ContentUris.withAppendedId(collection, id)
                resultat.add(
                    BackupItem(
                        id = id,
                        uri = uri,
                        nom = nom,
                        mimeType = mime,
                        tipus = "videos",
                        sizeBytes = size,
                        dateTaken = dateTaken,
                        dateAdded = dateAdded,
                        dateModified = dateModified
                    )
                )
            }
        }
        return resultat
    }
    private fun obtenirAudios(context: Context): List<BackupItem> {
        val resultat = mutableListOf<BackupItem>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val nom = cursor.getString(nameCol) ?: "sense_nom"
                val mime = cursor.getString(mimeCol) ?: "audio/*"
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(addedCol)
                val dateModified = cursor.getLong(modifiedCol)
                val uri = ContentUris.withAppendedId(collection, id)
                resultat.add(
                    BackupItem(
                        id = id,
                        uri = uri,
                        nom = nom,
                        mimeType = mime,
                        tipus = "audio",
                        sizeBytes = size,
                        dateTaken = null,
                        dateAdded = dateAdded,
                        dateModified = dateModified
                    )
                )
            }
        }
        return resultat
    }
    private suspend fun pujarJsonGenerat(
        context: Context,
        anyData: Any,
        fileName: String,
        targetPath: String
    ) {
        val tempFile = File(context.cacheDir, fileName)
        tempFile.writeText(gson.toJson(anyData), Charsets.UTF_8)
        try {
            val requestFile = tempFile.asRequestBody("application/json".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
            val pathBody = targetPath.toRequestBody("text/plain".toMediaTypeOrNull())
            api.pujarFitxer(jwtToken, pathBody, part)
        } finally {
            tempFile.delete()
        }
    }
    private suspend fun pujarDocumentOriginal(
        context: Context,
        item: BackupDocumentItem,
        storedName: String,
        targetPath: String
    ) {
        val tempFile = copyUriToTempFile(context, item.uri, storedName)
        try {
            val mediaType = item.mimeType.toMediaTypeOrNull()
                ?: "application/octet-stream".toMediaTypeOrNull()
            val requestFile = tempFile.asRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
            val pathBody = targetPath.toRequestBody("text/plain".toMediaTypeOrNull())
            api.pujarFitxer(jwtToken, pathBody, part)
        } finally {
            tempFile.delete()
        }
    }
    private fun recollirDocumentsRecursivament(
        carpeta: DocumentFile,
        relativeFolder: String,
        resultat: MutableList<BackupDocumentItem>
    ) {
        carpeta.listFiles().forEach { file ->
            if (file.isDirectory) {
                val nextFolder = if (relativeFolder.isBlank()) {
                    file.name ?: "carpeta"
                } else {
                    "$relativeFolder/${file.name ?: "carpeta"}"
                }
                recollirDocumentsRecursivament(file, nextFolder, resultat)
            } else if (file.isFile) {
                val name = file.name ?: return@forEach
                val mime = file.type ?: "application/octet-stream"
                if (esDocumentCompatible(name, mime)) {
                    resultat.add(
                        BackupDocumentItem(
                            uri = file.uri,
                            nom = name,
                            mimeType = mime,
                            sizeBytes = file.length(),
                            relativeFolder = relativeFolder
                        )
                    )
                }
            }
        }
    }
    private fun esDocumentCompatible(name: String, mime: String): Boolean {
        val lowerName = name.lowercase(Locale.getDefault())
        val lowerMime = mime.lowercase(Locale.getDefault())
        return lowerName.endsWith(".pdf") ||
                lowerName.endsWith(".doc") ||
                lowerName.endsWith(".docx") ||
                lowerName.endsWith(".xls") ||
                lowerName.endsWith(".xlsx") ||
                lowerName.endsWith(".ppt") ||
                lowerName.endsWith(".pptx") ||
                lowerName.endsWith(".txt") ||
                lowerName.endsWith(".rtf") ||
                lowerName.endsWith(".odt") ||
                lowerName.endsWith(".ods") ||
                lowerName.endsWith(".odp") ||
                lowerName.endsWith(".csv") ||
                lowerName.endsWith(".zip") ||
                lowerName.endsWith(".rar") ||
                lowerName.endsWith(".7z") ||
                lowerName.endsWith(".tar") ||
                lowerName.endsWith(".gz") ||
                lowerName.endsWith(".tgz") ||
                lowerName.endsWith(".bz2") ||
                lowerName.endsWith(".xz") ||
                lowerName.endsWith(".cab") ||
                lowerMime == "application/pdf" ||
                lowerMime == "application/msword" ||
                lowerMime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                lowerMime == "application/vnd.ms-excel" ||
                lowerMime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
                lowerMime == "application/vnd.ms-powerpoint" ||
                lowerMime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" ||
                lowerMime == "text/plain" ||
                lowerMime == "text/csv" ||
                lowerMime == "application/rtf" ||
                lowerMime == "application/vnd.oasis.opendocument.text" ||
                lowerMime == "application/vnd.oasis.opendocument.spreadsheet" ||
                lowerMime == "application/vnd.oasis.opendocument.presentation" ||
                lowerMime == "application/zip" ||
                lowerMime == "application/gzip" ||
                lowerMime == "application/x-tar" ||
                lowerMime == "application/x-rar-compressed" ||
                lowerMime == "application/vnd.rar" ||
                lowerMime == "application/x-7z-compressed" ||
                lowerMime == "application/x-bzip2" ||
                lowerMime == "application/vnd.ms-cab-compressed"
    }
    private fun obtenirContactesPerBackup(context: Context): List<ContactBackupEntry> {
        val map = linkedMapOf<Long, MutableContactBuilder>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ),
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Sense nom"
                map[id] = MutableContactBuilder(id = id, displayName = name)
            }
        }
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val phoneCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val phone = cursor.getString(phoneCol) ?: continue
                val builder = map.getOrPut(id) {
                    MutableContactBuilder(id = id, displayName = "Sense nom")
                }
                builder.phones.add(phone)
            }
        }
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val emailCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val email = cursor.getString(emailCol) ?: continue
                val builder = map.getOrPut(id) {
                    MutableContactBuilder(id = id, displayName = "Sense nom")
                }
                builder.emails.add(email)
            }
        }
        return map.values.map {
            ContactBackupEntry(
                contactId = it.id,
                displayName = it.displayName,
                phones = it.phones.distinct(),
                emails = it.emails.distinct()
            )
        }
    }
    private fun obtenirSmsPerBackup(context: Context): List<SmsBackupEntry> {
        val resultat = mutableListOf<SmsBackupEntry>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            ),
            null,
            null,
            Telephony.Sms.DEFAULT_SORT_ORDER
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeCol = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val readCol = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            while (cursor.moveToNext()) {
                resultat.add(
                    SmsBackupEntry(
                        smsId = cursor.getLong(idCol),
                        address = cursor.getString(addressCol),
                        body = cursor.getString(bodyCol),
                        date = cursor.getLong(dateCol),
                        type = cursor.getInt(typeCol),
                        read = cursor.getInt(readCol)
                    )
                )
            }
        }
        return resultat
    }
    private data class MutableContactBuilder(
        val id: Long,
        val displayName: String,
        val phones: MutableList<String> = mutableListOf(),
        val emails: MutableList<String> = mutableListOf()
    )
    private fun obtenirDocumentsAutomatics(): List<BackupDocumentItem> {
        val resultat = mutableListOf<BackupDocumentItem>()
        val root = Environment.getExternalStorageDirectory()
        if (!root.exists() || !root.canRead()) return emptyList()
        val excludedPaths = listOf(
            File(root, "Android/data").absolutePath,
            File(root, "Android/obb").absolutePath,
            File(root, "Android/media").absolutePath
        )
        root.walkTopDown()
            .onEnter { dir ->
                val path = dir.absolutePath
                excludedPaths.none { path.startsWith(it) }
            }
            .filter { it.isFile }
            .forEach { file ->
                val mime = guessMimeType(file.name)
                if (esDocumentCompatible(file.name, mime)) {
                    val relativeFolder = file.parentFile
                        ?.absolutePath
                        ?.removePrefix(root.absolutePath)
                        ?.trimStart(File.separatorChar)
                        ?.replace(File.separatorChar, '/')
                        ?: ""
                    resultat.add(
                        BackupDocumentItem(
                            uri = Uri.fromFile(file),
                            nom = file.name,
                            mimeType = mime,
                            sizeBytes = file.length(),
                            relativeFolder = relativeFolder
                        )
                    )
                }
            }
        return resultat
    }
    private fun esDocumentCompatible(name: String): Boolean {
        val lower = name.lowercase(Locale.getDefault())
        return lower.endsWith(".pdf") ||
                lower.endsWith(".doc") ||
                lower.endsWith(".docx") ||
                lower.endsWith(".xls") ||
                lower.endsWith(".xlsx") ||
                lower.endsWith(".ppt") ||
                lower.endsWith(".pptx") ||
                lower.endsWith(".txt") ||
                lower.endsWith(".rtf") ||
                lower.endsWith(".odt") ||
                lower.endsWith(".ods") ||
                lower.endsWith(".odp") ||
                lower.endsWith(".csv")
    }
    private fun guessMimeType(name: String): String {
        val lower = name.lowercase(Locale.getDefault())
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".doc") -> "application/msword"
            lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lower.endsWith(".xls") -> "application/vnd.ms-excel"
            lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            lower.endsWith(".ppt") -> "application/vnd.ms-powerpoint"
            lower.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            lower.endsWith(".txt") -> "text/plain"
            lower.endsWith(".rtf") -> "application/rtf"
            lower.endsWith(".odt") -> "application/vnd.oasis.opendocument.text"
            lower.endsWith(".ods") -> "application/vnd.oasis.opendocument.spreadsheet"
            lower.endsWith(".odp") -> "application/vnd.oasis.opendocument.presentation"
            lower.endsWith(".csv") -> "text/csv"
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".rar") -> "application/x-rar-compressed"
            lower.endsWith(".7z") -> "application/x-7z-compressed"
            lower.endsWith(".tar") -> "application/x-tar"
            lower.endsWith(".gz") -> "application/gzip"
            lower.endsWith(".tgz") -> "application/gzip"
            lower.endsWith(".bz2") -> "application/x-bzip2"
            lower.endsWith(".xz") -> "application/x-xz"
            lower.endsWith(".cab") -> "application/vnd.ms-cab-compressed"
            else -> "application/octet-stream"
        }
    }
}
