package com.solr98.beyondintegration.feature.enchant;

import dev.shadowsoffire.apotheosis.ench.api.IEnchantingBlock;
import dev.shadowsoffire.apotheosis.ench.table.EnchantingStatRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 单个 Apotheosis 书架的属性贡献（用于“拿着书架点击献祭”）。
 * <p>四项均封顶到配置硬上限；eterna 额外受书架自身 {@code maxEterna}（“最高 X”）约束，
 * 献祭后网络该属性的数值也不会超过它（例如网络已有 30、书架最高 50 → 只贡献 20）。
 */
public final class ApothShelfScanner {

    private ApothShelfScanner() {
    }

    /**
     * @return {@code int[4]}：eterna（0..cap）/ quanta（可为负）/ arcana（0..cap）/ rectification（0..cap）
     */
    public static int[] itemStats(ItemStack stack, Level level, BlockPos pos, int hardCap) {
        if (stack == null || stack.isEmpty()) return new int[]{0, 0, 0, 0};
        if (!(stack.getItem() instanceof BlockItem bi)) return new int[]{0, 0, 0, 0};
        BlockState state = bi.getBlock().defaultBlockState();
        if (!(state.getBlock() instanceof IEnchantingBlock)) return new int[]{0, 0, 0, 0};

        float eterna = EnchantingStatRegistry.getEterna(state, level, pos);
        if (!com.solr98.beyondintegration.CommandConfig.enchantApothIgnoreShelfMax()) {
            float max = EnchantingStatRegistry.getMaxEterna(state, level, pos);
            if (max > 0F) eterna = Math.min(eterna, max);
        }

        int cap = Math.max(0, Math.min(100, hardCap));
        // quanta 允许负值：部分书架是负贡献（如西瓜书架 / 宝藏书架），用于削减等级波动，
        // 不能像 eterna/arcana 那样取 0 下限，否则负向书架会被当成「无贡献」而失效。
        int quanta = (int) EnchantingStatRegistry.getQuanta(state, level, pos);
        quanta = Math.max(-cap, Math.min(cap, quanta));
        return new int[]{
                Math.min(cap, (int) Math.max(0F, eterna)),
                quanta,
                Math.min(cap, (int) Math.max(0F, EnchantingStatRegistry.getArcana(state, level, pos))),
                Math.min(cap, (int) Math.max(0F,
                        EnchantingStatRegistry.getQuantaRectification(state, level, pos))),
        };
    }

    /**
     * 该书架自身的 eterna 最高值（截图“（最高 50.00）”），供献祭时钳制“贡献后的数值”。
     * @return 最高值（0~100）；{@code 0} 表示该书架没有额外的自身最高值限制
     */
    public static int shelfMaxEterna(ItemStack stack, Level level, BlockPos pos) {
        if (stack == null || stack.isEmpty()) return 0;
        if (!(stack.getItem() instanceof BlockItem bi)) return 0;
        BlockState state = bi.getBlock().defaultBlockState();
        if (!(state.getBlock() instanceof IEnchantingBlock)) return 0;
        float max = EnchantingStatRegistry.getMaxEterna(state, level, pos);
        return max <= 0F ? 0 : Math.min(100, (int) max);
    }

}
