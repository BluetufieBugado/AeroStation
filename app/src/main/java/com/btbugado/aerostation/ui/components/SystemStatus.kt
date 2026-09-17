package com.btbugado.aerostation.ui.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cabeçalho de status inspirado no Switch:
 * horário + Wi-Fi + bateria, sem depender da barra de status do Android.
 */
@Composable
fun SystemStatus(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var batteryLevel by remember { mutableIntStateOf(readBatteryLevel(context)) }
    var wifiConnected by remember { mutableStateOf(isWifiConnected(context)) }
    var currentTime by remember { mutableStateOf(formatNow()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatNow()
            batteryLevel = readBatteryLevel(context)
            wifiConnected = isWifiConnected(context)
            delay(30_000)
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = currentTime,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 14.sp,
            color = AeroTextPrimary
        )
        WifiIndicator(
            connected = wifiConnected,
            modifier = Modifier.size(width = 20.dp, height = 18.dp)
        )
        BatteryIndicator(
            level = batteryLevel,
            modifier = Modifier.size(width = 23.dp, height = 18.dp)
        )
    }
}

@Composable
private fun WifiIndicator(
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (connected) AeroTextPrimary else AeroTextPrimary.copy(alpha = 0.35f)
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.10f, size.height * 0.36f)
            quadraticBezierTo(
                size.width * 0.50f, -size.height * 0.05f,
                size.width * 0.90f, size.height * 0.36f
            )
            moveTo(size.width * 0.27f, size.height * 0.56f)
            quadraticBezierTo(
                size.width * 0.50f, size.height * 0.30f,
                size.width * 0.73f, size.height * 0.56f
            )
        }
        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
        drawCircle(
            color = color,
            radius = 1.7.dp.toPx(),
            center = Offset(size.width * 0.50f, size.height * 0.82f)
        )
    }
}

@Composable
private fun BatteryIndicator(
    level: Int,
    modifier: Modifier = Modifier
) {
    val color = AeroTextPrimary
    Canvas(modifier = modifier.padding(1.dp)) {
        val bodyWidth = size.width * 0.82f
        val bodyHeight = size.height * 0.70f
        val top = (size.height - bodyHeight) / 2f

        // Corpo como CONTORNO (era preenchido sólido — por isso a bateria
        // parecia sempre cheia e só o brilho interno mexia).
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.78f),
            topLeft = Offset(2.dp.toPx(), top + 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(
                (bodyWidth - 4.dp.toPx()) * (level.coerceIn(0, 100) / 100f),
                bodyHeight - 4.dp.toPx()
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(bodyWidth, size.height * 0.38f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.12f, size.height * 0.24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
        )
    }
}

private fun readBatteryLevel(context: Context): Int {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    return if (scale > 0) (level * 100) / scale else 100
}

private fun isWifiConnected(context: Context): Boolean {
    return runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching false
        val network = cm.activeNetwork ?: return@runCatching false
        val capabilities = cm.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)
}

private fun formatNow(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
