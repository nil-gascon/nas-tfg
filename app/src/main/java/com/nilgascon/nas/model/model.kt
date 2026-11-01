package com.nilgascon.nas.model
import android.net.Uri
data class LoginResponse(val access_token: String, val token_type: String, val role: String? = "user")
data class SimpleResponse(val estat: String, val missatge: String)
data class MkdirResponse(
    val estat: String,
    val missatge: String,
    val nom_final: String,
    val ruta_completa: String
)
data class LogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String
)
data class ArxiuNas(
    val nom: String,
    val es_carpeta: Boolean,
    val mida_bytes: Long,
    val ruta_completa: String,
    val mtime: Double? = null
)
data class LlistatDirectoriResponse(
    val ruta_actual: String,
    val contingut: List<ArxiuNas>
)
data class NasConnection(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val ip: String,
    val port: String,
    val useHttps: Boolean = true
) {
    fun getBaseUrl(): String {
        val cleanIp = ip.trim().removePrefix("http://").removePrefix("https://").trim('/')
        val cleanPort = port.trim()
        return "https://$cleanIp:$cleanPort"
    }
}
data class StatusResponse(
    val capacitat_total_gb: Double,
    val espai_usat_gb: Double,
    val espai_lliure_gb: Double,
    val percentatge_usat: Double,
    val cpu_percent: Double,
    val ram_percent: Double
)
data class CpuDetails(
    val percent_total: Double,
    val percent_per_core: List<Double>,
    val count_logical: Int,
    val count_physical: Int?,
    val freq_current_mhz: Double?,
    val freq_min_mhz: Double?,
    val freq_max_mhz: Double?
)
data class RamDetails(
    val total_gb: Double,
    val available_gb: Double,
    val used_gb: Double,
    val percent: Double
)
data class SwapDetails(
    val total_gb: Double,
    val used_gb: Double,
    val free_gb: Double,
    val percent: Double
)
data class DiskDetails(
    val capacitat_total_gb: Double,
    val espai_usat_gb: Double,
    val espai_lliure_gb: Double,
    val percentatge_usat: Double
)
data class TemperatureItem(
    val sensor: String,
    val label: String,
    val current: Double?,
    val high: Double?,
    val critical: Double?
)
data class FanItem(
    val sensor: String,
    val label: String,
    val current_rpm: Double?
)
data class SystemDetailsResponse(
    val timestamp: String,
    val disk: DiskDetails,
    val cpu: CpuDetails,
    val ram: RamDetails,
    val swap: SwapDetails? = null,
    val temperatures: List<TemperatureItem>? = null,
    val fans: List<FanItem>? = null
)
data class SetupStatusResponse(val setup_completed: Boolean)
data class UserOut(val username: String, val role: String, val created_at: String)
data class UserCreate(val username: String, val password: String, val role: String = "user")
data class UserUpdate(val username: String? = null, val password: String? = null, val role: String? = null)
data class SetupRequest(val admin_username: String, val admin_password: String)
enum class TestPhase { IDLE, PING, DOWNLOAD, UPLOAD, FINISHED }
data class PerformanceStats(
    val phase: TestPhase = TestPhase.IDLE,
    val currentSpeedMbps: Double = 0.0,
    val maxSpeedMbps: Double = 0.0,
    val downloadFinalMbps: Double = 0.0,
    val uploadFinalMbps: Double = 0.0,
    val latencyMs: Long = 0L,
    val jitterMs: Long = 0L,
    val progress: Float = 0f,
    val history: List<Double> = emptyList(),
    val isRunning: Boolean = false,
    val message: String = ""
)
