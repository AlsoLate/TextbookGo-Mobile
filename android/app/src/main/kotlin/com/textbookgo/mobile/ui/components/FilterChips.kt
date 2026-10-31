package com.textbookgo.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 筛选行左侧的维度名，颜色与对应 chip 一致。 */
@Composable
fun FilterLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier.width(38.dp),
    )
}

/**
 * 维度筛选 chip：未选中为同色描边 + 浅色底，选中为实色高亮。
 * 每一行用不同强调色，避免年级/学科/版本混在一起。
 */
@Composable
fun AccentFilterChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        },
        shape = RoundedCornerShape(999.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = accent.copy(alpha = 0.07f),
            labelColor = accent,
            selectedContainerColor = accent,
            selectedLabelColor = onAccent,
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else accent.copy(alpha = 0.35f),
        ),
        elevation = FilterChipDefaults.filterChipElevation(
            elevation = 0.dp,
            pressedElevation = 0.dp,
        ),
        modifier = modifier,
    )
}
