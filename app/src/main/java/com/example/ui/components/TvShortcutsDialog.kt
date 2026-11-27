package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.DeviceMode

/**
 * Adds smooth D-Pad / TV remote focus scale and glowing border ring on focusable items.
 */
fun Modifier.tvFocusHighlight(
    shape: Shape = RoundedCornerShape(16.dp),
    focusedScale: Float = 1.04f
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tv_focus_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .onFocusChanged { isFocused = it.isFocused }
        .then(
            if (isFocused) {
                Modifier.border(
                    width = 2.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.primary
                        )
                    ),
                    shape = shape
                )
            } else {
                Modifier
            }
        )
}

data class TvShortcutSpec(
    val keys: String,
    val title: String,
    val description: String
)

private val TV_NAV_SHORTCUTS = listOf(
    TvShortcutSpec(
        keys = "▲ ▼ ◀ ▶",
        title = "Mover foco (D-Pad)",
        description = "Navega entre tarjetas, episodios, filtros y el menú lateral."
    ),
    TvShortcutSpec(
        keys = "OK / Enter",
        title = "Seleccionar",
        description = "Abre el anime enfocado o reproduce el episodio al instante."
    ),
    TvShortcutSpec(
        keys = "Atrás",
        title = "Regresar",
        description = "Vuelve a la pantalla anterior o cierra paneles abiertos."
    ),
    TvShortcutSpec(
        keys = "Buscar / Tecla /",
        title = "Ir a Explorar",
        description = "Abre el buscador y los filtros por género."
    ),
    TvShortcutSpec(
        keys = "Guía / Tecla G",
        title = "Abrir Calendario",
        description = "Consulta los estrenos por día de la semana."
    ),
    TvShortcutSpec(
        keys = "CH+ / CH−",
        title = "Cambiar servidor web",
        description = "Alterna rápido entre JKAnime y Latanime."
    ),
    TvShortcutSpec(
        keys = "Menú / Info / M",
        title = "Ver esta guía",
        description = "Abre los atajos del control TV en cualquier pantalla."
    )
)

private val TV_PLAYER_SHORTCUTS = listOf(
    TvShortcutSpec(
        keys = "OK / Play-Pausa",
        title = "Pausar o reanudar",
        description = "Alterna la reproducción y muestra los controles del video."
    ),
    TvShortcutSpec(
        keys = "◀ / ▶ (o Rew / FF)",
        title = "Retroceder / Adelantar 10s",
        description = "Salta 10 segundos atrás o adelante con aviso visual."
    ),
    TvShortcutSpec(
        keys = "▲ Arriba",
        title = "Lista de episodios",
        description = "Abre el selector rápido de episodios sin salir del video."
    ),
    TvShortcutSpec(
        keys = "Botón Rojo / S",
        title = "Omitir Opening / Ending",
        description = "Adelanta +85s para saltar el opening o ending al toque."
    ),
    TvShortcutSpec(
        keys = "CH+ / Siguiente",
        title = "Cambiar de episodio",
        description = "Canal+ pasa al episodio siguiente; Canal− al anterior."
    ),
    TvShortcutSpec(
        keys = "Botón Azul / F",
        title = "Pantalla completa",
        description = "Expande o contrae el modo cine del reproductor."
    )
)

@Composable
fun TvRemoteShortcutsDialog(
    currentMode: DeviceMode,
    onSwitchMode: ((DeviceMode) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .testTag("tv_shortcuts_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.SettingsRemote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.tv_shortcuts_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.tv_shortcuts_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("tv_shortcuts_close")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TvShortcutSection(
                        title = "Navegación en Smart TV",
                        icon = Icons.Rounded.LiveTv,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        items = TV_NAV_SHORTCUTS
                    )
                    TvShortcutSection(
                        title = "Control durante el episodio",
                        icon = Icons.Rounded.PlayArrow,
                        accentColor = MaterialTheme.colorScheme.primary,
                        items = TV_PLAYER_SHORTCUTS
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onSwitchMode != null) {
                        val targetMode = if (currentMode == DeviceMode.SMART_TV) {
                            DeviceMode.PHONE
                        } else {
                            DeviceMode.SMART_TV
                        }
                        OutlinedButton(
                            onClick = {
                                onSwitchMode(targetMode)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("tv_shortcuts_switch_mode")
                        ) {
                            Icon(
                                imageVector = if (targetMode == DeviceMode.PHONE) {
                                    Icons.Rounded.Smartphone
                                } else {
                                    Icons.Rounded.Tv
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (targetMode == DeviceMode.PHONE) {
                                    "Pasar a Teléfono"
                                } else {
                                    "Activar Smart TV"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("tv_shortcuts_done_button")
                    ) {
                        Text(
                            text = stringResource(R.string.tv_shortcuts_continue),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvShortcutSection(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    items: List<TvShortcutSpec>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { spec ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = accentColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = spec.keys,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = spec.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = spec.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
