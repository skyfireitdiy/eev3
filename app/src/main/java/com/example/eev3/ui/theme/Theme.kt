package com.example.eev3.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 定义亮色主题颜色
private val LightColors = lightColorScheme(
    primary = Color(0xFF2196F3),        // 明亮的蓝色
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF1976D2),
    
    secondary = Color(0xFFE91E63),      // 粉红色
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF8BBD0),
    onSecondaryContainer = Color(0xFFC2185B),
    
    tertiary = Color(0xFF4CAF50),       // 绿色
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8E6C9),
    onTertiaryContainer = Color(0xFF388E3C),
    
    error = Color(0xFFFF5252),          // 鲜艳的红色
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFFD32F2F),
    
    background = Color(0xFFFAFAFA),     // 浅灰白色背景
    onBackground = Color(0xFF212121),
    surface = Color.White,
    onSurface = Color(0xFF212121),
    
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF757575)
)

// 定义暗色主题颜色
private val DarkColors = darkColorScheme(
    primary = Color(0xFF64B5F6),        // 亮蓝色
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1976D2),
    onPrimaryContainer = Color(0xFFBBDEFB),
    
    secondary = Color(0xFFFF4081),      // 亮粉色
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFC2185B),
    onSecondaryContainer = Color(0xFFF8BBD0),
    
    tertiary = Color(0xFF81C784),       // 亮绿色
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF388E3C),
    onTertiaryContainer = Color(0xFFC8E6C9),
    
    error = Color(0xFFFF8A80),          // 亮红色
    onError = Color.Black,
    errorContainer = Color(0xFFD32F2F),
    onErrorContainer = Color(0xFFFFCDD2),
    
    background = Color(0xFF121212),     // 深色背景
    onBackground = Color.White,
    surface = Color(0xFF212121),
    onSurface = Color.White,
    
    surfaceVariant = Color(0xFF424242),
    onSurfaceVariant = Color(0xFFBDBDBD)
)

// 定义其他品牌颜色
val VibrantPurple = Color(0xFF9C27B0)   // 鲜艳的紫色
val BrightOrange = Color(0xFFFF9800)    // 明亮的橙色
val ElectricBlue = Color(0xFF03A9F4)    // 电光蓝
val LimeGreen = Color(0xFFCDDC39)       // 青柠绿

@Composable
fun Eev3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}