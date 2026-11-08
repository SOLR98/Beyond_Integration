package com.solr98.beyondintegration.client.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.solr98.beyondintegration.client.XpRepairClientState
import com.solr98.beyondintegration.network.PacketHandler
import com.solr98.beyondintegration.network.RequestXpRepairPacket
import com.solr98.beyondintegration.network.SetXpRepairPacket
import com.solr98.beyondintegration.network.XpRepairStatePacket
import com.wintercogs.beyonddimensions.api.ui.page.BdPage
import com.wintercogs.beyonddimensions.api.ui.page.BdPageContext
import com.wintercogs.beyonddimensions.api.ui.page.BdPageScope
import com.wintercogs.beyonddimensions.api.ui.page.BdPageSize
import com.wintercogs.beyonddimensions.client.ui.base.SLOT_WINDOW_WIDTH
import com.wintercogs.beyonddimensions.client.ui.kit.BdChoiceSetting
import com.wintercogs.beyonddimensions.client.ui.kit.BdCycler
import com.wintercogs.beyonddimensions.client.ui.kit.BdPixelIcon
import com.wintercogs.beyonddimensions.client.ui.kit.BdSectionLabel
import com.wintercogs.beyonddimensions.client.ui.kit.BdSettingRow
import com.wintercogs.beyonddimensions.client.ui.kit.BdToggle
import com.wintercogs.beyonddimensions.client.ui.kit.SIDE_RAIL_WIDTH
import com.wintercogs.beyonddimensions.client.ui.base.tr
import com.wintercogs.beyonddimensions.client.ui.kit.BdNumberEditor
import com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu
import net.minecraft.network.chat.Component

/** 页面状态：经验棒自动修复的全部设置（来自服务端下发的权威值）。 */
data class XpRepairState(
    val repairAllowed: Boolean,
    val enabled: Boolean,
    val tierIndex: Int,
    val armor: Boolean,
    val inventory: Boolean,
    val hands: Boolean,
    val curios: Boolean,
    val maid: Boolean,
    val absoluteMode: Boolean,
    val absolute: Int,
    val tiers: List<XpRepairStatePacket.Tier>,
    val targetLevel: Int,
    val maxTargetLevel: Int,
    val directGrant: Boolean,
)

/**
 * 经验棒「自动修复设置」页（注入到 BD 的 `XpExchangeMenu` 界面）。
 * <p>内容：启用开关、修复档位（服务端 `xp_repair_tiers` 列表，循环选择）、
 * 五个修复范围（护甲 / 背包 / 主手与副手 / 饰品 / 女仆）。
 * <p>档位效果说明按服务端修复模式（百分比 / 固定值）显示；服务端总开关关闭时，
 * 开关与档位都禁用，只展示说明。
 * <p>同步：设置存在经验棒物品 NBT 上，客户端本地副本不可信，故
 * `RequestXpRepairPacket` 打开界面时请求一次、`SetXpRepairPacket` 全量提交、
 * `XpRepairStatePacket` 回推权威值到 [XpRepairClientState]。
 */
object XpRepairPage : BdPage<XpExchangeMenu, XpRepairState, Unit> {

    /** 内容区宽度：与主页面同宽（窗口宽去掉左侧页签竖条）。 */
    private const val PAGE_WIDTH = SLOT_WINDOW_WIDTH - SIDE_RAIL_WIDTH

    override val icon: BdPixelIcon = BdPixelIcon(
        "..........",
        "...#...#..",
        "..#.#.#...",
        ".#...#....",
        "#..###....",
        "...#......",
        "...#......",
        "..###.....",
        "..........",
        "..........",
    )

    override fun title(context: BdPageContext<XpExchangeMenu>): Component =
        Component.translatable("beyond_integration.page.xp_repair.title")

    override fun size(context: BdPageContext<XpExchangeMenu>): BdPageSize =
        BdPageSize.Content(maxWidth = PAGE_WIDTH)

    override fun snapshot(context: BdPageContext<XpExchangeMenu>): XpRepairState {
        val tiers = XpRepairClientState.tiers()
        return XpRepairState(
            repairAllowed = XpRepairClientState.repairAllowed(),
            enabled = XpRepairClientState.enabled(),
            tierIndex = if (tiers.isEmpty()) 0 else XpRepairClientState.tierIndex().coerceIn(tiers.indices),
            armor = XpRepairClientState.armor(),
            inventory = XpRepairClientState.inventory(),
            hands = XpRepairClientState.hands(),
            curios = XpRepairClientState.curios(),
            maid = XpRepairClientState.maid(),
            absoluteMode = XpRepairClientState.absoluteMode(),
            absolute = XpRepairClientState.absolute(),
            tiers = tiers,
            // 目标等级直接读菜单的同步镜像：BD 原页面（主页面的目标等级输入框）改过之后，
            // 它随 BD 自己的 SyncSchema 每刻更新，这里也就跟着变（不再依赖本页自己的包）
            targetLevel = context.menu.target(),
            maxTargetLevel = XpRepairClientState.maxTargetLevel(),
            directGrant = XpRepairClientState.directGrant(),
        )
    }

    @Composable
    override fun ColumnScope.Content(state: XpRepairState, scope: BdPageScope<Unit>) {
        // 本地即时值：点击立即反映，服务端回推后按新快照重建
        var enabledVal by remember(state.enabled) { mutableStateOf(state.enabled) }
        var tierIndex by remember(state.tierIndex) { mutableStateOf(state.tierIndex) }
        var armor by remember(state.armor) { mutableStateOf(state.armor) }
        var inventory by remember(state.inventory) { mutableStateOf(state.inventory) }
        var hands by remember(state.hands) { mutableStateOf(state.hands) }
        var curios by remember(state.curios) { mutableStateOf(state.curios) }
        var maid by remember(state.maid) { mutableStateOf(state.maid) }
        var targetLevel by remember(state.targetLevel) { mutableStateOf(state.targetLevel) }

        val allowed = state.repairAllowed
        fun commit() = send(enabledVal, tierIndex, armor, inventory, hands, curios, maid, targetLevel)

        Column(Modifier.width(PAGE_WIDTH.dp)) {
            // 经验等级：上限来自服务端配置（BD 原上限 21863 由 BI 的 sanitizeTargetLevel mixin 放宽）
            BdSectionLabel(tr("beyond_integration.page.xp_repair.level"))
            BdWrappedText(
                tr(
                    "beyond_integration.page.xp_repair.level.desc",
                    state.maxTargetLevel,
                    tr(if (state.directGrant)
                        "beyond_integration.page.xp_repair.grant.direct"
                    else "beyond_integration.page.xp_repair.grant.batch"),
                ),
            )
            BdSettingRow(
                tr("beyond_integration.page.xp_repair.level.target"),
                control = {
                    BdNumberEditor(
                        targetLevel,
                        {
                            targetLevel = it
                            commit()
                        },
                        0..state.maxTargetLevel,
                        scope.shown,
                    )
                },
            )

            Spacer(Modifier.height(6.dp))
            BdSectionLabel(tr("beyond_integration.page.xp_repair.enable"))
            BdWrappedText(tr("beyond_integration.page.xp_repair.enable.desc"))
            BdSettingRow(
                tr("beyond_integration.page.xp_repair.enable.toggle"),
                control = {
                    BdToggle(enabledVal, enabled = allowed, onCheckedChange = {
                        enabledVal = it
                        commit()
                    })
                },
            )

            Spacer(Modifier.height(6.dp))
            BdSectionLabel(tr("beyond_integration.page.xp_repair.tier"))
            BdWrappedText(tr("beyond_integration.page.xp_repair.tier.desc"))
            // 档位选择独占一行：BdCycler 自身 fillMaxWidth，塞进 BdSettingRow 会把左侧标题挤没
            BdChoiceSetting(
                tr("beyond_integration.page.xp_repair.tier.current"),
                description = tierEffect(state, tierIndex),
            ) {
                BdCycler(
                    state.tiers.map { "${it.name} · ${it.percent}%" },
                    tierIndex,
                    enabled = allowed && state.tiers.isNotEmpty(),
                    onSelect = {
                        tierIndex = it
                        commit()
                    },
                )
            }

            Spacer(Modifier.height(6.dp))
            BdSectionLabel(tr("beyond_integration.page.xp_repair.ranges"))
            BdWrappedText(tr("beyond_integration.page.xp_repair.ranges.desc"))
            RangeRow(tr("beyond_integration.page.xp_repair.range.armor"), armor, allowed) {
                armor = it
                commit()
            }
            RangeRow(tr("beyond_integration.page.xp_repair.range.inventory"), inventory, allowed) {
                inventory = it
                commit()
            }
            RangeRow(tr("beyond_integration.page.xp_repair.range.hands"), hands, allowed) {
                hands = it
                commit()
            }
            RangeRow(tr("beyond_integration.page.xp_repair.range.curios"), curios, allowed) {
                curios = it
                commit()
            }
            // 女仆部分走 TLM 的 IMaidBauble（经验棒作为女仆饰品时每 tick 调用），与玩家装备扫描是两套
            RangeRow(
                tr("beyond_integration.page.xp_repair.range.maid"),
                maid,
                allowed,
                tr("beyond_integration.page.xp_repair.range.maid.desc"),
            ) {
                maid = it
                commit()
            }

            if (!allowed) {
                Spacer(Modifier.height(6.dp))
                BdWrappedText(tr("beyond_integration.page.xp_repair.disabled"))
            }
        }
    }

    /** 档位效果说明：按服务端修复模式显示百分比或固定值。 */
    private fun tierEffect(state: XpRepairState, index: Int): String {
        if (state.tiers.isEmpty()) return ""
        val tier = state.tiers.getOrNull(index.coerceIn(state.tiers.indices)) ?: return ""
        return if (state.absoluteMode) {
            tr("beyond_integration.page.xp_repair.effect.absolute", state.absolute, tier.interval)
        } else {
            tr("beyond_integration.page.xp_repair.effect.percent", tier.percent, tier.interval)
        }
    }

    @Composable
    private fun RangeRow(label: String, value: Boolean, enabled: Boolean,
                         description: String? = null, onChange: (Boolean) -> Unit) {
        BdSettingRow(label, description, control = {
            BdToggle(value, enabled = enabled, onCheckedChange = onChange)
        })
    }

    /** 全量提交当前设置。 */
    private fun send(enabled: Boolean, tierIndex: Int, armor: Boolean, inventory: Boolean,
                     hands: Boolean, curios: Boolean, maid: Boolean, targetLevel: Int) {
        PacketHandler.sendToServer(
            SetXpRepairPacket(enabled, tierIndex, armor, inventory, hands, curios, maid, targetLevel))
    }

    /** 打开界面时向服务端要一次权威值。 */
    fun requestCurrent() {
        PacketHandler.sendToServer(RequestXpRepairPacket())
    }
}
