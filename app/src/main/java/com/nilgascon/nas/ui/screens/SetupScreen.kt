package com.nilgascon.nas.ui.screens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nilgascon.nas.ui.theme.PremiumCard
import com.nilgascon.nas.viewmodel.NasViewModel
@Composable
fun SetupScreen(viewModel: NasViewModel, onSetupComplete: () -> Unit) {
    var adminUser by remember { mutableStateOf("") }
    var adminPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isConfiguring by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Icon(Icons.Default.Shield, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Configuració de Seguretat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text("Benvingut al teu nou NAS privat", color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(32.dp))
        PremiumCard(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Has d'establir un usuari administrador real per desactivar l'usuari per defecte insegur.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        PremiumCard {
            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextField(
                    value = adminUser,
                    onValueChange = { adminUser = it },
                    label = { Text("Nou Nom d'Administrador") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = adminPass,
                    onValueChange = { adminPass = it },
                    label = { Text("Contrasenya Robusta") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = confirmPass,
                    onValueChange = { confirmPass = it },
                    label = { Text("Confirma la Contrasenya") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Shield, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (adminPass != confirmPass) {
                            error = "Les contrasenyes no coincideixen."
                            return@Button
                        }
                        if (adminPass.length < 6) {
                            error = "La contrasenya ha de tenir almenys 6 caràcters."
                            return@Button
                        }
                        isConfiguring = true
                        viewModel.completarSetup(adminUser, adminPass, onSuccess = {
                            isConfiguring = false
                            onSetupComplete()
                        }, onError = {
                            isConfiguring = false
                            error = it
                        })
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isConfiguring && adminUser.isNotBlank() && adminPass.isNotBlank()
                ) {
                    if (isConfiguring) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("FINALITZAR CONFIGURACIÓ", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
