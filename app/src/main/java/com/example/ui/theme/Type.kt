package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val NdotFont = FontFamily(
    Font(R.font.ndot55, FontWeight.Bold)
)

val NTypeFont = FontFamily(
    Font(R.font.ntype82, FontWeight.Bold)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 50.sp
    ),
    displayMedium = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp
    ),
    displaySmall = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleLarge = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp
    ),
    bodySmall = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
    ),
    labelLarge = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp
    ),
    labelMedium = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
    ),
    labelSmall = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp
    )
)
