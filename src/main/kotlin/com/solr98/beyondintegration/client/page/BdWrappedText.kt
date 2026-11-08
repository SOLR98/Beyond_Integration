package com.solr98.beyondintegration.client.page

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wintercogs.beyonddimensions.client.ui.theme.Bd
import com.wintercogs.beyonddimensions.client.ui.theme.BdColors
import dev.compixel.ui.ore.display.OreText

/**
 * 通用多行说明文字。
 *
 * 宽度交给 Compose 的文本排版决定（按可用宽度自动折行，中文按字符断行、标点不会落到行首），
 * 不再手算像素宽度预切行——手算的宽度与像素字体、界面缩放、面板宽度都对不上。
 */
@Composable
internal fun BdWrappedText(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    OreText(
        text,
        modifier.fillMaxWidth(),
        color = Bd.colors[BdColors.faint],
        style = Bd.caption,
        maxLines = maxLines,
    )
}
