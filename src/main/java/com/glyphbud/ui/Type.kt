@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.glyphbud.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.glyphbud.R

/**
 * Google Sans Flex as a bundled variable font, tuned per style the same way LastWave does it.
 * Axes: wght 1-1000, wdth 25-151, opsz 6-144, ROND 0-100, GRAD 0-100, slnt -10-0.
 * Headlines go heavier, wider and rounder. Body text stays near the font's defaults.
 * Labels get extra roundness to match pill-shaped chips. opsz follows each style's size.
 */
private fun gsFlex(
    weight: Float,
    width: Float = 100f,
    round: Float = 0f,
    opticalSize: Float,
    grade: Float = 0f,
): FontFamily = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = when {
            weight >= 700f -> FontWeight.Bold
            weight >= 600f -> FontWeight.SemiBold
            weight >= 500f -> FontWeight.Medium
            else -> FontWeight.Normal
        },
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight.toInt()),
            FontVariation.width(width),
            FontVariation.Setting("ROND", round),
            FontVariation.Setting("opsz", opticalSize),
            FontVariation.Setting("GRAD", grade),
        ),
    ),
)

val GlyphTypography = Typography(
    displayLarge = TextStyle(fontFamily = gsFlex(weight = 760f, width = 122f, round = 42f, opticalSize = 57f), fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = gsFlex(weight = 740f, width = 120f, round = 40f, opticalSize = 45f), fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = gsFlex(weight = 720f, width = 118f, round = 38f, opticalSize = 36f), fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = gsFlex(weight = 800f, width = 122f, round = 40f, opticalSize = 32f), fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = gsFlex(weight = 780f, width = 120f, round = 38f, opticalSize = 28f), fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = gsFlex(weight = 760f, width = 116f, round = 36f, opticalSize = 24f), fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = gsFlex(weight = 640f, width = 110f, round = 30f, opticalSize = 22f), fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = gsFlex(weight = 600f, width = 106f, round = 28f, opticalSize = 16f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = gsFlex(weight = 580f, width = 105f, round = 26f, opticalSize = 14f), fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = gsFlex(weight = 440f, width = 102f, round = 18f, opticalSize = 16f), fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = gsFlex(weight = 430f, width = 102f, round = 18f, opticalSize = 15f), fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = gsFlex(weight = 420f, width = 100f, round = 16f, opticalSize = 13f), fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = gsFlex(weight = 680f, width = 108f, round = 46f, opticalSize = 14f), fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = gsFlex(weight = 660f, width = 108f, round = 44f, opticalSize = 12f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = gsFlex(weight = 660f, width = 108f, round = 44f, opticalSize = 11f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp),
)
