package com.mahamart.essae

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/*
 * MahaMart Scale Manager
 *
 * This theme intentionally uses a different visual language from
 * the other MahaMart apps:
 *
 * - white / cool-grey workspace
 * - charcoal device-control panels
 * - MahaMart red as an accent
 * - compact, technical controls
 *
 * Functionality is not changed by this file.
 */

private val ScaleRed = Color(0xFFD00019)
private val ScaleRedDark = Color(0xFFAA0014)
private val ScaleRedSoft = Color(0xFFFFE8EB)

private val ScaleBackground = Color(0xFFF4F5F7)
private val ScaleSurface = Color(0xFFFFFFFF)
private val ScaleSurfaceAlt = Color(0xFFF8F9FA)

private val ScaleText = Color(0xFF202124)
private val ScaleTextSecondary = Color(0xFF60656B)

private val ScaleBorder = Color(0xFFD9DDE2)
private val ScaleBorderStrong = Color(0xFFC5CAD1)

private val ScaleDark = Color(0xFF25282C)
private val ScaleDarkSecondary = Color(0xFF34383D)

private val ScaleLightColors: ColorScheme = lightColorScheme(
    primary = ScaleRed,
    onPrimary = Color.White,

    primaryContainer = ScaleRedSoft,
    onPrimaryContainer = ScaleRedDark,

    secondary = ScaleDark,
    onSecondary = Color.White,

    secondaryContainer = Color(0xFFE9EBEE),
    onSecondaryContainer = ScaleText,

    background = ScaleBackground,
    onBackground = ScaleText,

    surface = ScaleSurface,
    onSurface = ScaleText,

    surfaceVariant = ScaleSurfaceAlt,
    onSurfaceVariant = ScaleTextSecondary,

    outline = ScaleBorderStrong,
    outlineVariant = ScaleBorder,

    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun MahaMartTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ScaleLightColors,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(10.dp),
            extraLarge = RoundedCornerShape(12.dp)
        ),
        content = content
    )
}

@Composable
fun mahaMartButtonColors() = ButtonDefaults.buttonColors(
    containerColor = ScaleRed,
    contentColor = Color.White,
    disabledContainerColor = Color(0xFFE5AAB1),
    disabledContentColor = Color(0xFF7F4A51)
)

@Composable
fun mahaMartCardColors() = CardDefaults.cardColors(
    containerColor = ScaleSurface
)

/*
 * Colors used by the technical control-panel UI.
 */
fun scaleDarkColor() = ScaleDark
fun scaleDarkSecondaryColor() = ScaleDarkSecondary
fun scaleSurfaceAltColor() = ScaleSurfaceAlt
fun scaleBorderColor() = ScaleBorder
fun scaleTextSecondaryColor() = ScaleTextSecondary
fun scaleRedForUi() = ScaleRed
