package com.solr98.beyondintegration.client.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solr98.beyondintegration.client.ClientMenuNet
import com.solr98.beyondintegration.client.EnergyChargeState
import com.solr98.beyondintegration.client.EnchantSeparationState
import com.solr98.beyondintegration.client.PotionCharmState
import com.solr98.beyondintegration.feature.charm.PotionCharmMode
import com.solr98.beyondintegration.network.CyclePotionCharmModePacket
import com.solr98.beyondintegration.network.PacketHandler
import com.solr98.beyondintegration.network.RequestEnergyChargePacket
import com.solr98.beyondintegration.network.RequestEnchantSeparationPacket
import com.solr98.beyondintegration.network.RequestPotionCharmPacket
import com.solr98.beyondintegration.network.SacrificeMendingPacket
import com.solr98.beyondintegration.network.ToggleEnergyChargePacket
import com.solr98.beyondintegration.network.ToggleEnchantSeparationPacket
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet
import com.wintercogs.beyonddimensions.api.ui.page.BdPage
import com.wintercogs.beyonddimensions.api.ui.page.BdPageContext
import com.wintercogs.beyonddimensions.api.ui.page.BdPageScope
import com.wintercogs.beyonddimensions.api.ui.page.BdPageSize
import com.wintercogs.beyonddimensions.client.ui.base.SLOT_WINDOW_WIDTH
import com.wintercogs.beyonddimensions.client.ui.base.tr
import com.wintercogs.beyonddimensions.client.ui.kit.BdChip
import com.wintercogs.beyonddimensions.client.ui.kit.BdPixelIcon
import com.wintercogs.beyonddimensions.client.ui.kit.BdSectionLabel
import com.wintercogs.beyonddimensions.client.ui.kit.BdSettingRow
import com.wintercogs.beyonddimensions.client.ui.kit.BdToggle
import com.wintercogs.beyonddimensions.client.ui.kit.SIDE_RAIL_WIDTH
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu
import dev.compixel.ui.ore.display.OreText
import net.minecraft.network.chat.Component

/** 网络设置页状态：三个网络级开关 + 药水护符的献祭解锁状态。 */
data class NetSettingsState(
    val synced: Boolean,
    val enchantSeparation: Boolean,
    val energyCharge: Boolean,
    val potionCharmMode: Int,
    val potionCharmMending: Boolean,
)

/**
 * 「网络设置」页：集中 BI 加在主网络上的设置项（注入 BD 存储 / 终端界面 `DimensionsNetMenu`；
 * 终端菜单 `DimensionsNetTerminalMenu` 继承它，自动生效）。
 * <ul>
 *   <li><b>附魔分离</b>：网络级开关（经理/所有者可切换），命令 <code>/bdtools enchant separate</code> 的图形入口；</li>
 *   <li><b>自动充电</b>：网络级开关（盔甲/手持/Curios/女仆饰品从网络 FE 充电）；</li>
 *   <li><b>网络药水护符</b>：生效目标四态（仅玩家 / 仅女仆 / 两者 / 关闭）+ 献祭经验修补书解锁。</li>
 * </ul>
 * <p>三个开关由服务端按“当前打开的网络菜单”处理（`NetMenuResolver`：有菜单取菜单网络，否则回退主网络），
 * 页面自身不做权限判断：无经理/所有者权限时由服务端回一条提示。状态来自客户端缓存
 * （<code>EnchantSeparationState</code> / <code>EnergyChargeState</code> / <code>PotionCharmState</code>），
 * 打开页面时用三个 Request 包向服务端要一次权威值。
 * <p>未同步主网络（服务端未开 <code>bd_tweaks.primary_net_sync</code>）时页面只显示提示。
 */
object NetSettingsPage : BdPage<DimensionsNetMenu, NetSettingsState, Unit> {

    private const val PAGE_WIDTH = SLOT_WINDOW_WIDTH - SIDE_RAIL_WIDTH

    override val icon: BdPixelIcon = BdPixelIcon(
        "..........",
        "..#####...",
        "..#...#...",
        "..#.#.#...",
        "..#...#...",
        "..#####...",
        "....#.....",
        "..##.##...",
        ".#.....#..",
        "..........",
    )

    override fun title(context: BdPageContext<DimensionsNetMenu>): Component =
        Component.translatable("beyond_integration.page.net_settings.title")

    override fun size(context: BdPageContext<DimensionsNetMenu>): BdPageSize =
        BdPageSize.Content(maxWidth = PAGE_WIDTH)

    override fun snapshot(context: BdPageContext<DimensionsNetMenu>): NetSettingsState {
        // 优先用当前打开的存储/终端界面所连的网络；菜单未同步时回退主网络镜像
        var netId = context.menu.networkId()
        if (netId == DimensionsNet.NO_PRIMARY_NET_ID) netId = ClientMenuNet.netId()
        if (netId < 0) {
            return NetSettingsState(false, false, true, 0, false)
        }
        return NetSettingsState(
            synced = true,
            enchantSeparation = EnchantSeparationState.INSTANCE.get(netId),
            energyCharge = EnergyChargeState.INSTANCE.get(netId),
            potionCharmMode = PotionCharmState.getMode(netId),
            potionCharmMending = PotionCharmState.isMendingUnlocked(netId),
        )
    }

    @Composable
    override fun ColumnScope.Content(state: NetSettingsState, scope: BdPageScope<Unit>) {
        // 每次切到本页都向服务端要一次权威值（key 用 shown：首次组合时页面可能未显示）
        LaunchedEffect(scope.shown) {
            if (scope.shown) requestCurrent()
        }

        Column(Modifier.width(PAGE_WIDTH.dp)) {
            BdSectionLabel(tr("beyond_integration.page.net_settings.title"))
            BdWrappedText(tr("beyond_integration.page.net_settings.desc"))

            if (!state.synced) {
                Spacer(Modifier.height(4.dp))
                BdWrappedText(tr("beyond_integration.page.net_settings.not_synced"))
                return@Column
            }

            Spacer(Modifier.height(6.dp))
            BdSettingRow(
                tr("beyond_integration.page.net_settings.enchant_separation"),
                tr("beyond_integration.page.net_settings.enchant_separation.desc"),
                control = {
                    BdToggle(state.enchantSeparation, onCheckedChange = {
                        PacketHandler.sendToServer(ToggleEnchantSeparationPacket())
                    })
                },
            )
            BdSettingRow(
                tr("beyond_integration.page.net_settings.energy_charge"),
                tr("beyond_integration.page.net_settings.energy_charge.desc"),
                control = {
                    BdToggle(state.energyCharge, onCheckedChange = {
                        PacketHandler.sendToServer(ToggleEnergyChargePacket())
                    })
                },
            )
            BdSettingRow(
                tr(
                    "beyond_integration.page.net_settings.potion_charm",
                    tr(PotionCharmMode.of(state.potionCharmMode).langKey()),
                ),
                tr("beyond_integration.page.net_settings.potion_charm.desc"),
                control = {
                    BdChip(
                        onClick = { PacketHandler.sendToServer(CyclePotionCharmModePacket()) },
                    ) {
                        OreText(tr("beyond_integration.page.net_settings.switch"))
                    }
                },
            )
            BdSettingRow(
                if (state.potionCharmMending) {
                    tr("beyond_integration.page.net_settings.sacrifice.unlocked")
                } else {
                    tr("beyond_integration.page.net_settings.sacrifice")
                },
                tr("beyond_integration.page.net_settings.sacrifice.desc"),
                control = {
                    BdChip(
                        onClick = { PacketHandler.sendToServer(SacrificeMendingPacket()) },
                        enabled = !state.potionCharmMending,
                    ) {
                        OreText(
                            tr(
                                if (state.potionCharmMending)
                                    "beyond_integration.page.net_settings.sacrifice.done"
                                else "beyond_integration.page.net_settings.sacrifice.action",
                            ),
                        )
                    }
                },
            )
        }
    }

    /** 打开页面时向服务端请求三个网络的权威状态。 */
    fun requestCurrent() {
        PacketHandler.sendToServer(RequestEnchantSeparationPacket())
        PacketHandler.sendToServer(RequestEnergyChargePacket())
        PacketHandler.sendToServer(RequestPotionCharmPacket())
    }
}
