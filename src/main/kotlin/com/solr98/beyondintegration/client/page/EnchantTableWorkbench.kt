package com.solr98.beyondintegration.client.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.solr98.beyondintegration.ClientConfig
import com.solr98.beyondintegration.client.ApothPreviewState
import com.solr98.beyondintegration.client.EnchantSeparationState
import com.solr98.beyondintegration.feature.enchant.EnchantTableWidget
import com.solr98.beyondintegration.network.PacketHandler
import com.solr98.beyondintegration.network.RequestApothPreviewPacket
import com.solr98.beyondintegration.network.ToggleEnchantSeparationPacket
import com.wintercogs.beyonddimensions.api.ui.storage.StorageWorkbench
import com.wintercogs.beyonddimensions.api.ui.storage.StorageWorkbenchContext
import com.wintercogs.beyonddimensions.api.ui.storage.StorageWorkbenchScope
import com.wintercogs.beyonddimensions.api.ui.storage.StorageWorkbenchSize
import com.wintercogs.beyonddimensions.api.ui.storage.StorageWorkbenches
import com.wintercogs.beyonddimensions.api.ui.storage.WorkbenchClearButtons
import com.wintercogs.beyonddimensions.api.ui.storage.WorkbenchClearText
import com.wintercogs.beyonddimensions.client.ui.base.BdSlot
import com.wintercogs.beyonddimensions.client.ui.base.SLOT_PITCH
import com.wintercogs.beyonddimensions.client.ui.base.tr
import com.wintercogs.beyonddimensions.client.ui.kit.BdGlyphButton
import com.wintercogs.beyonddimensions.client.ui.kit.BdIcon
import com.wintercogs.beyonddimensions.client.ui.kit.BdIcons
import com.wintercogs.beyonddimensions.client.ui.kit.BdTooltip
import com.wintercogs.beyonddimensions.client.ui.kit.bdSlotFrame
import com.wintercogs.beyonddimensions.client.ui.theme.Bd
import com.wintercogs.beyonddimensions.client.ui.theme.BdColors
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu
import dev.compixel.forge.item.ItemIcon
import dev.compixel.forge.item.MinecraftItemIcon
import dev.compixel.ui.ore.display.OreText
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component

/** 附魔台的一个候选档位。 */
data class EnchantTableRow(
    val slot: Int,
    val name: String,
    val level: Int,
    val cost: Int,
)

data class EnchantTableState(
    val input: Int,
    val fuel: Int,
    val rows: List<EnchantTableRow>,
    val refreshEnabled: Boolean,
    val clear: WorkbenchClearText,
    /** 悬停预览开关（客户端偏好）。 */
    val previewOn: Boolean = true,
    /** 当前网络的附魔分离开关。 */
    val separationOn: Boolean = false,
    /** 三档完整预览附魔列表（服务端下发）。 */
    val previews: List<List<ApothPreviewState.Entry>> = emptyList(),
    /** 香草附魔台 3D 书本（原生绘制图标）。 */
    val book: ItemIcon? = null,
)

sealed interface EnchantTableAction {
    data class Enchant(val slot: Int) : EnchantTableAction
    data object Refresh : EnchantTableAction
    data object TogglePreview : EnchantTableAction
    data object Separate : EnchantTableAction
    data class Clear(val toStorage: Boolean) : EnchantTableAction
}

/**
 * 「网络附魔台」工作台（普通/原版规则，嵌入存储界面工作区）。
 * <p>布局与神化版一致：书本 + 装备/燃料槽同排 + 预览/刷新/附魔分离按钮 + 右侧三档候选（悬停预览）。
 */
object EnchantTableWorkbench : StorageWorkbench<EnchantTableState, EnchantTableAction> {

    private const val WIDTH = StorageWorkbenches.MAX_WIDTH

    override fun title(context: StorageWorkbenchContext): Component =
        Component.translatable("beyond_integration.workbench.enchant_table")

    override fun size(context: StorageWorkbenchContext): StorageWorkbenchSize =
        StorageWorkbenchSize(WIDTH, 68)

    override fun snapshot(context: StorageWorkbenchContext): EnchantTableState {
        val widget = context.menu.widget(EnchantTableWidget::class.java)
            ?: return EnchantTableState(0, 1, emptyList(), false, WorkbenchClearText())
        val rows = ArrayList<EnchantTableRow>(3)
        for (slot in 0 until 3) {
            val cost = widget.cost(slot)
            if (cost <= 0) continue
            val clue = widget.clue(slot)
            val ench = if (clue >= 0) enchantById(clue) else null
            val name = ench?.let { it.value().description.string }.orEmpty()
            rows.add(EnchantTableRow(slot, name, widget.level(slot), cost))
        }
        val netId = (context.menu as? DimensionsNetMenu)?.networkId() ?: -1
        // 首次显示时向服务端请求完整预览附魔列表
        context.remember("enchant_preview_req") {
            PacketHandler.sendToServer(RequestApothPreviewPacket())
            true
        }
        val anim = context.remember("book_anim") { BookAnim() }
        anim.hasCost = rows.isNotEmpty()
        anim.input = context.menu.slots[widget.inputStart()].item
        return EnchantTableState(
            input = widget.inputStart(),
            fuel = widget.fuelSlot(),
            rows = rows,
            refreshEnabled = widget.refreshEnabled(),
            clear = context.remember("clear") { WorkbenchClearText() },
            previewOn = ClientConfig.enchantPreviewOn(),
            separationOn = netId >= 0 && EnchantSeparationState.INSTANCE.get(netId),
            previews = ApothPreviewState.get(context.menu.containerId),
            book = context.remember("book") { createEnchantBookIcon(anim) },
        )
    }

    override fun handle(context: StorageWorkbenchContext, action: EnchantTableAction) {
        when (action) {
            is EnchantTableAction.Enchant -> context.request(EnchantTableWidget.ENCHANT, action.slot)
            EnchantTableAction.Refresh -> context.request(EnchantTableWidget.REFRESH, false)
            EnchantTableAction.TogglePreview ->
                ClientConfig.setEnchantPreviewOn(!ClientConfig.enchantPreviewOn())
            EnchantTableAction.Separate -> context.request(EnchantTableWidget.SEPARATE, false)
            is EnchantTableAction.Clear -> context.clearItems(action.toStorage)
        }
    }

    @Composable
    override fun Actions(state: EnchantTableState, scope: StorageWorkbenchScope<EnchantTableAction>) {
        WorkbenchClearButtons(state.clear) { scope.send(EnchantTableAction.Clear(it)) }
    }

    @Composable
    override fun Content(state: EnchantTableState, scope: StorageWorkbenchScope<EnchantTableAction>) {
        val colors = Bd.colors
        Row(Modifier.width(WIDTH.dp)) {
            // 左列：书本 + 装备/燃料槽同一行 + 预览/刷新/附魔分离按钮
            Column(
                Modifier.width(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val book = state.book
                if (book != null) {
                    MinecraftItemIcon(book, Modifier.size(ENCHANT_BOOK_SIZE.dp))
                } else {
                    Spacer(Modifier.height(ENCHANT_BOOK_SIZE.dp))
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.bdSlotFrame(colors)) { BdSlot(scope.slots, state.input) }
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier.bdSlotFrame(colors)) { BdSlot(scope.slots, state.fuel) }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BdGlyphButton(
                        BdIcons.Refresh,
                        tr("beyond_integration.workbench.enchant_table.refresh"),
                        { scope.send(EnchantTableAction.Refresh) },
                        enabled = state.refreshEnabled,
                        size = 11.dp,
                    )
                    Spacer(Modifier.width(2.dp))
                    BdGlyphButton(
                        BdIcons.ToStorage,
                        tr("beyond_integration.workbench.enchant_table.separation"),
                        { scope.send(EnchantTableAction.Separate) },
                        size = 11.dp,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            // 右列：三档候选（符文图标 + 名称 + 右侧绿色费用；悬停显示预览）
            Column(Modifier.weight(1f)) {
                if (state.rows.isEmpty()) {
                    OreText(
                        tr("beyond_integration.workbench.enchant_table.empty"),
                        color = colors[BdColors.faint],
                        style = Bd.caption,
                    )
                } else {
                    for (row in state.rows) {
                        val interaction = remember(row.slot) { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        BdTooltip(
                            tooltip = {
                                val entries = state.previews.getOrNull(row.slot).orEmpty()
                                if (entries.isNotEmpty()) {
                                    for (e in entries) {
                                        val ench = enchantById(e.enchantId())
                                        val nm = ench?.let { it.value().description.string }.orEmpty()
                                        OreText(
                                            Component.translatable(
                                                "beyond_integration.workbench.enchant_table.preview.entry",
                                                nm,
                                                enchantLevelText(e.level()),
                                            ).string,
                                            color = colors[BdColors.text],
                                            style = Bd.caption,
                                        )
                                    }
                                } else {
                                    OreText(
                                        Component.translatable(
                                            "beyond_integration.workbench.enchant_table.preview.entry",
                                            row.name,
                                            enchantLevelText(row.level),
                                        ).string,
                                        color = colors[BdColors.text],
                                        style = Bd.caption,
                                    )
                                }
                                OreText(
                                    Component.translatable(
                                        "beyond_integration.workbench.enchant_table.preview.gate_level",
                                        row.cost,
                                    ).string,
                                    color = colors[BdColors.online],
                                    style = Bd.caption,
                                )
                                OreText(
                                    Component.translatable(
                                        "beyond_integration.workbench.enchant_table.preview.level_cost",
                                        row.slot + 1,
                                    ).string,
                                    color = colors[BdColors.faint],
                                    style = Bd.caption,
                                )
                            },
                            enabled = state.previewOn,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().height(SLOT_PITCH.dp)
                                    .hoverable(interaction)
                                    .background(if (hovered) colors[BdColors.accentSoft] else Color.Transparent)
                                    .clickable { scope.send(EnchantTableAction.Enchant(row.slot)) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BdIcon(ENCHANT_RUNE_ICON, color = colors[BdColors.accentDeep])
                                Spacer(Modifier.width(3.dp))
                                OreText(
                                    if (row.name.isEmpty()) enchantLevelText(row.level)
                                    else "${row.name} ${enchantLevelText(row.level)}",
                                    modifier = Modifier.weight(1f),
                                    color = colors[BdColors.text],
                                    style = Bd.caption,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.width(4.dp))
                                OreText(
                                    "${row.slot + 1}",
                                    color = colors[BdColors.online],
                                    style = Bd.body,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 按数字 id 取附魔（1.21.1 起附魔是 datapack 注册表：Registries.ENCHANTMENT）。
 * 无世界上下文时退化为 null，界面只是显示名字用。
 */
private fun enchantById(id: Int): net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>? {
    if (id < 0) return null
    val level = net.minecraft.client.Minecraft.getInstance().level ?: return null
    return level.registryAccess()
        .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
        .asHolderIdMap()
        .byId(id)
}