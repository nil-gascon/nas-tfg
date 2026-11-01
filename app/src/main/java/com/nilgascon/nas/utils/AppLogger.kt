package com.nilgascon.nas.utils
import com.nilgascon.nas.model.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
enum class LogLevel { INFO, SUCCESS, WARNING, ERROR, BACKUP_AUTO }
object AppLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs
    private val pendingLogs = mutableListOf<LogEntry>()
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            level = level.name,
            tag = tag,
            message = message
        )
        _logs.value = (listOf(entry) + _logs.value).take(500)
        synchronized(pendingLogs) {
            pendingLogs.add(entry)
        }
    }
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun s(tag: String, message: String) = log(LogLevel.SUCCESS, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARNING, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)
    fun ba(tag: String, message: String) = log(LogLevel.BACKUP_AUTO, tag, message)
    fun getPendingLogs(): List<LogEntry> {
        return synchronized(pendingLogs) {
            val list = pendingLogs.toList()
            list
        }
    }
    fun clearPendingLogs(synced: List<LogEntry>) {
        synchronized(pendingLogs) {
            pendingLogs.removeAll(synced)
        }
    }
}
