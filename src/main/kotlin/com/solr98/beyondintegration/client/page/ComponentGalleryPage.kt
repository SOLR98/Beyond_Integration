package com.solr98.beyondintegration.client.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wintercogs.beyonddimensions.api.ui.page.BdPage
import com.wintercogs.beyonddimensions.api.ui.page.BdPageContext
import com.wintercogs.beyonddimensions.api.ui.page.BdPageScope
import com.wintercogs.beyonddimensions.api.ui.page.BdPageSize
import com.wintercogs.beyonddimensions.client.ui.base.SLOT_WINDOW_WIDTH
import com.wintercogs.beyonddimensions.client.ui.kit.BdChip
import com.wintercogs.beyonddimensions.client.ui.kit.BdChoiceSetting
import com.wintercogs.beyonddimensions.client.ui.kit.BdCycler
import com.wintercogs.beyonddimensions.client.ui.kit.BdDropdown
import com.wintercogs.beyonddimensions.client.ui.kit.BdGlyphButton
import com.wintercogs.beyonddimensions.client.ui.kit.BdIcons
import com.wintercogs.beyonddimensions.client.ui.kit.BdNumberEditor
import com.wintercogs.beyonddimensions.client.ui.kit.BdPixelIcon
import com.wintercogs.beyonddimensions.client.ui.kit.BdSectionLabel
import com.wintercogs.beyonddimensions.client.ui.kit.BdSegmented
import com.wintercogs.beyonddimensions.client.ui.kit.BdSettingRow
import com.wintercogs.beyonddimensions.client.ui.kit.BdToggle
import com.wintercogs.beyonddimensions.client.ui.kit.SIDE_RAIL_WIDTH
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu
import dev.compixel.ui.ore.button.OreButton
import dev.compixel.ui.ore.button.OreButtonStyle
import dev.compixel.ui.ore.button.OreIconButton
import dev.compixel.ui.ore.display.OreGlyph
import dev.compixel.ui.ore.display.OreText
import dev.compixel.ui.ore.input.OreDoubleField
import dev.compixel.ui.ore.input.OreIntField
import dev.compixel.ui.ore.input.OreLongField
import dev.compixel.ui.ore.input.OreSlider
import dev.compixel.ui.ore.input.OreTextField
import dev.compixel.ui.ore.navigation.OreTab
import dev.compixel.ui.ore.selection.OreCheckbox
import net.minecraft.network.chat.Component

/**
 * 「组件预览（调试）」页：把 BD 0.8.x 可用的按钮与选项组件各渲染一份，方便确认外观与交互。
 * <p>仅在开发环境注册（`ClientSetup` 里用 `FMLEnvironment.production` 判断），
 * 注入 BD 存储/终端界面（`DimensionsNetMenu`，终端菜单继承它）。
 * <p>窗口比常规页更宽（`SLOT_WINDOW_WIDTH * 2`），并且含多个控件的行都用 `BdChoiceSetting`
 * 让控件独占一行，避免把左侧标题/说明挤成多行。
 */
object ComponentGalleryPage : BdPage<DimensionsNetMenu, Unit, Unit> {

    /** 调试页用双倍槽宽，方便一行放下多个按钮。 */
    private const val PAGE_WIDTH = SLOT_WINDOW_WIDTH * 2 - SIDE_RAIL_WIDTH

    override val icon: BdPixelIcon = BdPixelIcon(
        "..........",
        ".########.",
        ".#......#.",
        ".#.#..#.#.",
        ".#......#.",
        ".#.#..#.#.",
        ".#......#.",
        ".#..#...#.",
        ".########.",
        "..........",
    )

    override fun title(context: BdPageContext<DimensionsNetMenu>): Component =
        Component.translatable("beyond_integration.page.component_gallery.title")

    override fun size(context: BdPageContext<DimensionsNetMenu>): BdPageSize =
        BdPageSize.Content(maxWidth = PAGE_WIDTH)

    override fun snapshot(context: BdPageContext<DimensionsNetMenu>) {}

    @Composable
    override fun ColumnScope.Content(state: Unit, scope: BdPageScope<Unit>) {
        var toggle by remember { mutableStateOf(true) }
        var chipActive by remember { mutableStateOf(false) }
        var segmented by remember { mutableStateOf(0) }
        var cycler by remember { mutableStateOf(0) }
        var dropdown by remember { mutableStateOf(0) }
        var number by remember { mutableStateOf(10) }
        var checkbox by remember { mutableStateOf(true) }
        var slider by remember { mutableStateOf(0.5f) }
        var intField by remember { mutableStateOf(1) }
        var longField by remember { mutableStateOf(1L) }
        var doubleField by remember { mutableStateOf(1.0) }
        var text by remember { mutableStateOf("文本") }
        var tabIndex by remember { mutableStateOf(0) }

        Column(Modifier.width(PAGE_WIDTH.dp)) {
            BdSectionLabel("按钮 · BD kit")
            BdChoiceSetting("BdGlyphButton", "图标按钮；description 为悬停提示") {
                Row {
                    BdGlyphButton(BdIcons.Settings, "设置", {})
                    Spacer(Modifier.width(6.dp))
                    BdGlyphButton(BdIcons.Close, "关闭（禁用）", {}, enabled = false)
                }
            }
            BdChoiceSetting("BdChip", "小按钮；可 active / 禁用") {
                Row {
                    BdChip(onClick = { chipActive = !chipActive }, active = chipActive) { OreText("Chip") }
                    Spacer(Modifier.width(6.dp))
                    BdChip(onClick = {}, enabled = false) { OreText("禁用") }
                }
            }
            BdChoiceSetting("BdSegmented", "互斥分段选择") {
                BdSegmented(listOf("甲", "乙", "丙"), segmented, { segmented = it })
            }

            Spacer(Modifier.height(8.dp))
            BdSectionLabel("按钮 · Compixel Ore")
            BdChoiceSetting("OreButton", "Primary / Secondary / Destructive / Quiet / 禁用") {
                Row {
                    OreButton("主要", {}, style = OreButtonStyle.Primary)
                    Spacer(Modifier.width(4.dp))
                    OreButton("次要", {}, style = OreButtonStyle.Secondary)
                    Spacer(Modifier.width(4.dp))
                    OreButton("危险", {}, style = OreButtonStyle.Destructive)
                    Spacer(Modifier.width(4.dp))
                    OreButton("安静", {}, style = OreButtonStyle.Quiet)
                    Spacer(Modifier.width(4.dp))
                    OreButton("禁用", {}, enabled = false)
                }
            }
            BdChoiceSetting("OreIconButton", "glyph 图标按钮") {
                Row {
                    OreIconButton(OreGlyph.Plus, "添加", {})
                    Spacer(Modifier.width(6.dp))
                    OreIconButton(OreGlyph.Cross, "关闭（禁用）", {}, enabled = false)
                }
            }

            Spacer(Modifier.height(8.dp))
            BdSectionLabel("选项 · BD kit")
            BdChoiceSetting("BdToggle", "参数顺序：(checked, onCheckedChange, enabled)") {
                Row {
                    BdToggle(toggle, onCheckedChange = { toggle = it })
                    Spacer(Modifier.width(10.dp))
                    BdToggle(false, onCheckedChange = {}, enabled = false)
                    Spacer(Modifier.width(6.dp))
                    OreText("左：可用　右：禁用")
                }
            }
            BdChoiceSetting("BdCycler", "循环选择；自带 fillMaxWidth，独占一行") {
                BdCycler(listOf("低", "中", "高"), cycler, true, { cycler = it })
            }
            BdChoiceSetting("BdDropdown", "下拉选择") {
                BdDropdown(listOf("A", "B", "C"), dropdown, true, { dropdown = it })
            }
            BdChoiceSetting("BdNumberEditor（Int）", "范围 0..100") {
                Row {
                    BdNumberEditor(number, { number = it }, 0..100)
                    Spacer(Modifier.width(10.dp))
                    OreText("当前值：$number")
                }
            }

            Spacer(Modifier.height(8.dp))
            BdSectionLabel("选项 · Compixel")
            BdChoiceSetting("OreCheckbox", "带文字标签") {
                OreCheckbox(checkbox, { checkbox = it }, label = "勾选")
            }
            BdChoiceSetting("OreSlider", "0..1 滑条") {
                OreSlider(slider, { slider = it }, valueRange = 0f..1f)
            }
            BdChoiceSetting("OreIntField", "整数输入") {
                OreIntField(intField, { intField = it })
            }
            BdChoiceSetting("OreLongField", "长整数输入") {
                OreLongField(longField, { longField = it })
            }
            BdChoiceSetting("OreDoubleField", "小数输入") {
                OreDoubleField(doubleField, { doubleField = it })
            }
            BdChoiceSetting("OreTextField", "文本输入") {
                OreTextField(text, { text = it })
            }
            BdChoiceSetting("OreTab", "页签样式按钮") {
                Row {
                    OreTab("页签一", tabIndex == 0, onClick = { tabIndex = 0 })
                    Spacer(Modifier.width(6.dp))
                    OreTab("页签二", tabIndex == 1, onClick = { tabIndex = 1 })
                }
            }
        }
    }
}
