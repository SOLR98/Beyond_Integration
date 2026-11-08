package com.solr98.beyondintegration.client.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wintercogs.beyonddimensions.api.ui.page.BdPage
import com.wintercogs.beyonddimensions.api.ui.page.BdPageContext
import com.wintercogs.beyonddimensions.api.ui.page.BdPageScope
import com.wintercogs.beyonddimensions.api.ui.page.BdPageSize
import com.wintercogs.beyonddimensions.client.ui.base.SLOT_WINDOW_WIDTH
import com.wintercogs.beyonddimensions.client.ui.kit.BdAmountPill
import com.wintercogs.beyonddimensions.client.ui.kit.BdChoiceSetting
import com.wintercogs.beyonddimensions.client.ui.kit.BdChip
import com.wintercogs.beyonddimensions.client.ui.kit.BdPixelIcon
import com.wintercogs.beyonddimensions.client.ui.kit.BdSectionDivider
import com.wintercogs.beyonddimensions.client.ui.kit.BdSectionLabel
import com.wintercogs.beyonddimensions.client.ui.kit.BdSettingRow
import com.wintercogs.beyonddimensions.client.ui.kit.BdToggle
import com.wintercogs.beyonddimensions.client.ui.kit.BdTooltip
import com.wintercogs.beyonddimensions.client.ui.kit.SIDE_RAIL_WIDTH
import com.wintercogs.beyonddimensions.client.ui.kit.bdInset
import com.wintercogs.beyonddimensions.client.ui.kit.bdRaised
import com.wintercogs.beyonddimensions.client.ui.kit.bdSlotFrame
import com.wintercogs.beyonddimensions.client.ui.kit.brackets
import com.wintercogs.beyonddimensions.client.ui.theme.Bd
import com.wintercogs.beyonddimensions.client.ui.theme.BdColors
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu
import dev.compixel.ui.ore.display.OreText
import dev.compixel.ui.ore.input.OreSlider
import net.minecraft.network.chat.Component

/**
 * 「排版与布局」预览页（开发环境）：把 BD 的排版组件与修饰符各展示一份，
 * 便于调工作台页时对照间距、分组、面板与交互效果。
 */
object LayoutGalleryPage : BdPage<DimensionsNetMenu, Unit, Unit> {

    private const val PAGE_WIDTH = SLOT_WINDOW_WIDTH - SIDE_RAIL_WIDTH

    override val icon: BdPixelIcon = BdPixelIcon(
        "..........",
        ".#......#.",
        ".#......#.",
        ".########.",
        ".#......#.",
        ".#......#.",
        ".########.",
        ".#......#.",
        ".#......#.",
        "..........",
    )

    override fun title(context: BdPageContext<DimensionsNetMenu>): Component =
        Component.translatable("beyond_integration.page.layout_gallery.title")

    override fun size(context: BdPageContext<DimensionsNetMenu>): BdPageSize =
        BdPageSize.Content(maxWidth = PAGE_WIDTH)

    override fun snapshot(context: BdPageContext<DimensionsNetMenu>) {}

    @Composable
    override fun ColumnScope.Content(state: Unit, scope: BdPageScope<Unit>) {
        val colors = Bd.colors
        var toggle by remember { mutableStateOf(true) }
        var slider by remember { mutableStateOf(35f) }

        BdSectionLabel("分组标题 / Section label")
        OreText("说明文字使用 Bd.caption 或 Bd.body。", color = colors[BdColors.faint], style = Bd.caption)
        BdSectionDivider()

        BdSectionLabel("面板 / Panels")
        Row {
            Box(Modifier.size(72.dp, 20.dp).bdRaised(colors), contentAlignment = Alignment.Center) {
                OreText("raised", color = colors[BdColors.text], style = Bd.caption)
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(72.dp, 20.dp).bdInset(colors), contentAlignment = Alignment.Center) {
                OreText("inset", color = colors[BdColors.text], style = Bd.caption)
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(20.dp).bdSlotFrame(colors))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(20.dp).brackets(colors[BdColors.accent]))
        }
        Spacer(Modifier.height(4.dp))

        BdSectionLabel("数量 / Amount")
        Row(verticalAlignment = Alignment.CenterVertically) {
            BdAmountPill("12345")
            Spacer(Modifier.width(8.dp))
            BdChip(onClick = {}) {
                OreText("Chip", color = colors[BdColors.text], style = Bd.caption)
            }
            Spacer(Modifier.width(4.dp))
            BdTooltip("悬停提示 / tooltip") {
                OreText("悬停这里", color = colors[BdColors.accentDeep], style = Bd.caption)
            }
        }
        Spacer(Modifier.height(4.dp))

        BdSectionLabel("设置行 / Setting rows")
        BdSettingRow("开关标题", "说明文字") {
            BdToggle(toggle, onCheckedChange = { toggle = it })
        }
        BdChoiceSetting("滑条标题") {
            OreSlider(slider, { slider = it }, valueRange = 0f..100f)
        }
    }
}
