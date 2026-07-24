package com.radiopolska.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class RadioSkin(
    val id: String,
    val name: String,
    val description: String,
    val accent: Color,
)

val RadioSkins = listOf(
    RadioSkin("system", "System", "Jasny lub ciemny zgodnie z telefonem", Color(0xFF2563EB)),
    RadioSkin("classic", "Klasyczna", "Czysta paleta radiowa", Color(0xFFD21F3C)),
    RadioSkin("night", "Nocna", "Ciemny motyw do sluchania wieczorem", Color(0xFF14B8A6)),
    RadioSkin("forest", "Leśna", "Zielony akcent i spokojne tlo", Color(0xFF16A34A)),
    RadioSkin("amber", "Bursztynowa", "Cieply akcent dla paneli sterowania", Color(0xFFF59E0B)),
)

private val SystemDarkColorScheme = darkColorScheme(
    primary = Color(0xFF93C5FD),
    secondary = Color(0xFFA7F3D0),
    tertiary = Color(0xFFF0ABFC),
)

private val SystemLightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    secondary = Color(0xFF047857),
    tertiary = Color(0xFFB45309),
)

private val ClassicColorScheme = lightColorScheme(
    primary = Color(0xFFD21F3C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DE),
    secondary = Color(0xFF2563EB),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFF3E7EA),
    background = Color(0xFFFFFBFE),
)

private val NightColorScheme = darkColorScheme(
    primary = Color(0xFF14B8A6),
    onPrimary = Color(0xFF062A27),
    primaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFFF97316),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1F2937),
    background = Color(0xFF0B1120),
)

private val ForestColorScheme = lightColorScheme(
    primary = Color(0xFF15803D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    secondary = Color(0xFF0F766E),
    surface = Color(0xFFFBFEFC),
    surfaceVariant = Color(0xFFE8F5EC),
    background = Color(0xFFFBFEFC),
)

private val AmberColorScheme = lightColorScheme(
    primary = Color(0xFFD97706),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE7BD),
    secondary = Color(0xFF7C3AED),
    surface = Color(0xFFFFFCF7),
    surfaceVariant = Color(0xFFF7EAD7),
    background = Color(0xFFFFFCF7),
)

private fun skinColorScheme(skinId: String, darkTheme: Boolean): ColorScheme =
    when (skinId) {
        "classic" -> ClassicColorScheme
        "night" -> NightColorScheme
        "forest" -> ForestColorScheme
        "amber" -> AmberColorScheme
        else -> if (darkTheme) SystemDarkColorScheme else SystemLightColorScheme
    }

@Composable
fun RadioPolskaTheme(
    skinId: String = "system",
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = skinColorScheme(skinId, darkTheme),
        typography = Typography,
        content = content,
    )
}
