package com.nilgascon.nas.ui.screens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nilgascon.nas.model.ArxiuNas
import com.nilgascon.nas.ui.theme.PremiumCard
import com.nilgascon.nas.utils.GestorFitxersUtils
import com.nilgascon.nas.viewmodel.NasViewModel
import java.util.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestorFitxersScreen(
    viewModel: NasViewModel,
    onGoBack: () -> Unit
) {
    val context = LocalContext.current
    val rutaActual by viewModel.rutaActual.collectAsState()
    val contingut by viewModel.contingutDirectori.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    val activeNas by viewModel.activeNas.collectAsState()
    var showMkdirDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<ArxiuNas?>(null) }
    var showUploadMenu by remember { mutableStateOf(false) }
    var nomNovaCarpeta by remember { mutableStateOf("") }
    var nouNom by remember { mutableStateOf("") }
    val multiFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val parts = GestorFitxersUtils.urisAMultipart(context, uris)
            val mtimes = uris.map { GestorFitxersUtils.getMtime(context, it) ?: 0f }
            viewModel.pujarFitxersMultiples(parts, mtimes)
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val fitxers = GestorFitxersUtils.recollirEstructuraCompleta(context, androidx.documentfile.provider.DocumentFile.fromTreeUri(context, it)!!)
            viewModel.pujarEstructuraCompleta(fitxers)
        }
    }
    LaunchedEffect(rutaActual) { viewModel.iniciarAutoRefreshCarpeta(rutaActual) }
    DisposableEffect(Unit) { onDispose { viewModel.aturarAutoRefreshCarpeta() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gestor de Fitxers", fontWeight = FontWeight.Bold)
                        Breadcrumbs(rutaActual) { viewModel.obrirCarpeta(it) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (rutaActual.isEmpty()) onGoBack() else {
                            val parent = if (rutaActual.contains("/")) rutaActual.substringBeforeLast("/") else ""
                            viewModel.obrirCarpeta(parent)
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Enrere") }
                },
                actions = {
                    IconButton(onClick = { showMkdirDialog = true }) { Icon(Icons.Default.CreateNewFolder, "Nova carpeta") }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showUploadMenu = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) { Icon(Icons.Default.Add, "Pujar") }
                DropdownMenu(expanded = showUploadMenu, onDismissRequest = { showUploadMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Pujar fitxers") },
                        leadingIcon = { Icon(Icons.Default.FilePresent, null) },
                        onClick = { showUploadMenu = false; multiFilePicker.launch("*/*") }
                    )
                    DropdownMenuItem(
                        text = { Text("Pujar carpeta") },
                        leadingIcon = { Icon(Icons.Default.DriveFolderUpload, null) },
                        onClick = { showUploadMenu = false; folderPicker.launch(null) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (contingut.isEmpty() && !uploadState.isUploading) {
                EmptyView()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(contingut) { arxiu ->
                        FileItemRow(
                            arxiu = arxiu,
                            onOpen = { if (arxiu.es_carpeta) viewModel.obrirCarpeta(arxiu.ruta_completa) },
                            onDownload = { viewModel.descarregarFitxer(context, arxiu) },
                            onRename = { showRenameDialog = arxiu; nouNom = arxiu.nom },
                            onDelete = { viewModel.esborrarElement(arxiu.ruta_completa) }
                        )
                    }
                }
            }
            if (uploadState.isUploading) {
                UploadProgressCard(uploadState)
            }
        }
    }
    if (showMkdirDialog) {
        NameDialog(
            title = "Nova carpeta",
            value = nomNovaCarpeta,
            onValueChange = { nomNovaCarpeta = it },
            onConfirm = { viewModel.crearCarpeta(nomNovaCarpeta); nomNovaCarpeta = ""; showMkdirDialog = false },
            onDismiss = { showMkdirDialog = false }
        )
    }
    if (showRenameDialog != null) {
        NameDialog(
            title = "Canviar nom",
            value = nouNom,
            onValueChange = { nouNom = it },
            onConfirm = { viewModel.canviarNomElement(showRenameDialog!!.ruta_completa, nouNom); showRenameDialog = null },
            onDismiss = { showRenameDialog = null }
        )
    }
}
@Composable
fun Breadcrumbs(path: String, onNavigate: (String) -> Unit) {
    val scrollState = rememberScrollState()
    LaunchedEffect(path) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "El meu NAS",
            modifier = Modifier.clickable { onNavigate("") },
            style = MaterialTheme.typography.bodySmall,
            color = if (path.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (path.isEmpty()) FontWeight.Bold else FontWeight.Normal
        )
        if (path.isNotEmpty()) {
            val parts = path.split("/").filter { it.isNotEmpty() }
            var currentPath = ""
            parts.forEach { part ->
                currentPath += if (currentPath.isEmpty()) part else "/$part"
                val target = currentPath
                Text(" / ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text(
                    text = part,
                    modifier = Modifier.clickable { onNavigate(target) },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (target == path) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (target == path) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}
@Composable
fun FileItemRow(arxiu: ArxiuNas, onOpen: () -> Unit, onDownload: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    PremiumCard(onClick = onOpen) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (arxiu.es_carpeta) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (arxiu.es_carpeta) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(arxiu.nom, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!arxiu.es_carpeta) Text(formatSize(arxiu.mida_bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Opcions") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Descarregar") }, leadingIcon = { Icon(Icons.Default.Download, null) }, onClick = { showMenu = false; onDownload() })
                    DropdownMenuItem(text = { Text("Canviar nom") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { showMenu = false; onRename() })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("Eliminar") }, 
                        leadingIcon = { Icon(Icons.Default.Delete, null) }, 
                        onClick = { showMenu = false; onDelete() }, 
                        colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }
}
@Composable
fun UploadProgressCard(state: NasViewModel.UploadUiState) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        PremiumCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(if (state.totalFiles > 1) "Pujant ${state.uploadedFiles + 1} de ${state.totalFiles}..." else "Pujant fitxer...", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.currentFile, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.totalFiles > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress }, 
                        modifier = Modifier.fillMaxWidth(), 
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
@Composable
fun EmptyView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Carpeta buida", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}
@Composable
fun NameDialog(title: String, value: String, onValueChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = onValueChange, singleLine = true, shape = RoundedCornerShape(12.dp)) },
        confirmButton = { Button(onClick = onConfirm) { Text("Acceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel·lar") } }
    )
}
fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
