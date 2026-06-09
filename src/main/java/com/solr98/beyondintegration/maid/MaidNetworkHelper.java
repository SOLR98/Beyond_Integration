package com.solr98.beyondintegration.maid;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * 仅从女仆饰品栏（Curios bauble）查找网络终端。
 */
public class MaidNetworkHelper {

    public static DimensionsNet findTerminal(LivingEntity entity) {
        if (!ModList.get().isLoaded("touhou_little_maid")) return null;

        DimensionsNet cached = MaidNetworkCache.get(entity);
        if (cached != null) return cached;

        try {
            if (!(entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) return null;

            // 仅从 Curios 饰品栏查找
            DimensionsNet net = scanCurios(maid);
            if (net != null) return cache(entity, net);

            // 也检查女仆的饰品格子（getMaidBauble）
            net = scanInv(maid.getMaidBauble());
            if (net != null) return cache(entity, net);

            MaidNetworkCache.remove(maid.getUUID());
        } catch (NoClassDefFoundError ignored) {}

        return null;
    }

    private static DimensionsNet scanCurios(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        try {
            DimensionsNet[] result = {null};
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(maid).ifPresent(handler -> {
                for (var stacksHandler : handler.getCurios().values()) {
                    var stacks = stacksHandler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            int netId = NetedItem.getNetId(stack);
                            if (netId >= 0) {
                                DimensionsNet n = DimensionsNet.getNetFromId(netId);
                                if (n != null) { result[0] = n; return; }
                            }
                        }
                    }
                }
            });
            return result[0];
        } catch (NoClassDefFoundError ignored) {
            return null;
        }
    }

    private static DimensionsNet scanInv(net.minecraftforge.items.IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                int netId = NetedItem.getNetId(stack);
                if (netId >= 0) {
                    DimensionsNet net = DimensionsNet.getNetFromId(netId);
                    if (net != null) return net;
                }
            }
        }
        return null;
    }

    private static DimensionsNet cache(LivingEntity entity, DimensionsNet net) {
        MaidNetworkCache.put(entity.getUUID(), net.getId());
        return net;
    }
}
