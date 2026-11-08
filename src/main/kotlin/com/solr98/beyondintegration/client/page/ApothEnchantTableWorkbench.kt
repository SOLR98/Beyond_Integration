package com.solr98.beyondintegration.client.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.solr98.beyondintegration.ClientConfig
import com.solr98.beyondintegration.client.ApothPreviewState
import com.solr98.beyondintegration.client.EnchantSeparationState
import com.solr98.beyondintegration.client.render.EnchantBookRender
import com.solr98.beyondintegration.feature.enchant.ApothEnchantTableWidget
import com.solr98.beyondintegration.network.PacketHandler
import com.solr98.beyondintegration.network.RequestApothPreviewPacket
import com.solr98.beyondintegration.network.RequestSacrificePreviewPacket
import com.solr98.beyondintegration.network.ToggleEnchantSeparationPacket
import dev.shadowsoffire.apotheosis.util.ApothMiscUtil
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu
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
import com.wintercogs.beyonddimensions.client.ui.kit.BdChoiceSetting
import com.wintercogs.beyonddimensions.client.ui.kit.BdGlyphButton
import com.wintercogs.beyonddimensions.client.ui.kit.BdIcon
import com.wintercogs.beyonddimensions.client.ui.kit.BdIcons
import com.wintercogs.beyonddimensions.client.ui.kit.BdPixelIcon
import com.wintercogs.beyonddimensions.client.ui.kit.BdPopover
import com.wintercogs.beyonddimensions.client.ui.kit.BdScrollColumn
import com.wintercogs.beyonddimensions.client.ui.kit.BdSectionDivider
import com.wintercogs.beyonddimensions.client.ui.kit.BdSectionLabel
import com.wintercogs.beyonddimensions.client.ui.kit.BdSettingRow
import com.wintercogs.beyonddimensions.client.ui.kit.BdToggle
import com.wintercogs.beyonddimensions.client.ui.kit.BdTooltip
import com.wintercogs.beyonddimensions.client.ui.kit.bdSlotFrame
import com.wintercogs.beyonddimensions.client.ui.theme.Bd
import com.wintercogs.beyonddimensions.client.ui.theme.BdColors
import dev.compixel.forge.drawing.NativeRefresh
import dev.compixel.forge.item.ItemIcon
import dev.compixel.forge.item.MinecraftItemIcon
import dev.compixel.ui.ore.display.OreText
import dev.compixel.ui.ore.input.OreSlider
import net.minecraft.client.Minecraft
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import java.util.function.Consumer
import kotlin.math.roundToInt

/** 候选行左侧的符文图标在 {@link ENCHANT_RUNE_ICON}（EnchantBookIcon.kt）。 */

data class ApothEnchantRow(
    val slot: Int,
    val name: String,
    val level: Int,
    val cost: Int,
    /** 该档实际消耗的经验点数（Apothic：由附魔强度换算）。 */
    val xpPoints: Int,
)

/** 过滤列表里的一个附魔项。 */
data class ApothEnchantOption(val id: String, val name: String)

data class ApothEnchantState(
    val input: Int,
    val fuel: Int,
    val rows: List<ApothEnchantRow>,
    val eterna: Int,
    val quanta: Int,
    val arcana: Int,
    val refreshEnabled: Boolean,
    val clear: WorkbenchClearText,
    /** 悬停预览开关（客户端偏好）。 */
    val previewOn: Boolean = true,
    /** 当前网络的附魔分离开关。 */
    val separationOn: Boolean = false,
    /** 三档完整预览附魔列表（服务端下发）。 */
    val previews: List<List<ApothPreviewState.Entry>> = emptyList(),
    /** 稳定（1.21.1 的 Apothic stable 布尔：稳定时不再滚出负向波动）。 */
    val stable: Boolean = false,
    /** 已献祭书架提供的四参数上限。 */
    val maxEterna: Int = 0,
    val maxQuanta: Int = 0,
    val maxArcana: Int = 0,
    val maxStable: Boolean = false,
    /** 书架黑名单过滤开关 / 已读取的黑名单数量。 */
    val filterEnabled: Boolean = false,
    val filteredCount: Int = 0,
    /** 已过滤附魔（id + 显示名）。 */
    val filteredEnchants: List<ApothEnchantOption> = emptyList(),
    /** 可添加过滤的附魔（装备槽为空时=全部，否则=该装备可附魔项）。 */
    val availableEnchants: List<ApothEnchantOption> = emptyList(),
    /** 宝藏附魔是否可用（全局配置或献祭过宝藏书架）。 */
    val treasureAllowed: Boolean = false,
    /** 是否需要献祭书架提升上限（false 时隐藏献祭按钮）。 */
    val requireSacrifice: Boolean = true,
    /** 献祭预览（悬浮献祭按钮时显示：当前属性 + 献祭后变动）。 */
    val sacrificePreview: com.solr98.beyondintegration.client.ApothSacrificePreviewState.Preview? = null,
    /** 香草附魔台 3D 书本（原生绘制图标）。 */
    val book: ItemIcon? = null,
)

sealed interface ApothEnchantAction {
    data class Enchant(val slot: Int) : ApothEnchantAction
    data class Stats(val eterna: Int, val quanta: Int, val arcana: Int, val stable: Boolean) : ApothEnchantAction
    data object Refresh : ApothEnchantAction
    data object Sacrifice : ApothEnchantAction
    data object ToggleFilter : ApothEnchantAction
    data class AddFilter(val id: String) : ApothEnchantAction
    data class RemoveFilter(val id: String) : ApothEnchantAction
    data object TogglePreview : ApothEnchantAction
    data object Separate : ApothEnchantAction
    data class Clear(val toStorage: Boolean) : ApothEnchantAction
}

/**
 * 「网络附魔台（神化）」工作台（Apothic 规则，嵌入存储界面工作区）。
 * <p>与普通附魔台相同：装备槽 + 燃料槽 + 三档候选；额外提供 eterna / quanta / arcana 三个参数，
 * 通过 `Actions` 行的「参数」按钮打开 Popup 滑条调整（Popup 不受工作区 173×76 裁剪）。
 */
object ApothEnchantTableWorkbench : StorageWorkbench<ApothEnchantState, ApothEnchantAction> {

    private const val WIDTH = StorageWorkbenches.MAX_WIDTH

    override fun title(context: StorageWorkbenchContext): Component =
        Component.translatable("beyond_integration.workbench.apoth_enchant_table")

    override fun size(context: StorageWorkbenchContext): StorageWorkbenchSize =
        StorageWorkbenchSize(WIDTH, 68)

    override fun snapshot(context: StorageWorkbenchContext): ApothEnchantState {
        val widget = context.menu.widget(ApothEnchantTableWidget::class.java)
            ?: return ApothEnchantState(0, 1, emptyList(), 0, 0, 0, false, WorkbenchClearText())
        val rows = ArrayList<ApothEnchantRow>(3)
        for (slot in 0 until 3) {
            val cost = widget.cost(slot)
            if (cost <= 0) continue
            val clue = widget.clue(slot)
            val ench = if (clue >= 0) apothEnchantById(clue) else null
            val name = ench?.let { it.value().description.string }.orEmpty()
            rows.add(ApothEnchantRow(slot, name, widget.level(slot), cost, ApothMiscUtil.getExpCostForSlot(cost, slot)))
        }
        val netId = (context.menu as? DimensionsNetMenu)?.networkId() ?: -1
        val inputStack = context.menu.slots[widget.inputStart()].item
        val availableEnchants = ArrayList<ApothEnchantOption>()
        for (holder in apothAllEnchants()) {
            val ench = holder.value()
            if (inputStack.isEmpty() || ench.canEnchant(inputStack)) {
                val rl = holder.unwrapKey().orElse(null) ?: continue
                availableEnchants.add(
                    ApothEnchantOption(rl.location().toString(), ench.description.string),
                )
            }
        }
        val filteredEnchants = ApothPreviewState.filtered(context.menu.containerId).map { id ->
            val rl = net.minecraft.resources.ResourceLocation.tryParse(id)
            val ench = rl?.let { apothEnchantByKey(it) }
            ApothEnchantOption(id, ench?.let { it.value().description.string } ?: id)
        }
        // 首次显示时向服务端请求完整预览附魔列表
        context.remember("apoth_preview_req") {
            PacketHandler.sendToServer(RequestApothPreviewPacket())
            true
        }
        // 献祭预览：手持书架（或其数量）变化时向服务端要一次，供献祭按钮悬浮提示显示
        val carried = context.menu.carried
        val carriedFp = "${net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(carried.item)}|${carried.count}"
        val fpRef = context.remember("apoth_sac_fp") { java.util.concurrent.atomic.AtomicReference("") }
        if (fpRef.get() != carriedFp) {
            fpRef.set(carriedFp)
            PacketHandler.sendToServer(RequestSacrificePreviewPacket())
        }
        // 原版书本动画状态：有费用则开书，输入变化触发翻页
        val anim = context.remember("book_anim") { BookAnim() }
        anim.hasCost = rows.isNotEmpty()
        anim.input = context.menu.slots[widget.inputStart()].item
        return ApothEnchantState(
            input = widget.inputStart(),
            fuel = widget.fuelSlot(),
            rows = rows,
            eterna = widget.eterna(),
            quanta = widget.quanta(),
            arcana = widget.arcana(),
            refreshEnabled = widget.refreshEnabled(),
            clear = context.remember("clear") { WorkbenchClearText() },
            previewOn = ClientConfig.enchantPreviewOn(),
            separationOn = netId >= 0 && EnchantSeparationState.INSTANCE.get(netId),
            previews = ApothPreviewState.get(context.menu.containerId),
            stable = widget.stable(),
            maxEterna = widget.maxEterna(),
            maxQuanta = widget.maxQuanta(),
            maxArcana = widget.maxArcana(),
            maxStable = widget.maxStable(),
            filterEnabled = widget.filterEnabled(),
            filteredCount = widget.filteredCount(),
            filteredEnchants = filteredEnchants,
            availableEnchants = availableEnchants,
            treasureAllowed = widget.treasureAllowed(),
            requireSacrifice = widget.requireSacrifice(),
            sacrificePreview = com.solr98.beyondintegration.client.ApothSacrificePreviewState.get(context.menu.containerId),
            book = context.remember("book") { createBookIcon(anim) },
        )
    }

    /** 旧版神化附魔台的 3D 书本（原生绘制，动画与原版附魔台一致）。 */
    private fun createBookIcon(anim: BookAnim): ItemIcon = createEnchantBookIcon(anim)

    override fun handle(context: StorageWorkbenchContext, action: ApothEnchantAction) {
        when (action) {
            is ApothEnchantAction.Enchant -> context.request(ApothEnchantTableWidget.ENCHANT, action.slot)
            is ApothEnchantAction.Stats -> context.request(
                ApothEnchantTableWidget.SET_STATS,
                ApothEnchantTableWidget.Stats(action.eterna, action.quanta, action.arcana, action.stable),
            )
            ApothEnchantAction.Refresh -> context.request(ApothEnchantTableWidget.REFRESH, false)
            ApothEnchantAction.Sacrifice ->
                context.request(ApothEnchantTableWidget.SACRIFICE, false)
            ApothEnchantAction.ToggleFilter ->
                context.request(ApothEnchantTableWidget.TOGGLE_FILTER, false)
            is ApothEnchantAction.AddFilter ->
                context.request(ApothEnchantTableWidget.ADD_FILTER, action.id)
            is ApothEnchantAction.RemoveFilter ->
                context.request(ApothEnchantTableWidget.REMOVE_FILTER, action.id)
            ApothEnchantAction.TogglePreview ->
                ClientConfig.setEnchantPreviewOn(!ClientConfig.enchantPreviewOn())
            ApothEnchantAction.Separate -> context.request(ApothEnchantTableWidget.SEPARATE, false)
            is ApothEnchantAction.Clear -> context.clearItems(action.toStorage)
        }
    }

    @Composable
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun Actions(state: ApothEnchantState, scope: StorageWorkbenchScope<ApothEnchantAction>) {
        var open by remember { mutableStateOf(false) }
        var filterOpen by remember { mutableStateOf(false) }
        var leftPage by remember { mutableStateOf(0) }
        var rightPage by remember { mutableStateOf(0) }
        BdGlyphButton(
            BdIcons.Settings,
            tr("beyond_integration.workbench.apoth_enchant_table.stats"),
            { open = !open },
            size = 11.dp,
        )
        Spacer(Modifier.width(2.dp))
        if (state.requireSacrifice) {
        BdTooltip(tooltip = { SacrificePreviewTooltip(state) }) {
        BdGlyphButton(
        BdIcons.ToStorage,
        tr("beyond_integration.workbench.apoth_enchant_table.sacrifice"),
        { scope.send(ApothEnchantAction.Sacrifice) },
        size = 11.dp,
        )
        }
        Spacer(Modifier.width(2.dp))
        }
        BdGlyphButton(
            BdIcons.Funnel,
            tr("beyond_integration.workbench.apoth_enchant_table.filter_list"),
            { filterOpen = !filterOpen },
            size = 11.dp,
        )
        Spacer(Modifier.width(2.dp))
        WorkbenchClearButtons(state.clear) { scope.send(ApothEnchantAction.Clear(it)) }
        if (open) {
            BdPopover(onDismissRequest = { open = false }) {
                StatsRow("beyond_integration.workbench.apoth_enchant_table.eterna", state.eterna, state.maxEterna) { v ->
                    scope.send(ApothEnchantAction.Stats(v, state.quanta, state.arcana, state.stable))
                }
                StatsRow("beyond_integration.workbench.apoth_enchant_table.quanta", state.quanta, state.maxQuanta) { v ->
                    scope.send(ApothEnchantAction.Stats(state.eterna, v, state.arcana, state.stable))
                }
                StatsRow("beyond_integration.workbench.apoth_enchant_table.arcana", state.arcana, state.maxArcana) { v ->
                    scope.send(ApothEnchantAction.Stats(state.eterna, state.quanta, v, state.stable))
                }
                BdSettingRow(tr("beyond_integration.workbench.apoth_enchant_table.stability"),
                    tr(if (state.maxStable) "beyond_integration.workbench.apoth_enchant_table.stability.desc.available"
                       else "beyond_integration.workbench.apoth_enchant_table.stability.desc.locked")) {
                    BdToggle(state.stable && state.maxStable,
                        onCheckedChange = { on ->
                            if (state.maxStable) scope.send(ApothEnchantAction.Stats(
                                state.eterna, state.quanta, state.arcana, on))
                        })
                }
                Spacer(Modifier.height(3.dp))
                BdSettingRow(
                    Component.translatable(
                        "beyond_integration.workbench.apoth_enchant_table.filter",
                        state.filteredCount,
                    ).string,
                ) {
                    BdToggle(state.filterEnabled, onCheckedChange = { scope.send(ApothEnchantAction.ToggleFilter) })
                }
            }
        }
        if (filterOpen) {
            val pageSize = 6
            val addable = state.availableEnchants.filter { a -> state.filteredEnchants.none { it.id == a.id } }
            val leftPages = maxOf(1, (state.filteredEnchants.size + pageSize - 1) / pageSize)
            val rightPages = maxOf(1, (addable.size + pageSize - 1) / pageSize)
            val lp = leftPage.coerceIn(0, leftPages - 1)
            val rp = rightPage.coerceIn(0, rightPages - 1)
            BdPopover(onDismissRequest = { filterOpen = false }) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).onPointerEvent(PointerEventType.Scroll) { event ->
                        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy > 0f) {
                            if (lp + 1 < leftPages) leftPage = lp + 1
                        } else if (dy < 0f) {
                            if (lp > 0) leftPage = lp - 1
                        }
                    }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            BdSectionLabel(
                                tr("beyond_integration.workbench.apoth_enchant_table.filtered_list"),
                                Modifier.weight(1f),
                            )
                            Pager(lp, leftPages) { leftPage = it }
                        }
                        if (state.filteredEnchants.isEmpty()) {
                            OreText(
                                tr("beyond_integration.workbench.apoth_enchant_table.filter_empty"),
                                color = Bd.colors[BdColors.faint],
                                style = Bd.caption,
                            )
                        } else {
                            val start = lp * pageSize
                            val end = minOf(start + pageSize, state.filteredEnchants.size)
                            for (i in start until end) {
                                val f = state.filteredEnchants[i]
                                Row(
                                    Modifier.fillMaxWidth().height(SLOT_PITCH.dp)
                                        .clickable { scope.send(ApothEnchantAction.RemoveFilter(f.id)) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OreText(
                                        "[-] ${f.name}",
                                        modifier = Modifier.weight(1f),
                                        color = Bd.colors[BdColors.text],
                                        style = Bd.caption,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f).onPointerEvent(PointerEventType.Scroll) { event ->
                        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy > 0f) {
                            if (rp + 1 < rightPages) rightPage = rp + 1
                        } else if (dy < 0f) {
                            if (rp > 0) rightPage = rp - 1
                        }
                    }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            BdSectionLabel(
                                tr("beyond_integration.workbench.apoth_enchant_table.addable_list"),
                                Modifier.weight(1f),
                            )
                            Pager(rp, rightPages) { rightPage = it }
                        }
                        val start = rp * pageSize
                        val end = minOf(start + pageSize, addable.size)
                        for (i in start until end) {
                            val a = addable[i]
                            Row(
                                Modifier.fillMaxWidth().height(SLOT_PITCH.dp)
                                    .clickable { scope.send(ApothEnchantAction.AddFilter(a.id)) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OreText(
                                    "[+] ${a.name}",
                                    modifier = Modifier.weight(1f),
                                    color = Bd.colors[BdColors.text],
                                    style = Bd.caption,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Pager(page: Int, pages: Int, onPage: (Int) -> Unit) {
        BdGlyphButton(BdIcons.ChevronLeft, null, { if (page > 0) onPage(page - 1) }, size = 9.dp)
        OreText("${page + 1}/$pages", color = Bd.colors[BdColors.faint], style = Bd.caption)
        BdGlyphButton(BdIcons.ChevronRight, null, { if (page + 1 < pages) onPage(page + 1) }, size = 9.dp)
    }


/** 献祭按钮的悬浮预览：附魔台当前属性 + 献祭手持书架后的变动。 */
@Composable
private fun ColumnScope.SacrificePreviewTooltip(state: ApothEnchantState) {
    OreText(
        tr("beyond_integration.workbench.apoth_enchant_table.sacrifice"),
        color = Bd.colors[BdColors.text],
        style = Bd.body,
    )
    val p = state.sacrificePreview
    if (p == null) {
        OreText(
            tr("beyond_integration.workbench.apoth_enchant_table.sacrifice.loading"),
            color = Bd.colors[BdColors.faint],
            style = Bd.caption,
        )
        return
    }
    if (!p.ok) {
        val key = p.blockKey
        OreText(
            if (key != null) tr(key) else tr("beyond_integration.workbench.apoth_enchant_table.sacrifice.unavailable"),
            color = Bd.colors[BdColors.danger],
            style = Bd.caption,
        )
        return
    }
    // 当前 → 献祭后（quanta 可能为负，代表削减等级波动）
    SacrificeStatLine("beyond_integration.workbench.apoth_enchant_table.eterna", p.cur[0], p.next[0])
    SacrificeStatLine("beyond_integration.workbench.apoth_enchant_table.quanta", p.cur[1], p.next[1])
    SacrificeStatLine("beyond_integration.workbench.apoth_enchant_table.arcana", p.cur[2], p.next[2])
    if (p.cur[3] == 0 && p.next[3] > 0) {
        OreText(
            tr(if (state.maxStable) "beyond_integration.workbench.apoth_enchant_table.stability.on"
               else "beyond_integration.workbench.apoth_enchant_table.stability.unavailable"),
            color = Bd.colors[BdColors.online],
            style = Bd.caption,
        )
    }
}

/** 单行「标签：当前 → 献祭后」，数值不变时只显示一个值。 */
@Composable
private fun SacrificeStatLine(labelKey: String, cur: Int, next: Int) {
    val label = Component.translatable(labelKey).string
    val text = if (cur == next) {
        Component.translatable("beyond_integration.workbench.apoth_enchant_table.sacrifice.line.keep", label, cur).string
    } else {
        Component.translatable("beyond_integration.workbench.apoth_enchant_table.sacrifice.line", label, cur, next).string
    }
    OreText(text, color = Bd.colors[BdColors.text], style = Bd.caption)
}

    /** 稳定状态行（布尔）：显示「可用/不可用」并按当前是否稳定着色。 */
    @Composable
    private fun StableInfoRow(labelKey: String, value: Boolean, available: Boolean) {
    val label = Component.translatable(labelKey).string
    val text = if (available) {
    tr(if (value) "beyond_integration.workbench.apoth_enchant_table.stability.on"
    else "beyond_integration.workbench.apoth_enchant_table.stability.off")
    } else {
    tr("beyond_integration.workbench.apoth_enchant_table.stability.unavailable")
    }
    OreText(
    Component.translatable(
    "beyond_integration.workbench.apoth_enchant_table.stats_value_plain", label, text,
    ).string,
    color = Bd.colors[BdColors.text],
    style = Bd.caption,
    )
    }

    @Composable
    private fun InfoRow(labelKey: String, value: Int, max: Int) {
        val label = Component.translatable(labelKey).string
        OreText(
            Component.translatable(
                "beyond_integration.workbench.apoth_enchant_table.stats_value_max", label, value, max,
            ).string,
            color = Bd.colors[BdColors.text],
            style = Bd.caption,
        )
    }

    @Composable
    private fun StatsRow(labelKey: String, value: Int, max: Int, onChange: (Int) -> Unit) {
        val label = Component.translatable(labelKey).string
        // 滑条只在本地拖，不实时提交；停手 350ms 后提交一次。
        var local by remember(value) { mutableStateOf(value) }
        LaunchedEffect(local) {
            if (local == value) return@LaunchedEffect
            kotlinx.coroutines.delay(350L)
            if (local != value) onChange(local)
        }
        val range = 0f..max.coerceAtLeast(1).toFloat()
        Column(Modifier.width(150.dp)) {
            BdChoiceSetting(
                Component.translatable(
                    "beyond_integration.workbench.apoth_enchant_table.stats_value_max", label, local, max,
                ).string,
            ) {
                OreSlider(local.toFloat(), { local = it.roundToInt() }, valueRange = range)
            }
        }
    }

    @Composable
    override fun Content(state: ApothEnchantState, scope: StorageWorkbenchScope<ApothEnchantAction>) {
        val colors = Bd.colors
        var infoOpen by remember { mutableStateOf(false) }
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
                    BdTooltip(tooltip = {
                        InfoRow("beyond_integration.workbench.apoth_enchant_table.eterna", state.eterna, state.maxEterna)
                        InfoRow("beyond_integration.workbench.apoth_enchant_table.quanta", state.quanta, state.maxQuanta)
                        InfoRow("beyond_integration.workbench.apoth_enchant_table.arcana", state.arcana, state.maxArcana)
            StableInfoRow("beyond_integration.workbench.apoth_enchant_table.stability", state.stable, state.maxStable)
                        OreText(
                            Component.translatable(
                                "beyond_integration.workbench.apoth_enchant_table.treasure_status",
                                tr(
                                    if (state.treasureAllowed) "beyond_integration.workbench.apoth_enchant_table.treasure_on"
                                    else "beyond_integration.workbench.apoth_enchant_table.treasure_off",
                                ),
                            ).string,
                            color = if (state.treasureAllowed) colors[BdColors.online] else colors[BdColors.faint],
                            style = Bd.caption,
                        )
                    }) {
                        BdGlyphButton(BdIcons.Info, null, { infoOpen = !infoOpen }, size = 11.dp)
                    }
                    Spacer(Modifier.width(2.dp))
                    BdGlyphButton(
                        BdIcons.Refresh,
                        tr("beyond_integration.workbench.enchant_table.refresh"),
                        { scope.send(ApothEnchantAction.Refresh) },
                        enabled = state.refreshEnabled,
                        size = 11.dp,
                    )
                    Spacer(Modifier.width(2.dp))
                    BdGlyphButton(
                        BdIcons.ToStorage,
                        tr("beyond_integration.workbench.apoth_enchant_table.separation"),
                        { scope.send(ApothEnchantAction.Separate) },
                        size = 11.dp,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            // 右列：三参数显示 + 三档候选（符文图标 + 名称 + 右侧绿色费用；悬停显示预览）
            Column(Modifier.weight(1f)) {
                OreText(
                    Component.translatable(
                        "beyond_integration.workbench.apoth_enchant_table.stats_short",
                        state.eterna,
                        state.quanta,
                        state.arcana,
                    ).string,
                    color = colors[BdColors.faint],
                    style = Bd.caption,
                )
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
                                        val ench = apothEnchantById(e.enchantId())
                                        val nm = ench?.let { it.value().description.string }.orEmpty()
                                        OreText(
                                            Component.translatable(
                                                "beyond_integration.workbench.apoth_enchant_table.preview.entry",
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
                                            "beyond_integration.workbench.apoth_enchant_table.preview.entry",
                                            row.name,
                                            enchantLevelText(row.level),
                                        ).string,
                                        color = colors[BdColors.text],
                                        style = Bd.caption,
                                    )
                                }
                                OreText(
                                    Component.translatable(
                                        "beyond_integration.workbench.apoth_enchant_table.preview.lapis",
                                        row.slot + 1,
                                    ).string,
                                    color = colors[BdColors.online],
                                    style = Bd.caption,
                                )
                                OreText(
                                    Component.translatable(
                                        "beyond_integration.workbench.apoth_enchant_table.preview.xp_points",
                                        row.xpPoints,
                                    ).string,
                                    color = colors[BdColors.faint],
                                    style = Bd.caption,
                                )
                                OreText(
                                    Component.translatable(
                                        "beyond_integration.workbench.apoth_enchant_table.preview.cost",
                                        row.cost,
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
                                    .clickable { scope.send(ApothEnchantAction.Enchant(row.slot)) },
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
        if (infoOpen) {
            BdPopover(onDismissRequest = { infoOpen = false }) {
                InfoRow("beyond_integration.workbench.apoth_enchant_table.eterna", state.eterna, state.maxEterna)
                InfoRow("beyond_integration.workbench.apoth_enchant_table.quanta", state.quanta, state.maxQuanta)
                InfoRow("beyond_integration.workbench.apoth_enchant_table.arcana", state.arcana, state.maxArcana)
                StableInfoRow("beyond_integration.workbench.apoth_enchant_table.stability", state.stable, state.maxStable)
                OreText(
                    Component.translatable(
                        "beyond_integration.workbench.apoth_enchant_table.treasure_status",
                        tr(
                            if (state.treasureAllowed) "beyond_integration.workbench.apoth_enchant_table.treasure_on"
                            else "beyond_integration.workbench.apoth_enchant_table.treasure_off",
                        ),
                    ).string,
                    color = if (state.treasureAllowed) colors[BdColors.online] else colors[BdColors.faint],
                    style = Bd.caption,
                )
                BdSettingRow(
                    Component.translatable(
                        "beyond_integration.workbench.apoth_enchant_table.filter",
                        state.filteredCount,
                    ).string,
                ) {
                    BdToggle(state.filterEnabled, onCheckedChange = { scope.send(ApothEnchantAction.ToggleFilter) })
                }
            }
        }
    }
}

/** 1.21.1 附魔注册表：经世界 registryAccess 取（BuiltInRegistries.ENCHANTMENT 已不存在）。 */
private fun apothRegistry(): net.minecraft.core.Registry<Enchantment>? {
    val level = net.minecraft.client.Minecraft.getInstance().level ?: return null
    return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
}

/** 按数字 id 取附魔（Holder）。 */
private fun apothEnchantById(id: Int): Holder<Enchantment>? {
    if (id < 0) return null
    return apothRegistry()?.asHolderIdMap()?.byId(id)
}

/** 按 ResourceLocation 取附魔（Holder）。 */
private fun apothEnchantByKey(id: ResourceLocation): Holder<Enchantment>? =
    apothRegistry()?.getHolder(id)?.orElse(null)

/** 全部附魔。 */
private fun apothAllEnchants(): List<Holder<Enchantment>> =
    apothRegistry()?.holders()?.toList().orEmpty()
