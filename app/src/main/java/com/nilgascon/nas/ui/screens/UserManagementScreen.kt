package com.nilgascon.nas.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nilgascon.nas.model.UserOut
import com.nilgascon.nas.ui.theme.PremiumCard
import com.nilgascon.nas.viewmodel.NasViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(viewModel: NasViewModel, onGoBack: () -> Unit) {
    val users by viewModel.usuarisGestio.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var userToEdit by remember { mutableStateOf<UserOut?>(null) }
    LaunchedEffect(Unit) {
        viewModel.carregarUsuaris()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestió d'Usuaris", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onGoBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(users) { user ->
                UserRow(
                    user = user,
                    onDelete = { viewModel.eliminarUsuari(user.username) },
                    onEdit = { userToEdit = user }
                )
            }
        }
    }
    if (showAddDialog) {
        UserDialog(
            title = "Afegir Nou Usuari",
            confirmLabel = "Crear",
            onDismiss = { showAddDialog = false },
            onConfirm = { u, p, r ->
                viewModel.crearUsuari(u, p ?: "", r)
                showAddDialog = false
            }
        )
    }
    if (userToEdit != null) {
        UserDialog(
            title = "Editar Usuari",
            confirmLabel = "Actualitzar",
            initialUsername = userToEdit!!.username,
            initialRole = userToEdit!!.role,
            isEdit = true,
            onDismiss = { userToEdit = null },
            onConfirm = { u, p, r ->
                viewModel.editarUsuari(userToEdit!!.username, u, p, r)
                userToEdit = null
            }
        )
    }
}
@Composable
fun UserRow(user: UserOut, onDelete: () -> Unit, onEdit: () -> Unit) {
    PremiumCard(onClick = onEdit) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (user.role == "admin") Icons.Default.AdminPanelSettings else Icons.Default.Person,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.username, fontWeight = FontWeight.Bold)
                Text("Rol: ${user.role.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
@Composable
fun UserDialog(
    title: String,
    confirmLabel: String,
    initialUsername: String = "",
    initialRole: String = "user",
    isEdit: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String) -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(initialRole) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nom d'usuari") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isEdit) "Nova contrasenya (opcional)" else "Contrasenya") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Permisos", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = role == "user", onClick = { role = "user" })
                    Text("Usuari Estàndard", modifier = Modifier.padding(start = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = role == "admin", onClick = { role = "admin" })
                    Text("Administrador", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(username, password.ifBlank { null }, role) },
                enabled = username.isNotBlank() && (isEdit || password.isNotBlank())
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel·lar") }
        }
    )
}
