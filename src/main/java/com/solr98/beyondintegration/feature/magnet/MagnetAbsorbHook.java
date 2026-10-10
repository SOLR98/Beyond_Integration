package com.solr98.beyondintegration.feature.magnet;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 网络磁铁“吸入物品处理”扩展接口（SPI）：地面物品在入网前交由已注册的钩子处理。
 * <p>与计划稿《网络磁铁 / 漏斗“吸入物品自动处理”模块》（{@code feature/process}）对接：
 * 处理模块实现本接口并注册到 {@link MagnetAbsorbHooks}，即可在磁铁吸入时按掩码处理物品，
 * 无需再修改 {@link MagnetHandler} 或对应的 Mixin。
 */
public interface MagnetAbsorbHook {

    /**
     * 在物品入网前处理。
     *
     * @param input  即将入网的地面物品堆（{@code count} 为整堆数量；只读参考，处理模块自行决定消耗/入网）
     * @param level  所在世界（服务端）
     * @param net    该磁铁绑定的维度网络
     * @param holder 持有者实体；方块载体（漏斗）或未知时为 {@code null}
     * @return {@code true} 表示已消耗该物品，磁铁将丢弃实体且不再把原物入网
     */
    boolean onAbsorb(ItemStack input, Level level, DimensionsNet net, @Nullable Entity holder);
}
