package com.kadaliao.kaixinguo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The pistachio green of the app's icon: deeper on light backgrounds,
// brighter on dark ones, as the iOS app's accent color.
private val light = lightColorScheme(
    primary = Color(0xFF4A8C2A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC3E58F),
    onPrimaryContainer = Color(0xFF0F2400),
    secondary = Color(0xFF56624B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE7C9),
    onSecondaryContainer = Color(0xFF141E0C),
    tertiary = Color(0xFF386664),
    background = Color(0xFFFDFDF8),
    onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFFDFDF8),
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFE0E4D6),
    onSurfaceVariant = Color(0xFF5B6052),
    surfaceBright = Color(0xFFFDFDF8),
    surfaceDim = Color(0xFFDADBD4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8F1),
    surfaceContainer = Color(0xFFF1F2EB),
    surfaceContainerHigh = Color(0xFFEBECE5),
    surfaceContainerHighest = Color(0xFFE5E6DF),
    outline = Color(0xFF74796D),
    outlineVariant = Color(0xFFC4C8BA),
)

private val dark = darkColorScheme(
    primary = Color(0xFF7CC24A),
    onPrimary = Color(0xFF0F2A00),
    primaryContainer = Color(0xFF2F5E1C),
    onPrimaryContainer = Color(0xFFC3E58F),
    secondary = Color(0xFFBECBAE),
    onSecondary = Color(0xFF29341F),
    secondaryContainer = Color(0xFF3F4A34),
    onSecondaryContainer = Color(0xFFDAE7C9),
    tertiary = Color(0xFFA0CFCC),
    background = Color(0xFF121410),
    onBackground = Color(0xFFE2E3DC),
    surface = Color(0xFF121410),
    onSurface = Color(0xFFE2E3DC),
    surfaceVariant = Color(0xFF44483E),
    onSurfaceVariant = Color(0xFFA9AD9F),
    surfaceBright = Color(0xFF383A35),
    surfaceDim = Color(0xFF121410),
    surfaceContainerLowest = Color(0xFF0D0F0B),
    surfaceContainerLow = Color(0xFF1A1C18),
    surfaceContainer = Color(0xFF1E201C),
    surfaceContainerHigh = Color(0xFF282B26),
    surfaceContainerHighest = Color(0xFF333631),
    outline = Color(0xFF8E9286),
    outlineVariant = Color(0xFF44483E),
)

/** The player is dark whatever the system's look, around a black video. */
private val player = dark.copy(background = Color.Black, surface = Color(0xFF121212), surfaceContainerLow = Color(0xFF171717))

/** KaixinguoTheme follows the system's light or dark look. */
@Composable
fun KaixinguoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, content = content)
}

@Composable
fun PlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = player, content = content)
}
