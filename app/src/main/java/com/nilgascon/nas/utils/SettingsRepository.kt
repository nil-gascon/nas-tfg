package com.nilgascon.nas.utils
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nilgascon.nas.model.NasConnection
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nas_settings", Context.MODE_PRIVATE)
    private val gson = Gson()
    var isAutoBackupEnabled: Boolean
        get() = prefs.getBoolean("auto_backup_enabled", false)
        set(value) = prefs.edit().putBoolean("auto_backup_enabled", value).apply()
    var backupIntervalHours: Int
        get() = prefs.getInt("backup_interval_hours", 24)
        set(value) = prefs.edit().putInt("backup_interval_hours", value).apply()
    var backupUnit: String 
        get() = prefs.getString("backup_unit", "HOURS") ?: "HOURS"
        set(value) = prefs.edit().putString("backup_unit", value).apply()
    var backupValue: Int
        get() = prefs.getInt("backup_value", 24)
        set(value) = prefs.edit().putInt("backup_value", value).apply()
    var autoBackupPhotos: Boolean
        get() = prefs.getBoolean("auto_backup_photos", true)
        set(value) = prefs.edit().putBoolean("auto_backup_photos", value).apply()
    var autoBackupVideos: Boolean
        get() = prefs.getBoolean("auto_backup_videos", true)
        set(value) = prefs.edit().putBoolean("auto_backup_videos", value).apply()
    var autoBackupAudio: Boolean
        get() = prefs.getBoolean("auto_backup_audio", false)
        set(value) = prefs.edit().putBoolean("auto_backup_audio", value).apply()
    var autoBackupDocs: Boolean
        get() = prefs.getBoolean("auto_backup_docs", false)
        set(value) = prefs.edit().putBoolean("auto_backup_docs", value).apply()
    var autoBackupContacts: Boolean
        get() = prefs.getBoolean("auto_backup_contacts", false)
        set(value) = prefs.edit().putBoolean("auto_backup_contacts", value).apply()
    var autoBackupSms: Boolean
        get() = prefs.getBoolean("auto_backup_sms", false)
        set(value) = prefs.edit().putBoolean("auto_backup_sms", value).apply()
    var notifyOnSuccess: Boolean
        get() = prefs.getBoolean("notify_on_success", true)
        set(value) = prefs.edit().putBoolean("notify_on_success", value).apply()
    var notifyOnError: Boolean
        get() = prefs.getBoolean("notify_on_error", true)
        set(value) = prefs.edit().putBoolean("notify_on_error", value).apply()
    var lastBackupTimestamp: Long
        get() = prefs.getLong("last_backup_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_backup_timestamp", value).apply()
    var lastBackupSuccess: Boolean
        get() = prefs.getBoolean("last_backup_success", true)
        set(value) = prefs.edit().putBoolean("last_backup_success", value).apply()
    var lastBackupMessage: String?
        get() = prefs.getString("last_backup_message", null)
        set(value) = prefs.edit().putString("last_backup_message", value).apply()
    fun getAuthToken(): String? = prefs.getString("auth_token", null)
    fun setAuthToken(token: String) = prefs.edit().putString("auth_token", token).apply()
    fun getUserRole(): String = prefs.getString("user_role", "user") ?: "user"
    fun setUserRole(role: String) = prefs.edit().putString("user_role", role).apply()
    fun getSavedConnections(): List<NasConnection> {
        val json = prefs.getString("saved_nas_list", null) ?: return emptyList()
        val type = object : TypeToken<List<NasConnection>>() {}.type
        return gson.fromJson(json, type)
    }
    fun saveConnections(list: List<NasConnection>) {
        val json = gson.toJson(list)
        prefs.edit().putString("saved_nas_list", json).apply()
    }
    var activeNasId: String?
        get() = prefs.getString("active_nas_id", null)
        set(value) = prefs.edit().putString("active_nas_id", value).apply()
    fun getActiveConnection(): NasConnection? {
        val list = getSavedConnections()
        return list.find { it.id == activeNasId } ?: list.firstOrNull()
    }
}
