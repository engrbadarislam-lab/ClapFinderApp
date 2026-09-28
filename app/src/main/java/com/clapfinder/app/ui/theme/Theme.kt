package com.clapfinder.app.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos

/** Same values as res/values/colors.xml (the XML stays for the service, overlay and notifications). */
object CF {
    val GreenPrimary = Color(0xFFF57C00)       // green_primary (brand orange)
    val GreenPrimaryDark = Color(0xFFDB6A00)   // green_primary_dark
    val GreenSoft = Color(0xFFFFF0E1)          // green_soft
    val Bg = Color(0xFFF57C00)                 // bg
    val Sheet = Color(0xFFFFFFFF)              // sheet
    val Card = Color(0xFFF1F4F2)               // card
    val Divider = Color(0xFFE3E8E5)            // divider
    val TextPrimary = Color(0xFF161A18)        // text_primary
    val TextSecondary = Color(0xFF7C8A83)      // text_secondary
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)
    val DarkOrange = Color(0xFFCC5500)         // darkorange
    val OrangePrimary = Color(0xFFFF6B00)      // orange_primary
    val OrangePrimaryDisabled = Color(0x66FF6B00) // orange_primary_disabled
    val WhiteSoft = Color(0xDDFFFFFF)          // #DDFFFFFF used on splash / overlay texts
    val SeekTrack = Color(0xFFD8E2DC)          // seek_progress.xml background
    val RadioOff = Color(0xFFB7C2BB)           // radio_off.xml stroke

    /** res/drawable/bg_splash.xml (angle 270 = top → bottom). */
    val SplashGradient = Brush.verticalGradient(
        0f to Color(0xFFF57C00),
        0.5f to Color(0xFFFF8F2E),
        1f to Color(0xFFDB6A00),
    )

    /** @dimen/card_radius */
    val CardShape = RoundedCornerShape(22.dp)
}

/** android.view.animation.AccelerateDecelerateInterpolator — so animations feel exactly like the old Views. */
val AccelerateDecelerate = Easing { x -> (cos((x + 1f) * PI) / 2.0 + 0.5).toFloat() }

/**
 * TextView-like defaults: no Material letter-spacing / fixed line-height, and the
 * classic font padding, so text boxes measure like the old XML layouts.
 */
@Suppress("DEPRECATION")
val BaseTextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    letterSpacing = 0.sp,
    lineHeight = TextUnit.Unspecified,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)

/** style/CardTitle (built on [BaseTextStyle] because `style =` replaces the local style). */
val CardTitle = BaseTextStyle.copy(color = CF.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)

@Composable
fun ClapFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CF.GreenPrimary,
            onPrimary = CF.White,
            secondary = CF.GreenPrimary,
            background = CF.Bg,
        )
    ) {
        CompositionLocalProvider(LocalTextStyle provides BaseTextStyle, content = content)
    }
}
