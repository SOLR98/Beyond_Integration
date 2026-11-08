package com.solr98.beyondintegration.client.gui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solr98.beyondintegration.common.menu.NetPathwayFilterMenu
import com.wintercogs.beyonddimensions.api.ui.page.BdPages
import com.wintercogs.beyonddimensions.client.ui.base.BdInventorySection
import com.wintercogs.beyonddimensions.client.ui.base.BdInventoryScreen
import com.wintercogs.beyonddimensions.client.ui.base.BdSlotSection
import com.wintercogs.beyonddimensions.client.ui.base.InjectedPages
import com.wintercogs.beyonddimensions.client.ui.base.InjectedTabs
import com.wintercogs.beyonddimensions.client.ui.base.SLOT_WINDOW_WIDTH
import com.wintercogs.beyonddimensions.client.ui.base.flagSlotIds
import com.wintercogs.beyonddimensions.client.ui.base.playerSlotIds
import com.wintercogs.beyonddimensions.client.ui.base.rememberPageSelection
import com.wintercogs.beyonddimensions.client.ui.base.tr
import com.wintercogs.beyonddimensions.client.ui.kit.BdHeader
import com.wintercogs.beyonddimensions.client.ui.kit.BdIcons
import com.wintercogs.beyonddimensions.client.ui.kit.BdMainPage
import com.wintercogs.beyonddimensions.client.ui.kit.BdRailTab
import com.wintercogs.beyonddimensions.client.ui.kit.BdScreenFrame
import com.wintercogs.beyonddimensions.client.ui.kit.BdSettingRow
import com.wintercogs.beyonddimensions.client.ui.kit.BdTabbedWindow
import com.wintercogs.beyonddimensions.client.ui.kit.BdToggle
import com.wintercogs.beyonddimensions.common.init.BDBlocks
import dev.compixel.forge.item.ItemIcon
import dev.compixel.forge.slots.ComposeMenuSlots
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

/** 网络通道过滤页的状态（来自菜单的同步字段，由服务端按方块实体回填）。 */
data class NetPathwayFilterState(
    val filterEnabled: Boolean,
    val onlyInput: Boolean,
    val fuzzy: Boolean,
)

/** 网络通道过滤页的动作：切换某个开关（0=启用过滤 1=仅接收输入 2=模糊过滤）。 */
sealed interface NetPathwayFilterAction {
    data class Toggle(val index: Int, val value: Boolean) : NetPathwayFilterAction
}

/**
 * 维度网络通道（net_pathway）过滤界面，用 BD 0.8.3 的界面体系（CompixelUI + `BdInventoryScreen`）构建，
 * 与 BD 自带的机器界面同款视觉与交互。
 * <p>内容：标记槽区（白名单样品，槽数 = 全局行数×9，由 `bd_tweaks.net_pathway_filter_rows` 决定）
 * + 三个开关（启用过滤 / 仅接收输入 / 模糊过滤）+ 玩家背包。
 * <p>开关值走菜单的 `SyncSchema`（服务端每刻从方块实体回填），点击只发 `MenuAction` 请求，
 * 不做本地乐观更新；`snapshot()` 在游戏线程读菜单字段，`Content()` 在 Compose 线程只用快照。
 * <p>标记槽是 `FlagStackTypedSlot`（虚拟槽），由 BD 的 `BdSlotAdapter` 负责图标与资源键渲染；
 * 资源同步（`BDMenuResources`）对 `isOrdered()` 的槽自动生效。
 */
class NetPathwayFilterScreen(menu: NetPathwayFilterMenu, inventory: Inventory, title: Component) :
    BdInventoryScreen<NetPathwayFilterMenu, NetPathwayFilterState, NetPathwayFilterAction>(menu, title) {

    /** 窗口标题栏图标：网络通道方块。 */
    private val icon = ItemIcon.snapshot(ItemStack(BDBlocks.NET_PATHWAY.get()))
    /** 标题与槽位分组在构造时取好，避免在 Compose 线程读游戏对象。 */
    private val titleText = title.string
    private val markerSlots = menu.flagSlotIds()
    private val playerSlots = menu.playerSlotIds()

    private val markersLabel = tr("gui.beyond_integration.net_pathway.markers")
    private val inventoryLabel = tr("ui.beyonddimensions.inventory")
    private val onLabel = tr("gui.beyond_integration.net_pathway.on")
    private val offLabel = tr("gui.beyond_integration.net_pathway.off")

    override fun snapshot() = NetPathwayFilterState(
        container.filterEnabled(),
        container.onlyInput(),
        container.fuzzy(),
    )

    override fun handle(action: NetPathwayFilterAction) {
        if (action is NetPathwayFilterAction.Toggle) {
            container.requestToggle(action.index, action.value)
        }
    }

    @Composable
    override fun Content(state: NetPathwayFilterState, slots: ComposeMenuSlots<NetPathwayFilterMenu>) {
        val page = rememberPageSelection()
        BdScreenFrame {
            BdTabbedWindow(
                slots.areaModifier(),
                width = SLOT_WINDOW_WIDTH.dp,
                header = { BdHeader(icon, titleText, ::requestClose) },
                tabs = {
                    BdRailTab(titleText, BdIcons.Main, selected = page.main) { page.open(BdPages.MAIN) }
                    InjectedTabs(pages, page)
                },
            ) {
                BdMainPage(page.main) {
                    BdSlotSection(markersLabel, markerSlots, slots)
                    Spacer(Modifier.height(6.dp))

                    BdSettingRow(
                        tr("gui.beyond_integration.net_pathway.filter", if (state.filterEnabled) onLabel else offLabel),
                        control = {
                            BdToggle(state.filterEnabled, { on -> send(NetPathwayFilterAction.Toggle(0, on)) })
                        },
                    )
                    BdSettingRow(
                        tr("gui.beyond_integration.net_pathway.only_input", if (state.onlyInput) onLabel else offLabel),
                        control = {
                            BdToggle(state.onlyInput, { on -> send(NetPathwayFilterAction.Toggle(1, on)) })
                        },
                    )
                    BdSettingRow(
                        tr("gui.beyond_integration.net_pathway.fuzzy", if (state.fuzzy) onLabel else offLabel),
                        control = {
                            BdToggle(state.fuzzy, { on -> send(NetPathwayFilterAction.Toggle(2, on)) })
                        },
                    )

                    Spacer(Modifier.height(6.dp))
                    BdInventorySection(inventoryLabel, playerSlots, slots)
                }
                InjectedPages(pages, page)
            }
        }
    }
}

