package com.nilgascon.nas.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nilgascon.nas.ui.theme.InfoStatItem
import com.nilgascon.nas.ui.theme.PremiumCard
import com.nilgascon.nas.ui.theme.SectionHeader
import com.nilgascon.nas.viewmodel.NasViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: NasViewModel,
    onOpenDrive: () -> Unit,
    onOpenSystemDetails: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenUserManagement: () -> Unit = {}
) {
    val status by viewModel.status.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val isUserAdmin = userRole == "admin"
    LaunchedEffect(Unit) {
        viewModel.iniciarAutoRefreshDashboard()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.aturarAutoRefreshDashboard()
        }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("El meu NAS", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Tancar sessió")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            if (isUserAdmin) {
                SectionHeader("Estat del Sistema")
                PremiumCard(onClick = onOpenSystemDetails) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        if (status == null) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Carregant estadístiques...")
                        } else {
                            val estat = status!!
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Rendiment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            InfoStatItem("CPU", "${estat.cpu_percent}%")
                            InfoStatItem("RAM", "${estat.ram_percent}%")
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Disc (${estat.percentatge_usat}%)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (estat.percentatge_usat / 100).toFloat() },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                strokeCap = StrokeCap.Round
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Toca per veure detalls de temperatura i xarxa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            SectionHeader("Serveis")
            if (isUserAdmin) {
                PremiumCard(onClick = onOpenDrive) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Gestor de Fitxers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Accés remot als teus fitxers", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            PremiumCard(onClick = onOpenBackup) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CloudUpload, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Còpia de Seguretat i Automatització", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Protegeix les teves fotos i dades", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
            if (isUserAdmin) {
                Spacer(modifier = Modifier.height(12.dp))
                PremiumCard(onClick = onOpenLogs) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.List, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Registres de l'App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Comprova que tot funcioni bé", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Seguretat i Usuaris")
                PremiumCard(onClick = onOpenUserManagement) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ManageAccounts, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Administració d'Usuaris", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Crea, elimina i gestiona permisos", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
