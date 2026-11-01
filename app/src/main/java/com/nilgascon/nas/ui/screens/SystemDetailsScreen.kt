package com.nilgascon.nas.ui.screens
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nilgascon.nas.model.TestPhase
import com.nilgascon.nas.ui.theme.InfoStatItem
import com.nilgascon.nas.ui.theme.PremiumCard
import com.nilgascon.nas.ui.theme.SectionHeader
import com.nilgascon.nas.viewmodel.NasViewModel
import java.util.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDetailsScreen(
    viewModel: NasViewModel,
    onGoBack: () -> Unit
) {
    val details by viewModel.systemDetails.collectAsState()
    val perfStats by viewModel.performanceStats.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.iniciarAutoRefreshSystemDetails()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.aturarAutoRefreshSystemDetails()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalls del sistema", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Enrere")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (details == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Connectant amb el NAS...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            val d = details!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    SpeedTestCard(perfStats) { viewModel.executarTestRendiment() }
                }
                val temperatures = d.temperatures
                if (!temperatures.isNullOrEmpty()) {
                    item { SectionHeader("Temperatura") }
                    item {
                        PremiumCard {
                            Column(modifier = Modifier.padding(20.dp)) {
                                val mainTemp = temperatures.firstOrNull { it.label.lowercase().contains("package") || it.label.lowercase().contains("cpu") } 
                                    ?: temperatures.first()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                                        TemperatureGauge(temp = mainTemp.current ?: 0.0)
                                        Text(
                                            "${mainTemp.current?.toInt() ?: "--"}º",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(20.dp))
                                    Column {
                                        Text(mainTemp.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            if ((mainTemp.current ?: 0.0) > (mainTemp.high ?: 70.0)) "Temperatura elevada" else "Funcionament òptim",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if ((mainTemp.current ?: 0.0) > (mainTemp.high ?: 70.0)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (temperatures.size > 1) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    temperatures.drop(1).forEach { t ->
                                        InfoStatItem(
                                            label = t.label,
                                            value = "${t.current ?: "--"} ºC",
                                            icon = {
                                                Icon(
                                                    Icons.Default.DeviceThermostat,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = getTempColor(t.current ?: 0.0)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { SectionHeader("Maquinari") }
                item {
                    PremiumCard {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Emmagatzematge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { (d.disk.percentatge_usat / 100).toFloat() },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                strokeCap = StrokeCap.Round
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            InfoStatItem("Capacitat total", "${d.disk.capacitat_total_gb} GB")
                            InfoStatItem("Espai usat", "${d.disk.espai_usat_gb} GB (${d.disk.percentatge_usat}%)")
                            InfoStatItem("Espai lliure", "${d.disk.espai_lliure_gb} GB")
                        }
                    }
                }
                item {
                    PremiumCard {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("CPU i Memòria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            InfoStatItem("Ús CPU", "${d.cpu.percent_total}%")
                            InfoStatItem("Nuclis", "${d.cpu.count_logical} (L) / ${d.cpu.count_physical ?: 0} (F)")
                            InfoStatItem("RAM Usada", "${d.ram.used_gb} GB / ${d.ram.total_gb} GB (${d.ram.percent}%)")
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun TemperatureGauge(temp: Double) {
    val color = getTempColor(temp)
    val sweepAngle = 240f
    val startAngle = 150f
    val ratio = (temp / 100.0).coerceIn(0.0, 1.0).toFloat()
    Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
        drawArc(
            color = Color.LightGray.copy(alpha = 0.2f),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle * ratio,
            useCenter = false,
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
@Composable
fun getTempColor(temp: Double): Color {
    return when {
        temp < 45 -> Color(0xFF4CAF50)
        temp < 65 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
}
@Composable
fun SpeedTestCard(stats: com.nilgascon.nas.model.PerformanceStats, onStart: () -> Unit) {
    PremiumCard {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (stats.phase == TestPhase.FINISHED) {
                Text("Resultats de Xarxa", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ResultValueCard(
                        label = "DESCÀRREGA",
                        value = stats.downloadFinalMbps,
                        icon = Icons.Default.ArrowDownward,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary
                    )
                    ResultValueCard(
                        label = "PUJADA",
                        value = stats.uploadFinalMbps,
                        icon = Icons.Default.ArrowUpward,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatMiniItem("Ping", "${stats.latencyMs} ms")
                    VerticalDivider(modifier = Modifier.height(24.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    StatMiniItem("Jitter", "${stats.jitterMs} ms")
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text("Tornar a provar")
                }
            } else {
                Text(
                    text = when (stats.phase) {
                        TestPhase.IDLE -> "Velocitat del NAS"
                        TestPhase.PING -> "Calculant Ping..."
                        TestPhase.DOWNLOAD -> "Provant Descàrrega"
                        TestPhase.UPLOAD -> "Provant Pujada"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                    SpeedGauge(speed = stats.currentSpeedMbps, maxSpeed = stats.maxSpeedMbps.coerceAtLeast(100.0))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f", stats.currentSpeedMbps),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text("Mbps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                if (stats.isRunning) {
                    LinearProgressIndicator(
                        progress = { stats.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text("Iniciar Speedtest")
                    }
                }
            }
        }
    }
}
@Composable
fun ResultValueCard(label: String, value: Double, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, color: androidx.compose.ui.graphics.Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
            Text(
                text = String.format(Locale.getDefault(), "%.1f", value),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text("Mbps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
fun StatMiniItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}
@Composable
fun SpeedGauge(speed: Double, maxSpeed: Double) {
    val sweepAngle = 240f
    val startAngle = 150f
    val targetRatio = (speed / maxSpeed).coerceIn(0.0, 1.0).toFloat()
    val animatedRatio by animateFloatAsState(
        targetValue = targetRatio,
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "gauge"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawArc(
            color = Color.LightGray.copy(alpha = 0.2f),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                0.0f to Color.Cyan,
                0.5f to Color.Blue,
                1.0f to Color.Magenta
            ),
            startAngle = startAngle,
            sweepAngle = sweepAngle * animatedRatio,
            useCenter = false,
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
