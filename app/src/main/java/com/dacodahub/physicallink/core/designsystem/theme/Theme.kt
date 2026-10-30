package com.dacodahub.physicallink.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

internal val DarkColorScheme = darkColorScheme(
    primary = PhysicalLinkAccents.TealDark, onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF124F4C), onPrimaryContainer = Color(0xFFA2F5EE),
    secondary = PhysicalLinkAccents.IndigoDark, secondaryContainer = Color(0xFF343F60), onSecondaryContainer = Color(0xFFDDE2FF),
    tertiary = PhysicalLinkAccents.AmberDark, tertiaryContainer = Color(0xFF5B4121), onTertiaryContainer = Color(0xFFFFDDB4),
    background = Color(0xFF071219), onBackground = Color(0xFFE8F0FA),
    surface = Color(0xFF10212C), onSurface = Color(0xFFE8F0FA), onSurfaceVariant = Color(0xFFB8C9CA),
    surfaceContainer = Color(0xFF142733), surfaceContainerLow = Color(0xFF10212C),
    surfaceContainerHigh = Color(0xFF243B49), outline = Color(0xFF849899), outlineVariant = Color(0xFF3D5053)
)
internal val LightColorScheme = lightColorScheme(
    primary = PhysicalLinkAccents.TealLight, onPrimary = Color.White,
    primaryContainer = Color(0xFFB0F1E9), onPrimaryContainer = Color(0xFF004C48),
    secondary = PhysicalLinkAccents.IndigoLight, secondaryContainer = Color(0xFFDDE4FF), onSecondaryContainer = Color(0xFF263354),
    tertiary = PhysicalLinkAccents.AmberLight, tertiaryContainer = Color(0xFFFFDDB4), onTertiaryContainer = Color(0xFF614015),
    background = Color(0xFFF0F5FA), onBackground = Color(0xFF101C35),
    surface = Color(0xFFFCFDFF), onSurface = Color(0xFF101C35), onSurfaceVariant = Color(0xFF50647D),
    surfaceContainer = Color(0xFFE8F0F8), surfaceContainerLow = Color(0xFFF8FBFF),
    surfaceContainerHigh = Color(0xFFDDE9E9), outline = Color(0xFF6A8082), outlineVariant = Color(0xFFBECECF)
)

@Composable
fun PhysicalLinkTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
