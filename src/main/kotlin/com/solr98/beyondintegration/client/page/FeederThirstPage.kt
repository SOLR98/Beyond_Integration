package com.solr98.beyondintegration.client.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solr98.beyondintegration.client.FeederThirstClientState
import com.solr98.beyondintegration.feature.feeder.FeederThirstSettings
import com.solr98.beyondintegration.network.PacketHandler
import com.solr98.beyondintegration.network.RequestFeederThirstPacket
import com.solr98.beyondintegration.network.SetFeederThirstPacket
import com.wintercogs.beyonddimensions.api.ui.page.BdPage
import com.wintercogs.beyonddimensions.api.ui.page.BdPageContext
import com.wintercogs.beyonddimensions.api.ui.page.BdPageScope
import com.wintercogs.beyonddimensions.api.ui.page.BdPageSize
import com.wintercogs.beyonddimensions.client.ui.base.SLOT_WINDOW_WIDTH
import com.wintercogs.beyonddimensions.client.ui.kit.BdPixelIcon
import com.wintercogs.beyonddimensions.client.ui.kit.BdSectionLabel
import com.wintercogs.beyonddimensions.client.ui.kit.BdToggle
import com.wintercogs.beyonddimensions.client.ui.kit.SIDE_RAIL_WIDTH
import com.wintercogs.beyonddimensions.common.menu.NetFeederMenu
import dev.compixel.ui.ore.input.OreSlider
import kotlinx.coroutines.delay
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/** 页面状态：喂食器的四项触发阈值（来自服务端推送的权威值）。 */
data class FeederThirstState(
    val thirst: Int,
    val quenched: Int,
    val food: Int,
    val saturation: Int,
)

/**
 * 网络喂食器「口渴 / 饱食」页（注入到 BD 的 `NetFeederMenu` 界面）。
 * <p>两种模式（由后两项阈值是否 > 0 推导，切换时一并改写四项，保证一致）：
 * <ul>
 *   <li><b>阈值模式</b>（水饱和 = 饱和度 = 0）：口渴 / 饱食度阈值可调；</li>
 *   <li><b>饱和维持模式</b>（水饱和 / 饱和度 > 0）：后两项可调，<b>口渴 / 饱食度被禁止调整并固定为上限 20</b>
 *       —— 因为补给是「触发即补满」，此时前两项本来就会一直贴上限（机制硬性限制）。</li>
 * </ul>
 * <p>文本：说明交给 [BdWrappedText]，按面板可用宽度自动折行。
 * <p>数据：设置存在手持物品 NBT 上，客户端本地副本不会自动更新，故由服务端推
 * `FeederThirstStatePacket` 到 `FeederThirstClientState`；滑动只在本地即时显示，
 * 停手 150ms 后才发设置包（防抖）。
 */
object FeederThirstPage : BdPage<NetFeederMenu, FeederThirstState, Unit> {

    /** 上限值（阈值范围 0~20 的满值）。 */
    private const val FULL = 20

    /** 内容区宽度：与主页面同宽（窗口宽去掉左侧页签竖条）。 */
    private const val PAGE_WIDTH = SLOT_WINDOW_WIDTH - SIDE_RAIL_WIDTH

    override val icon: BdPixelIcon = BdPixelIcon(
        "#.....#...",
        "##...##...",
        ".#...#....",
        "..#.#.....",
        "...#......",
        "..#.#.....",
        ".#...#....",
        "##...##...",
        "#.....#...",
        "..........",
    )

    override fun title(context: BdPageContext<NetFeederMenu>): Component =
        Component.translatable("beyond_integration.page.feeder_thirst.title")

    /**
     * 宽度与主页面一致，高度由内容决定（[BdPageSize.Content]）：
     * 说明文字自动折行后的行数不确定，用固定高度会留白或出滚动条。
     */
    override fun size(context: BdPageContext<NetFeederMenu>): BdPageSize =
        BdPageSize.Content(maxWidth = PAGE_WIDTH)

    override fun snapshot(context: BdPageContext<NetFeederMenu>): FeederThirstState =
        FeederThirstState(
            FeederThirstClientState.thirst(),
            FeederThirstClientState.quenched(),
            FeederThirstClientState.food(),
            FeederThirstClientState.saturation(),
        )

    @Composable
    override fun ColumnScope.Content(state: FeederThirstState, scope: BdPageScope<Unit>) {
        // 后两项 > 0 即为「饱和维持模式」：前两项固定上限、禁止调整
        val holdMode = state.quenched > 0 || state.saturation > 0

        var thirst by remember(state.thirst) { mutableStateOf(state.thirst) }
        var quenched by remember(state.quenched) { mutableStateOf(state.quenched) }
        var food by remember(state.food) { mutableStateOf(state.food) }
        var saturation by remember(state.saturation) { mutableStateOf(state.saturation) }

        // 防抖：任一值停手 150ms 后统一提交；服务端回推造成的同步不会重复提交
        LaunchedEffect(thirst, quenched, food, saturation) {
            if (thirst != state.thirst || quenched != state.quenched
                || food != state.food || saturation != state.saturation
            ) {
                delay(150)
                send(thirst, quenched, food, saturation)
            }
        }

        Column(Modifier.width(PAGE_WIDTH.dp)) {
            BdSectionLabel(Component.translatable(
                "beyond_integration.page.feeder_thirst.mode").string)
            BdWrappedText(Component.translatable(
                "beyond_integration.page.feeder_thirst.mode.desc").string)
            BdToggle(
                checked = holdMode,
                onCheckedChange = { on ->
                    if (on) {
                        // 进入饱和维持模式：四项都设上限，前两项随即被固定
                        thirst = FULL
                        quenched = FULL
                        food = FULL
                        saturation = FULL
                    } else {
                        // 回到阈值模式：后两项清零，前两项回到默认阈值
                        thirst = FeederThirstSettings.DEFAULT_THIRST
                        quenched = 0
                        food = FeederThirstSettings.DEFAULT_FOOD
                        saturation = 0
                    }
                    send(thirst, quenched, food, saturation)
                },
                enabled = scope.shown,
            )

            // 口渴：饱和维持模式下固定上限且不可调
            LabeledSlider(
                title = Component.translatable(
                    "beyond_integration.page.feeder_thirst.thirst",
                    if (holdMode) FULL else thirst).string,
                description = Component.translatable(
                    "beyond_integration.page.feeder_thirst.thirst.desc").string,
                value = if (holdMode) FULL else thirst,
                enabled = scope.shown && !holdMode,
                onChange = { thirst = it },
            )

            LabeledSlider(
                title = Component.translatable(
                    "beyond_integration.page.feeder_thirst.quenched", quenched).string,
                description = Component.translatable(
                    "beyond_integration.page.feeder_thirst.quenched.desc").string,
                value = quenched,
                enabled = scope.shown,
                onChange = { quenched = it },
            )

            // 饱食度：饱和维持模式下固定上限且不可调
            LabeledSlider(
                title = Component.translatable(
                    "beyond_integration.page.feeder_thirst.food",
                    if (holdMode) FULL else food).string,
                description = Component.translatable(
                    "beyond_integration.page.feeder_thirst.food.desc").string,
                value = if (holdMode) FULL else food,
                enabled = scope.shown && !holdMode,
                onChange = { food = it },
            )

            LabeledSlider(
                title = Component.translatable(
                    "beyond_integration.page.feeder_thirst.saturation", saturation).string,
                description = Component.translatable(
                    "beyond_integration.page.feeder_thirst.saturation.desc").string,
                value = saturation,
                enabled = scope.shown,
                onChange = { saturation = it },
            )
        }
    }

    /** 发送设置请求。 */
    private fun send(thirst: Int, quenched: Int, food: Int, saturation: Int) {
        PacketHandler.sendToServer(SetFeederThirstPacket(thirst, quenched, food, saturation))
    }

    /** 打开界面时向服务端要一次权威值。 */
    fun requestCurrent() {
        PacketHandler.sendToServer(RequestFeederThirstPacket())
    }

    /** 标题 + 自动折行说明 + 滑动条。 */
    @Composable
    private fun LabeledSlider(
        title: String,
        description: String,
        value: Int,
        enabled: Boolean,
        onChange: (Int) -> Unit,
    ) {
        BdSectionLabel(title)
        BdWrappedText(description)
        Spacer(Modifier.height(1.dp))
        OreSlider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            // 左右留白：滑块把手画在轨道两端时会探出面板边缘
            modifier = Modifier.padding(horizontal = 7.dp),
            valueRange = FeederThirstSettings.MIN.toFloat()..FULL.toFloat(),
            enabled = enabled,
        )
        Spacer(Modifier.height(3.dp))
    }
}


