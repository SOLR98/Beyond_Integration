package com.solr98.beyondintegration.maid;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * 女仆网络查找助手。
 * 帮助女仆实体找到其可用的维度网络：优先读缓存，其次依次扫描
 * Curios 饰品栏与女仆饰物背包中的网络终端物品。
 */
public class MaidNetworkHelper {

    /**
     * 查找女仆可用的维度网络入口。
     * 未加载 touhou_little_maid 或找不到终端时返回 null。
     */
    public static DimensionsNet findTerminal(LivingEntity entity) {
        if (!ModList.get().isLoaded("touhou_little_maid")) return null;

        DimensionsNet cached = MaidNetworkCache.get(entity);
        if (cached != null) return cached;

        try {
            if (!(entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) return null;

            DimensionsNet net = scanCurios(maid);
            if (net != null) return cache(entity, net);

            net = scanInv(maid.getMaidBauble());
            if (net != null) return cache(entity, net);

            MaidNetworkCache.remove(maid.getUUID());
        } catch (NoClassDefFoundError ignored) {}

        return null;
    }

    /**
     * 扫描女仆的 Curios 饰品栏，查找带有网络 ID 的终端物品。
     * @return 找到的维度网络，未找到返回 null
     */
    private static DimensionsNet scanCurios(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        try {
            DimensionsNet[] result = {null};
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(maid).ifPresent(handler -> {
                for (var stacksHandler : handler.getCurios().values()) {
                    IDynamicStackHandler stacks = stacksHandler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (stack.isEmpty()) continue;

                        int netId = NetedItem.getNetId(stack);
                        if (netId < 0) continue;

                        DimensionsNet n = DimensionsNet.getNetFromId(netId);
                        if (n != null) { result[0] = n; return; }
                    }
                }
            });
            return result[0];
        } catch (NoClassDefFoundError ignored) {
            return null;
        }
    }

    /**
     * 扫描通用物品容器（如女仆饰物背包），查找网络终端物品。
     * @return 找到的维度网络，未找到返回 null
     */
    private static DimensionsNet scanInv(net.minecraftforge.items.IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            int netId = NetedItem.getNetId(stack);
            if (netId < 0) continue;

            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net != null) return net;
        }
        return null;
    }

    /** 将找到的网络写入缓存并返回。 */
    private static DimensionsNet cache(LivingEntity entity, DimensionsNet net) {
        MaidNetworkCache.put(entity.getUUID(), net.getId());
        return net;
    }
}
