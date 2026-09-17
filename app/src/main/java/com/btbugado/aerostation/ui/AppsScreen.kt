package com.btbugado.aerostation.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.btbugado.aerostation.data.LauncherContentCache
import com.btbugado.aerostation.ui.components.AppIconCard
import com.btbugado.aerostation.ui.theme.AeroTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AppIconWidth = 120.dp
private const val AppIconPx = 128

private data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap
)

@Composable
fun AppsScreen(contentPadding: PaddingValues = PaddingValues(), gamepadActive: Boolean = false) {
    val context = LocalContext.current
    // null = ainda carregando; lista vazia = carregou e não achou nada
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    val scope = rememberCoroutineScope()
    // Mesmo snap da Home: focar o primeiro app recentraliza a fila no recuo.
    val rowState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val cached = LauncherContentCache.getApps()
        if (cached != null) {
            apps = cached.map {
                InstalledApp(
                    label = it.label,
                    packageName = it.packageName,
                    icon = it.icon.asImageBitmap()
                )
            }
        } else {
            val loaded = withContext(Dispatchers.Default) { loadLaunchableApps(context) }
            LauncherContentCache.putApps(loaded)
            apps = loaded.map {
                InstalledApp(
                    label = it.label,
                    packageName = it.packageName,
                    icon = it.icon.asImageBitmap()
                )
            }
        }
    }

    val currentApps = apps
    when {
        currentApps == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        currentApps.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nenhum app encontrado.", color = AeroTextSecondary)
            }
        }

        else -> {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(
                    // Mesmo enquadramento da Home (fila começa aos 76dp).
                    start = 76.dp,
                    end = 32.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding()
                ),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(currentApps, key = { _, it -> it.packageName }) { index, app ->
                    AppIconCard(
                        title = app.label,
                        icon = app.icon,
                        modifier = Modifier.width(AppIconWidth),
                        gamepadAutoFocus = gamepadActive && index == 0,
                        onFocusGained = if (index == 0) {
                            { scope.launch { rowState.animateScrollToItem(0) } }
                        } else null,
                        onClick = {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(launchIntent)
                            }
                        }
                    )
                }
            }
        }
    }
}

/** Busca todos os apps com launcher (os que aparecem na gaveta de apps normal do Android). */
private fun loadLaunchableApps(context: Context): List<LauncherContentCache.LauncherApp> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

    return resolved
        .asSequence()
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { info ->
            val label = info.loadLabel(pm).toString()
            val bitmap = info.loadIcon(pm).toBitmap(width = AppIconPx, height = AppIconPx)
            LauncherContentCache.LauncherApp(
                label = label,
                packageName = info.activityInfo.packageName,
                icon = bitmap
            )
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}
