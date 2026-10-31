package com.textbookgo.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.textbookgo.mobile.core.repository.AppTheme

/** 晨光主题：未漂白纸色 + 冷墨，强调色用于区分筛选维度与选中态。 */
private val DayColorScheme = lightColorScheme(
    primary = Color(0xFF1F1D1A),
    onPrimary = Color(0xFFFFFCF6),
    primaryContainer = Color(0xFFE9E2D3),
    onPrimaryContainer = Color(0xFF2A2723),
    secondary = Color(0xFF5B6B5F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE8DA),
    onSecondaryContainer = Color(0xFF26312A),
    tertiary = Color(0xFFA85B3B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3DED2),
    onTertiaryContainer = Color(0xFF3D281F),
    background = Color(0xFFF7F3EA),
    onBackground = Color(0xFF1F1D1A),
    surface = Color(0xFFFFFDF7),
    onSurface = Color(0xFF1F1D1A),
    surfaceVariant = Color(0xFFEFE9DD),
    onSurfaceVariant = Color(0xFF5C574E),
    outline = Color(0xFF8C867A),
    outlineVariant = Color(0xFFE2DACB),
    error = Color(0xFFA23B32),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF4DAD6),
    onErrorContainer = Color(0xFF3F1613),
)

/** 暗色主题：深板岩炭灰 + 暖米灰文字，拒绝纯黑高对比。 */
private val NightColorScheme = darkColorScheme(
    primary = Color(0xFFE7E3D9),
    onPrimary = Color(0xFF1A1918),
    primaryContainer = Color(0xFF2C2924),
    onPrimaryContainer = Color(0xFFEDE8DC),
    secondary = Color(0xFF9BB0A0),
    onSecondary = Color(0xFF141815),
    secondaryContainer = Color(0xFF26302A),
    onSecondaryContainer = Color(0xFFD7E4D9),
    tertiary = Color(0xFFD99A7C),
    onTertiary = Color(0xFF201310),
    tertiaryContainer = Color(0xFF3A2A22),
    onTertiaryContainer = Color(0xFFF1D9CB),
    background = Color(0xFF121110),
    onBackground = Color(0xFFE7E3D9),
    surface = Color(0xFF1B1A18),
    onSurface = Color(0xFFE7E3D9),
    surfaceVariant = Color(0xFF232120),
    onSurfaceVariant = Color(0xFFBDB8AD),
    outline = Color(0xFF807B70),
    outlineVariant = Color(0xFF34312C),
    error = Color(0xFFE0A8A0),
    onError = Color(0xFF2B0F0C),
    errorContainer = Color(0xFF3E2624),
    onErrorContainer = Color(0xFFF3D6D1),
)

/**
 * 维度强调色：年级/学科/版本各有一套“实色 + 浅底”，
 * 选中时用实色填充，未选中时用同色描边与文字，形成清晰的色彩分区。
 */
@Immutable
data class AccentPalette(
    val grade: Color,
    val gradeContainer: Color,
    val subject: Color,
    val subjectContainer: Color,
    val version: Color,
    val versionContainer: Color,
    val selection: Color,
    val selectionContainer: Color,
    val success: Color,
    val successContainer: Color,
    val gold: Color,
    val goldContainer: Color,
    val onAccent: Color,
)

private val DayAccents = AccentPalette(
    grade = Color(0xFF3E5C76),
    gradeContainer = Color(0xFFE2EAF3),
    subject = Color(0xFFA85B3B),
    subjectContainer = Color(0xFFF4E0D6),
    version = Color(0xFF5E7351),
    versionContainer = Color(0xFFE6ECDF),
    selection = Color(0xFFB4552F),
    selectionContainer = Color(0xFFF7DFD3),
    success = Color(0xFF4F6B57),
    successContainer = Color(0xFFE0E9E1),
    gold = Color(0xFFA9824B),
    goldContainer = Color(0xFFF2E8D5),
    onAccent = Color(0xFFFFFCF6),
)

private val NightAccents = AccentPalette(
    grade = Color(0xFF9DBAD8),
    gradeContainer = Color(0xFF24313E),
    subject = Color(0xFFE0A184),
    subjectContainer = Color(0xFF3A2A22),
    version = Color(0xFFAEC49E),
    versionContainer = Color(0xFF293426),
    selection = Color(0xFFE8A886),
    selectionContainer = Color(0xFF3E2B23),
    success = Color(0xFFA3C0AA),
    successContainer = Color(0xFF26332B),
    gold = Color(0xFFD8B47C),
    goldContainer = Color(0xFF3A3021),
    onAccent = Color(0xFF171614),
)

val LocalAccents = staticCompositionLocalOf { DayAccents }

@Composable
fun TextbookGoTheme(
    theme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit,
) {
    val night = theme.isNight(isSystemInDarkTheme())
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAccents provides if (night) NightAccents else DayAccents,
    ) {
        MaterialTheme(
            colorScheme = if (night) NightColorScheme else DayColorScheme,
            content = content,
        )
    }
}
