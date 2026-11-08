package com.solr98.beyondintegration.feature.enchant;

import dev.shadowsoffire.apothic_enchanting.api.EnchantmentStatBlock;
import dev.shadowsoffire.apothic_enchanting.table.EnchantingStatRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 单个 Apotheosis 书架的属性贡献（用于「拿着书架点击献祭」）。
 * <p>1.21.1 的 apothic_enchanting 移除了 rectification（修正值），其效果由两部分承担：
 * <ul>
 *   <li>{@code quanta} 的负向削减（如 geode_shelf 的 -15，降低等级波动幅度）；</li>
 *   <li>{@code EnchantmentStatBlock.providesStability()} 布尔稳定标记（不再滚出负向波动）。</li>
 * </ul>
 * 本类第 4 项因此改为「稳定标记」0/1，与 quanta 负值一并保留上层的真实语义。
 */
public final class ApothShelfScanner {

    private ApothShelfScanner() {
    }

    /**
     * @return {@code int[4]}：eterna（≥0）/ quanta（可为负）/ arcana（≥0）/ stable(0|1)
     */
    public static int[] itemStats(ItemStack stack, Level level, BlockPos pos, int hardCap) {
        if (stack == null || stack.isEmpty()) return new int[]{0, 0, 0, 0};
        if (!(stack.getItem() instanceof BlockItem bi)) return new int[]{0, 0, 0, 0};
        BlockState state = bi.getBlock().defaultBlockState();
        if (!(state.getBlock() instanceof EnchantmentStatBlock)) return new int[]{0, 0, 0, 0};

        float eterna = EnchantingStatRegistry.getEterna(state, level, pos);
        if (!com.solr98.beyondintegration.CommandConfig.enchantApothIgnoreShelfMax()) {
            float max = EnchantingStatRegistry.getMaxEterna(state, level, pos);
            if (max > 0F) eterna = Math.min(eterna, max);
        }

        int cap = Math.max(0, Math.min(100, hardCap));
        // quanta 允许负值：部分书架是负贡献（如 geode_shelf 的 -15，用于削减波动），
        // 不能像 eterna/arcana 那样取 0 下限，否则负向书架会被当成「无贡献」而失效。
        int quanta = (int) EnchantingStatRegistry.getQuanta(state, level, pos);
        quanta = Math.max(-cap, Math.min(cap, quanta));
        // 第 4 位：1.21.1 用 providesStability()（布尔）取代旧的 rectification 数值，
        // 这里归一化成 0/1 标记，供上层按 true/false 使用。
        int stable = ((EnchantmentStatBlock) state.getBlock()).providesStability(state, level, pos) ? 1 : 0;

        return new int[]{
                Math.min(cap, (int) Math.max(0F, eterna)),
                quanta,
                Math.min(cap, (int) Math.max(0F, EnchantingStatRegistry.getArcana(state, level, pos))),
                stable,
        };
    }

    /**
     * 该书架自身的 eterna 最高值（截图「（最高 50.00）」），供献祭时钳制「贡献后的数值」。
     *
     * @return 最高值（0~100）；{@code 0} 表示该书架没有额外的自身最高值限制
     */
    public static int shelfMaxEterna(ItemStack stack, Level level, BlockPos pos) {
        if (stack == null || stack.isEmpty()) return 0;
        if (!(stack.getItem() instanceof BlockItem bi)) return 0;
        BlockState state = bi.getBlock().defaultBlockState();
        if (!(state.getBlock() instanceof EnchantmentStatBlock)) return 0;
        float max = EnchantingStatRegistry.getMaxEterna(state, level, pos);
        return max <= 0F ? 0 : Math.min(100, (int) max);
    }
}
