package com.nilgascon.nas.ui.screens
import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.nilgascon.nas.model.NasConnection
import com.nilgascon.nas.ui.theme.PremiumCard
import com.nilgascon.nas.utils.TailscaleControl
import com.nilgascon.nas.viewmodel.NasViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: NasViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    var usuari by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val error by viewModel.loginError.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val connections by viewModel.savedConnections.collectAsState()
    val activeNas by viewModel.activeNas.collectAsState()
    val isNasAccessible by viewModel.isNasAccessible.collectAsState()
    var showNasDialog by remember { mutableStateOf(false) }
    var showNasList by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.verificarAccessibilitat()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Icon(Icons.Default.Storage, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("NAS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Privacitat total a les teves mans", color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(32.dp))
            PremiumCard(
                containerColor = if (isNasAccessible == true) Color(0xFF4CAF50).copy(alpha = 0.05f) 
                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.05f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isNasAccessible == true) Icons.Default.VpnLock else Icons.Default.VpnKeyOff, 
                            null, 
                            tint = if (isNasAccessible == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("VPN de Tailscale", fontWeight = FontWeight.Bold)
                            AnimatedContent(
                                targetState = isNasAccessible,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "status"
                            ) { status ->
                                Text(
                                    text = if (status == true) "Connexió segura establerta" else "El NAS no és accessible",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (status == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        StatusBadge(
                            text = if (isNasAccessible == true) "CONECTAT" else "OFFLINE",
                            color = if (isNasAccessible == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                        IconButton(onClick = { viewModel.verificarAccessibilitat() }) {
                            Icon(Icons.Default.Refresh, "Refrescar", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { TailscaleControl.openAndConnect(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isNasAccessible == true) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary, contentColor = if (isNasAccessible == true) MaterialTheme.colorScheme.onSurfaceVariant else Color.White)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CONNEXIÓ RÀPIDA", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                        OutlinedButton(
                            onClick = { activity?.let { TailscaleControl.disconnectAndExit(it) } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = borderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("TANCAR APP", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            PremiumCard(onClick = { showNasList = true }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activeNas?.name ?: "Selecciona un NAS", fontWeight = FontWeight.Bold)
                        if (activeNas != null) {
                            Text("${activeNas!!.ip}:${activeNas!!.port}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Icon(Icons.Default.UnfoldMore, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            PremiumCard {
                Column(modifier = Modifier.padding(24.dp)) {
                    OutlinedTextField(
                        value = usuari,
                        onValueChange = { usuari = it },
                        label = { Text("Usuari") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        enabled = !isLoggingIn
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Contrasenya") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        enabled = !isLoggingIn
                    )
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.doLogin(usuari, pass) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoggingIn && isNasAccessible == true && usuari.isNotBlank() && pass.isNotBlank()
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Connectant...")
                        } else {
                            Text("INICIAR SESSIÓ", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
    if (showNasList) {
        AlertDialog(
            onDismissRequest = { showNasList = false },
            title = { Text("Servidors NAS") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    if (connections.isEmpty()) {
                        Text("Cap NAS guardat.", modifier = Modifier.padding(vertical = 16.dp))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(connections) { nas ->
                                NasItemRow(
                                    nas = nas,
                                    isActive = nas.id == activeNas?.id,
                                    onClick = { viewModel.seleccionarNas(nas); viewModel.verificarAccessibilitat(); showNasList = false },
                                    onDelete = { viewModel.eliminarNas(nas) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showNasDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                        Icon(Icons.Default.Add, null); Spacer(modifier = Modifier.width(8.dp)); Text("Afegir nou servidor")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNasList = false }) { Text("Tancar") } }
        )
    }
    if (showNasDialog) {
        var nom by remember { mutableStateOf("") }
        var ip by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("8000") }
        AlertDialog(
            onDismissRequest = { showNasDialog = false },
            title = { Text("Nou Servidor NAS") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = nom, onValueChange = { nom = it }, label = { Text("Nom") }, singleLine = true)
                    OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("IP / Tailscale") }, singleLine = true)
                    OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Icon(Icons.Default.Https, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connexió HTTPS (SSL) forçada", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4CAF50))
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.afegirNas(nom, ip, port, true); showNasDialog = false }, enabled = nom.isNotBlank() && ip.isNotBlank() && port.isNotBlank()) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showNasDialog = false }) { Text("Cancel·lar") } }
        )
    }
}
@Composable
fun NasItemRow(nas: NasConnection, isActive: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Router, null, tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nas.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.Https, "HTTPS", modifier = Modifier.size(12.dp), tint = Color(0xFF4CAF50))
            }
            Text("${nas.ip}:${nas.port}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        }
    }
}
@Composable
fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)
