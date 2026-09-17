package com.btbugado.aerostation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.btbugado.aerostation.data.RaPlayedGame
import com.btbugado.aerostation.data.RaProfile
import com.btbugado.aerostation.data.RaResult
import com.btbugado.aerostation.data.RetroAchievementsApi
import com.btbugado.aerostation.data.RetroAchievementsStore
import com.btbugado.aerostation.ui.components.SettingsItem
import com.btbugado.aerostation.ui.components.gamepadFocusable
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import com.btbugado.aerostation.ui.theme.AeroTextSecondary

private sealed interface AchievementsState {
    data object NoCredentials : AchievementsState
    data object Loading : AchievementsState
    data object InvalidCredentials : AchievementsState
    data object NetworkError : AchievementsState
    data class Content(val profile: RaProfile, val games: List<RaPlayedGame>) : AchievementsState
}

/**
 * Aba de conquistas: perfil do RetroAchievements + jogos jogados com
 * progresso de troféus. Precisa de usuário + chave de API configurados em
 * Ajustes > Conquistas.
 */
@Composable
fun AchievementsScreen(
    contentPadding: PaddingValues = PaddingValues(),
    gamepadActive: Boolean = false
) {
    val context = LocalContext.current
    val credentialsKey = remember {
        RetroAchievementsStore.loadUsername(context) + "\u0000" +
            RetroAchievementsStore.loadApiKey(context)
    }
    var refreshTick by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<AchievementsState>(AchievementsState.Loading) }

    LaunchedEffect(credentialsKey, refreshTick) {
        val username = RetroAchievementsStore.loadUsername(context)
        val apiKey = RetroAchievementsStore.loadApiKey(context)
        if (username.isBlank() || apiKey.isBlank()) {
            state = AchievementsState.NoCredentials
            return@LaunchedEffect
        }
        state = AchievementsState.Loading
        // Conta primeiro (valida a credencial e traz nome canônico + foto),
        // depois pontos/rank e por fim a lista de jogos.
        when (val account = RetroAchievementsApi.getUserAccount(username, apiKey)) {
            is RaResult.InvalidCredentials -> {
                state = AchievementsState.InvalidCredentials
            }
            is RaResult.NetworkError -> {
                state = AchievementsState.NetworkError
            }
            is RaResult.Ok -> {
                val (displayName, picUrl) = account.value
                when (val profile = RetroAchievementsApi.getProfile(username, apiKey, displayName, picUrl)) {
                    is RaResult.InvalidCredentials -> {
                        state = AchievementsState.InvalidCredentials
                    }
                    is RaResult.NetworkError -> {
                        state = AchievementsState.NetworkError
                    }
                    is RaResult.Ok -> {
                        when (val games = RetroAchievementsApi.getRecentlyPlayed(username, apiKey)) {
                            is RaResult.Ok -> state = AchievementsState.Content(profile.value, games.value)
                            else -> state = AchievementsState.NetworkError
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        when (val current = state) {
            is AchievementsState.NoCredentials -> CenteredMessage(
                "Conecte sua conta do RetroAchievements em Ajustes > Conquistas\npara ver pontos e progresso aqui."
            )
            is AchievementsState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            is AchievementsState.InvalidCredentials -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CenteredMessage("Usuário ou chave de API inválidos.\nConfira em Ajustes > Conquistas.")
                Spacer(modifier = Modifier.height(16.dp))
                SettingsItem(
                    title = "Tentar de novo",
                    gamepadAutoFocus = gamepadActive,
                    onClick = { refreshTick++ }
                )
            }
            is AchievementsState.NetworkError -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CenteredMessage("Não consegui falar com o RetroAchievements.\nConfira a internet e tente de novo.")
                Spacer(modifier = Modifier.height(16.dp))
                SettingsItem(
                    title = "Tentar de novo",
                    gamepadAutoFocus = gamepadActive,
                    onClick = { refreshTick++ }
                )
            }
            is AchievementsState.Content -> {
                if (current.games.isEmpty()) {
                    CenteredMessage(
                        "Nenhum jogo jogado ainda.\nJogue algo com conquistas e volte aqui!"
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 30.dp,
                            end = 30.dp,
                            top = 20.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "profile") {
                            ProfileHeader(
                                profile = current.profile,
                                gamepadAutoFocus = false
                            )
                        }
                        itemsIndexed(current.games, key = { _, it -> it.gameId }) { index, game ->
                            PlayedGameRow(
                                game = game,
                                gamepadAutoFocus = gamepadActive && index == 0
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = AeroTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun ProfileHeader(profile: RaProfile, gamepadAutoFocus: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite)))
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(18.dp))
            .gamepadFocusable(autoFocus = gamepadAutoFocus)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (profile.picUrl != null) {
            AsyncImage(
                model = profile.picUrl,
                contentDescription = profile.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AeroGlassWhite)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AeroGlassWhite)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.displayName,
                color = AeroTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${profile.totalPoints} pontos",
                color = AeroTextPrimary,
                fontSize = 14.sp
            )
            Text(
                text = if (profile.rank > 0) {
                    "Rank #${profile.rank} • ${profile.totalSoftcorePoints} casual"
                } else {
                    "${profile.totalSoftcorePoints} pontos casuais"
                },
                color = AeroTextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun PlayedGameRow(game: RaPlayedGame, gamepadAutoFocus: Boolean) {
    val progress = if (game.numPossible > 0) {
        (game.numAchieved.toFloat() / game.numPossible).coerceIn(0f, 1f)
    } else {
        0f
    }
    val subtitle = buildString {
        if (game.consoleName.isNotBlank()) append(game.consoleName)
        if (game.numPossible > 0) {
            if (isNotEmpty()) append(" • ")
            append("${game.numAchieved}/${game.numPossible}")
            if (game.numAchievedHardcore > 0) append(" (${game.numAchievedHardcore} hardcore)")
        }
        if (game.lastPlayed.isNotBlank()) {
            if (isNotEmpty()) append(" • ")
            append(game.lastPlayed)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite)))
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(16.dp))
            .gamepadFocusable(autoFocus = gamepadAutoFocus)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (game.imageUrl != null) {
            AsyncImage(
                model = game.imageUrl,
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AeroGlassWhite)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                color = AeroTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = AeroTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            if (game.numPossible > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
