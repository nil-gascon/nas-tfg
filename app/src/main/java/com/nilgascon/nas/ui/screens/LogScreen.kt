package com.nilgascon.nas.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nilgascon.nas.model.LogEntry
import com.nilgascon.nas.utils.AppLogger
import com.nilgascon.nas.utils.LogLevel
import com.nilgascon.nas.viewmodel.NasViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(viewModel: NasViewModel, onGoBack: () -> Unit) {
    val localLogs by AppLogger.logs.collectAsState()
    val remoteLogs by viewModel.logsRemots.collectAsState()
    var selectedLevel by remember { mutableStateOf<String?>(null) }
    var tabIndex by remember { mutableIntStateOf(0) }
    val currentList = if (tabIndex == 0) localLogs else remoteLogs
    val filteredLogs = if (selectedLevel == null) currentList else currentList.filter { it.level == selectedLevel }
    LaunchedEffect(tabIndex) {
        if (tabIndex == 1) viewModel.carregarLogsRemots()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registres del Sistema", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onGoBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Enrere") }
                },
                actions = {
                    IconButton(onClick = { if (tabIndex == 1) viewModel.carregarLogsRemots() }) {
                        Icon(Icons.Default.Refresh, "Actualitzar")
                    }
                    var showFilter by remember { mutableStateOf(false) }
                    IconButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.FilterList, "Filtrar")
                    }
                    DropdownMenu(expanded = showFilter, onDismissRequest = { showFilter = false }) {
                        DropdownMenuItem(text = { Text("Tots") }, onClick = { selectedLevel = null; showFilter = false })
                        LogLevel.entries.forEach { level ->
                            DropdownMenuItem(text = { Text(level.name) }, onClick = { selectedLevel = level.name; showFilter = false })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Dispositiu") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("NAS (Centralitzat)") })
            }
            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hi ha registres per mostrar", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs) { entry ->
                        LogItem(entry)
                    }
                }
            }
        }
    }
}
@Composable
fun LogItem(entry: LogEntry) {
    val color = when (entry.level) {
        "INFO" -> MaterialTheme.colorScheme.primary
        "SUCCESS" -> Color(0xFF4CAF50)
        "WARNING" -> Color(0xFFFF9800)
        "ERROR" -> MaterialTheme.colorScheme.error
        "BACKUP_AUTO" -> Color(0xFF9C27B0) 
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = entry.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(color = color, shape = RoundedCornerShape(4.dp)) {
                    Text(text = entry.level, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = entry.tag, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = entry.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
