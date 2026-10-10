package com.solr98.beyondintegration.feature.magnet;

import com.mojang.logging.LogUtils;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 磁铁吸入处理钩子注册表。处理模块通过 {@link #register(MagnetAbsorbHook)} 挂载；
 * {@link MagnetHandler} 在物品入网前调用 {@link #process}；任一钩子返回 {@code true}
 * 即视为该物品已被消耗，磁铁丢弃实体且不再把原物入网。
 */
public final class MagnetAbsorbHooks {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<MagnetAbsorbHook> HOOKS = new CopyOnWriteArrayList<>();

    private MagnetAbsorbHooks() {}

    /** 注册一个吸入处理钩子（幂等，重复注册同一实例忽略）。 */
    public static void register(MagnetAbsorbHook hook) {
        if (hook != null && !HOOKS.contains(hook)) {
            HOOKS.add(hook);
        }
    }

    /** 是否已注册任何钩子（供接入层快速短路）。 */
    public static boolean hasHooks() {
        return !HOOKS.isEmpty();
    }

    /**
     * 依次询问已注册钩子；任一返回 {@code true} 即视为物品已消耗。
     * 钩子异常按“未消耗”处理，保证不吞物、不中断吸入。
     */
    public static boolean process(ItemStack input, Level level, DimensionsNet net, @Nullable Entity holder) {
        if (HOOKS.isEmpty() || input.isEmpty()) return false;
        for (MagnetAbsorbHook hook : HOOKS) {
            try {
                if (hook.onAbsorb(input, level, net, holder)) return true;
            } catch (Throwable t) {
                LOGGER.warn("MagnetAbsorbHook {} failed on {}", hook.getClass().getName(), input, t);
            }
        }
        return false;
    }
}
