package com.btbugado.aerostation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.btbugado.aerostation.ui.components.gamepadFocusable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import com.btbugado.aerostation.ui.theme.AeroTextSecondary

/**
 * Ícone quadrado de jogo com cantos levemente arredondados e borda de vidro,
 * lembrando o menu do Switch — mas com o acabamento "glass" do Frutiger Aero.
 *
 * Com [flipEnabled], o card vira sozinho de tempos em tempos mostrando o
 * verso (nome + [backLine1]/[backLine2], ex: console e tempo de jogo),
 * no estilo das live tiles do Windows Phone. [flipStartDelayMs] dessincroniza
 * os cards da fileira pra não virarem todos juntos.
 *
 * Toque normal abre o jogo; toque longo abre o menu de customização
 * (renomear, trocar capa, etc).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCard(
    title: String,
    modifier: Modifier = Modifier,
    artUrl: String? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    gamepadAutoFocus: Boolean = false,
    nextUp: FocusRequester? = null,
    nextDown: FocusRequester? = null,
    nextLeft: FocusRequester? = null,
    nextRight: FocusRequester? = null,
    focusRequester: FocusRequester? = null,
    onFocusGained: (() -> Unit)? = null,
    flipEnabled: Boolean = false,
    flipStartDelayMs: Long = 0L,
    /** Pausa na frente antes de virar (vem do Ajustes, em ms). */
    flipFrontHoldMs: Long = 4200L,
    backLine1: String? = null,
    backLine2: String? = null,
    /** Capturas do jogo: o verso roda entre elas a cada ciclo. */
    backArtUrls: List<String> = emptyList()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press-scale")
    // No modo controle virtual, o toque não abre o jogo (só o gamepad) —
    // evita inicialização acidental ao usar o pad na tela.
    val touchOk = LocalTouchActionsEnabled.current
    val density = LocalDensity.current

    // Flip frente/verso: segura a frente, dobra até 180° (verso), segura o
    // verso e completa a volta. O snap de 360° pra 0° é invisível.
    val rotation = remember { Animatable(0f) }
    // Qual captura está no verso (roda a cada ciclo que mostra o verso).
    var backIndex by remember { mutableStateOf(0) }
    val backArt = backArtUrls.getOrNull(backIndex)
    LaunchedEffect(flipEnabled, flipFrontHoldMs, backArtUrls.size) {
        if (!flipEnabled) {
            rotation.snapTo(0f)
            return@LaunchedEffect
        }
        backIndex = 0
        delay(flipStartDelayMs)
        while (true) {
            delay(flipFrontHoldMs)
            rotation.animateTo(180f, tween(550, easing = FastOutSlowInEasing))
            delay(2600)
            if (backArtUrls.size > 1) {
                backIndex = (backIndex + 1) % backArtUrls.size
            }
            rotation.animateTo(360f, tween(550, easing = FastOutSlowInEasing))
            rotation.snapTo(0f)
        }
    }
    val showingBack = flipEnabled && rotation.value in 90f..270f

    Column(
        modifier = modifier
            .aspectRatio(0.8f)
            // Só o pressionar mexe neste nível: o nó medido pelo foco fica
            // parado, então o cursor do controle não passeia durante o flip.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(AeroGlassWhiteStrong, AeroGlassWhite)
                )
            )
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(18.dp))
            .gamepadFocusable(
                autoFocus = gamepadAutoFocus,
                onConfirm = onClick,
                onSecondary = onLongClick,
                nextUp = nextUp,
                nextDown = nextDown,
                nextLeft = nextLeft,
                nextRight = nextRight,
                focusRequester = focusRequester,
                onFocusGained = onFocusGained
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (touchOk) onClick() },
                onLongClick = { if (touchOk) onLongClick() }
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A rotação vive SÓ aqui dentro: o layout é idêntico ao de antes,
        // mas o retângulo publicado pro cursor nunca muda de tamanho.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation.value
                    cameraDistance = 12f * density.density
                }
        ) {
            if (showingBack) {
                // Verso: conteúdo pré-espelhado (180°) pra compensar a rotação
                // externa — no total dá a volta completa e o texto sai legível.
                // Com captura, ela vira o fundo (com véu pra leitura); sem,
                // o verso é só o vidro com os textos. Se a imagem falhar, o
                // Coil não desenha nada e cai no mesmo vidro + textos.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    if (backArt != null) {
                        AsyncImage(
                            model = backArt,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.25f),
                                            Color.Black.copy(alpha = 0.72f)
                                        )
                                    )
                                )
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            color = AeroTextPrimary,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            maxLines = 2
                        )
                        if (!backLine1.isNullOrBlank()) {
                            Text(
                                text = backLine1,
                                color = AeroTextSecondary,
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                        if (!backLine2.isNullOrBlank()) {
                            Text(
                                text = backLine2,
                                color = AeroTextPrimary,
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 22.dp)
                    ) {
                        if (artUrl != null) {
                            AsyncImage(
                                model = artUrl,
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            // Placeholder da capa do jogo — some assim que uma capa é atribuída
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0x552E86AB), Color(0x556FD6C4))
                                        ),
                                        RoundedCornerShape(12.dp)
                                    )
                            )
                        }
                    }
                    Text(
                        text = title,
                        color = AeroTextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
