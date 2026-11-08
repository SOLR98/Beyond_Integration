package com.solr98.beyondintegration.client.page

import com.solr98.beyondintegration.client.render.EnchantBookRender
import com.wintercogs.beyonddimensions.client.ui.kit.BdPixelIcon
import dev.compixel.forge.drawing.NativeRefresh
import dev.compixel.forge.item.ItemIcon
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import java.util.function.Consumer

/** 候选行左侧的符文图标（10×10 单色像素图）。 */
internal val ENCHANT_RUNE_ICON = BdPixelIcon(
    "..........",
    "..####....",
    ".#....#...",
    "#..##..#..",
    "#.#..#.#..",
    "#.#..#.#..",
    "#..##..#..",
    ".#....#...",
    "..####....",
    "..........",
)

/** 书本绘制区域边长（GUI 单位）。 */
internal const val ENCHANT_BOOK_SIZE = 32

/** 书本 3D 缩放（工作台按布局调）。 */
internal const val ENCHANT_BOOK_SCALE = 20.0f

/** 书本锚点 X（工作台按实测偏移调；比区域中心左移 8）。 */
internal const val ENCHANT_BOOK_ANCHOR_X = ENCHANT_BOOK_SIZE / 2.0f - 8.0f

/** 书本锚点 Y（工作台按实测偏移调；比区域中心上移 7）。 */
internal const val ENCHANT_BOOK_ANCHOR_Y = ENCHANT_BOOK_SIZE / 2.0f - 7.0f

/**
 * 原版附魔台书本动画状态（照搬 {@code EnchantmentScreen#tickBook}）：
 * 输入物品变化触发随机翻页；有费用时缓慢开书，无费用时合书；渲染用 partialTick 插值。
 */
internal class BookAnim {
    @Volatile
    var hasCost: Boolean = false
    @Volatile
    var input: ItemStack = ItemStack.EMPTY

    private var last: ItemStack = ItemStack.EMPTY
    private val random = RandomSource.create()
    private var lastTick = Long.MIN_VALUE
    private var flip = 0f
    private var oFlip = 0f
    private var flipT = 0f
    private var flipA = 0f
    private var open = 0f
    private var oOpen = 0f

    /** 每游戏 tick 推进一次（渲染线程调用，重复 tick 幂等）。 */
    fun tickIfNeeded(gameTime: Long) {
        if (gameTime == lastTick) return
        lastTick = gameTime
        if (!ItemStack.matches(input, last)) {
            last = input.copy()
            do {
                flipT += (random.nextInt(4) - random.nextInt(4)).toFloat()
            } while (flip <= flipT + 1f && flip >= flipT - 1f)
        }
        oFlip = flip
        oOpen = open
        open = Mth.clamp(open + (if (hasCost) 0.2f else -0.2f), 0f, 1f)
        val f1 = Mth.clamp((flipT - flip) * 0.4f, -0.2f, 0.2f)
        flipA += (f1 - flipA) * 0.9f
        flip += flipA
    }

    fun openNow(partialTick: Float): Float = Mth.lerp(partialTick, oOpen, open)
    fun flipNow(partialTick: Float): Float = Mth.lerp(partialTick, oFlip, flip)
}

/** 普通/神化附魔台共用的 3D 书本图标（原生绘制，动画与原版一致）。 */
internal fun createEnchantBookIcon(anim: BookAnim): ItemIcon =
    ItemIcon.drawn(
        "enchant_table_book",
        Consumer { graphics ->
            val mc = Minecraft.getInstance()
            anim.tickIfNeeded(mc.level?.gameTime ?: 0L)
            EnchantBookRender.render(
                graphics,
                ENCHANT_BOOK_ANCHOR_X,
                ENCHANT_BOOK_ANCHOR_Y,
                ENCHANT_BOOK_SCALE,
                anim.openNow(framePartial(mc)),
                anim.flipNow(framePartial(mc)),
            )
        },
        NativeRefresh.GAME_TICK,
    )

/** 附魔等级文本：罗马数字（客户端配置 {@code enchant_level_roman}）或普通数字。 */
internal fun enchantLevelText(level: Int): String =
    if (com.solr98.beyondintegration.ClientConfig.enchantLevelRoman()) toRoman(level)
    else level.toString()

/** 1..3999 的罗马数字；越界/非正数回退普通数字。 */
private fun toRoman(n: Int): String {
    if (n <= 0 || n > 3999) return n.toString()
    val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    var num = n
    val sb = StringBuilder()
    for (i in values.indices) {
        while (num >= values[i]) {
            num -= values[i]
            sb.append(symbols[i])
        }
    }
    return sb.toString()
}

/** 1.21.1 用纳秒帧时间：换算成 1.20.1 的「毫秒/帧」量级（保持插值手感一致）。 */
private fun framePartial(mc: Minecraft): Float {
    val ns = mc.frameTimeNs
    if (ns <= 0L) return 1.0f
    // 原版 partialTick 上限 1.0；这里按 50ms/帧（20tps）折算并夹到 [0,1]
    return (ns / 50_000_000.0f).coerceIn(0.0f, 1.0f)
}