package com.nilgascon.nas.ui.screens
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nilgascon.nas.ui.theme.PremiumCard
import com.nilgascon.nas.ui.theme.SectionHeader
import com.nilgascon.nas.viewmodel.NasViewModel
import java.text.SimpleDateFormat
import java.util.*
data class LocalBackupConfig(
    val enabled: Boolean,
    val value: String,
    val unit: String,
    val photos: Boolean,
    val videos: Boolean,
    val audio: Boolean,
    val docs: Boolean,
    val contacts: Boolean,
    val sms: Boolean,
    val notifySuccess: Boolean,
    val notifyError: Boolean
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: NasViewModel,
    onGoBack: () -> Unit
) {
    val context = LocalContext.current
    val backupState by viewModel.backupState.collectAsState()
    val settings = viewModel.getSettings()
    fun loadCurrentConfig() = LocalBackupConfig(
        enabled = settings.isAutoBackupEnabled,
        value = settings.backupValue.toString(),
        unit = settings.backupUnit,
        photos = settings.autoBackupPhotos,
        videos = settings.autoBackupVideos,
        audio = settings.autoBackupAudio,
        docs = settings.autoBackupDocs,
        contacts = settings.autoBackupContacts,
        sms = settings.autoBackupSms,
        notifySuccess = settings.notifyOnSuccess,
        notifyError = settings.notifyOnError
    )
    var currentConfig by remember { mutableStateOf(loadCurrentConfig()) }
    var editEnabled by remember { mutableStateOf(currentConfig.enabled) }
    var editValue by remember { mutableStateOf(currentConfig.value) }
    var editUnit by remember { mutableStateOf(currentConfig.unit) }
    var editPhotos by remember { mutableStateOf(currentConfig.photos) }
    var editVideos by remember { mutableStateOf(currentConfig.videos) }
    var editAudio by remember { mutableStateOf(currentConfig.audio) }
    var editDocs by remember { mutableStateOf(currentConfig.docs) }
    var editContacts by remember { mutableStateOf(currentConfig.contacts) }
    var editSms by remember { mutableStateOf(currentConfig.sms) }
    var editNotifySuccess by remember { mutableStateOf(currentConfig.notifySuccess) }
    var editNotifyError by remember { mutableStateOf(currentConfig.notifyError) }
    val hasChanges = editEnabled != currentConfig.enabled ||
            editValue != currentConfig.value ||
            editUnit != currentConfig.unit ||
            editPhotos != currentConfig.photos ||
            editVideos != currentConfig.videos ||
            editAudio != currentConfig.audio ||
            editDocs != currentConfig.docs ||
            editContacts != currentConfig.contacts ||
            editSms != currentConfig.sms ||
            editNotifySuccess != currentConfig.notifySuccess ||
            editNotifyError != currentConfig.notifyError
    val criticalPermissions = mutableListOf<String>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_SMS)
    }
    val optionalPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else emptyList()
    fun checkAllCriticalGranted(ctx: Context): Boolean {
        return criticalPermissions.all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    var manualPhotos by remember { mutableStateOf(true) }
    var manualVideos by remember { mutableStateOf(true) }
    var manualAudio by remember { mutableStateOf(false) }
    var manualDocs by remember { mutableStateOf(false) }
    var manualContacts by remember { mutableStateOf(false) }
    var manualSms by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allCriticalOk = criticalPermissions.all { perms[it] == true }
        if (allCriticalOk) {
            viewModel.iniciarBackupMedia(context, manualPhotos, manualVideos, manualAudio, manualDocs, manualContacts, manualSms)
        } else {
            Toast.makeText(context, "Calen els permisos de fitxers i contactes per fer la còpia.", Toast.LENGTH_LONG).show()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Còpia de Seguretat", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onGoBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Enrere") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            AnimatedVisibility(visible = backupState.isRunning) {
                RunningBackupCard(backupState) { viewModel.cancelBackup() }
            }
            if (!backupState.isRunning) {
                SectionHeader("Còpia Automàtica")
                PremiumCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Servei en segon pla", fontWeight = FontWeight.Bold)
                                StatusBadge(
                                    text = if (editEnabled) "ACTIVAT" else "DESACTIVAT",
                                    color = if (editEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(checked = editEnabled, onCheckedChange = { editEnabled = it })
                        }
                        AnimatedVisibility(visible = editEnabled) {
                            Column {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("Freqüència", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = editValue,
                                        onValueChange = { 
                                            val filtered = it.filter { c -> c.isDigit() }
                                            editValue = filtered
                                        },
                                        modifier = Modifier.width(85.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    var showUnits by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedCard(onClick = { showUnits = true }, shape = RoundedCornerShape(12.dp)) {
                                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(when(editUnit) { "MINUTES" -> "Minuts"; "HOURS" -> "Hores"; "DAYS" -> "Dies"; else -> "Hores" }, modifier = Modifier.weight(1f))
                                                Icon(Icons.Default.ArrowDropDown, null)
                                            }
                                        }
                                        DropdownMenu(expanded = showUnits, onDismissRequest = { showUnits = false }) {
                                            DropdownMenuItem(text = { Text("Minuts") }, onClick = { editUnit = "MINUTES"; showUnits = false })
                                            DropdownMenuItem(text = { Text("Hores") }, onClick = { editUnit = "HOURS"; showUnits = false })
                                            DropdownMenuItem(text = { Text("Dies") }, onClick = { editUnit = "DAYS"; showUnits = false })
                                        }
                                    }
                                }
                                if (editUnit == "MINUTES") {
                                    val v = editValue.toIntOrNull() ?: 0
                                    if (v < 15) {
                                        Text(
                                            "El sistema Android aplicarà el mínim de 15 minuts.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("Contingut automàtic", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                                    FilterChip(selected = editPhotos, onClick = { editPhotos = !editPhotos }, label = { Text("Fotos") })
                                    FilterChip(selected = editVideos, onClick = { editVideos = !editVideos }, label = { Text("Vídeos") })
                                    FilterChip(selected = editAudio, onClick = { editAudio = !editAudio }, label = { Text("Àudio") })
                                    FilterChip(selected = editDocs, onClick = { editDocs = !editDocs }, label = { Text("Docs") })
                                    FilterChip(selected = editContacts, onClick = { editContacts = !editContacts }, label = { Text("Contactes") })
                                    FilterChip(selected = editSms, onClick = { editSms = !editSms }, label = { Text("SMS") })
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text("Notificacions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = editNotifySuccess, onCheckedChange = { editNotifySuccess = it })
                                    Text("Notifica èxit", style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Checkbox(checked = editNotifyError, onCheckedChange = { editNotifyError = it })
                                    Text("Notifica error", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        AnimatedVisibility(visible = hasChanges, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Column {
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { 
                                        viewModel.programarBackupAutomatic(
                                            editEnabled, editValue.toIntOrNull() ?: 24, editUnit,
                                            editPhotos, editVideos, editAudio, editDocs, editContacts, editSms
                                        )
                                        settings.notifyOnSuccess = editNotifySuccess
                                        settings.notifyOnError = editNotifyError
                                        currentConfig = loadCurrentConfig()
                                        Toast.makeText(context, "Configuració aplicada!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Save, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("APLICAR CANVIS PENDENTS", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (!hasChanges && editEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(color = Color(0xFF4CAF50).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Configuració guardada i activa", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                                }
                            }
                        }
                        if (settings.lastBackupTimestamp > 0L) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                            LastInfoRow(settings)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Còpia Manual")
                PremiumCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Selecciona què vols copiar ara", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        BackupToggle("Fotos", Icons.Default.Image, manualPhotos) { manualPhotos = it }
                        BackupToggle("Vídeos", Icons.Default.VideoLibrary, manualVideos) { manualVideos = it }
                        BackupToggle("Àudio", Icons.Default.Audiotrack, manualAudio) { manualAudio = it }
                        BackupToggle("Documents", Icons.Default.Description, manualDocs) { manualDocs = it }
                        BackupToggle("Contactes", Icons.Default.Contacts, manualContacts) { manualContacts = it }
                        BackupToggle("SMS", Icons.Default.Sms, manualSms) { manualSms = it }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { 
                                if (checkAllCriticalGranted(context)) {
                                    viewModel.iniciarBackupMedia(context, manualPhotos, manualVideos, manualAudio, manualDocs, manualContacts, manualSms)
                                } else {
                                    permissionLauncher.launch((criticalPermissions + optionalPermissions).toTypedArray())
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Backup, null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("FER CÒPIA ARA", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(text = text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
@Composable
fun RunningBackupCard(state: com.nilgascon.nas.model.BackupUiState, onCancel: () -> Unit) {
    PremiumCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                    Text("EN CURS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("${(state.progress * 100).toInt()}%", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(state.currentPhase, fontWeight = FontWeight.Bold)
            Text(state.message, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Spacer(modifier = Modifier.height(20.dp))
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), strokeCap = StrokeCap.Round)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(12.dp)) {
                Text("CANCEL·LAR", fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}
@Composable
fun BackupToggle(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
@Composable
fun FlowRow(modifier: Modifier = Modifier, spacing: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        placeables.forEach { p ->
            if (currentRowWidth + p.width + spacing.roundToPx() > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow); currentRow = mutableListOf(); currentRowWidth = 0
            }
            currentRow.add(p); currentRowWidth += p.width + spacing.roundToPx()
        }
        rows.add(currentRow)
        val height = rows.sumOf { r -> r.maxOf { it.height } + spacing.roundToPx() }
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { r ->
                var x = 0
                r.forEach { p -> p.place(x, y); x += p.width + spacing.roundToPx() }
                y += r.maxOf { it.height } + spacing.roundToPx()
            }
        }
    }
}
@Composable
fun LastInfoRow(settings: com.nilgascon.nas.utils.SettingsRepository) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val success = settings.lastBackupSuccess
        Icon(imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error, contentDescription = null, tint = if (success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            val dateStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(settings.lastBackupTimestamp))
            Text("Últim intent: $dateStr", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(if (success) "Correcte" else (settings.lastBackupMessage ?: "Error"), color = if (success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}
