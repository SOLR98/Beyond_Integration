package com.solr98.beyondintegration.maid;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.UUID;

public class MaidNetworkHelper {

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

                        if (handleInvalidToken(stack, netId)) {
                            stacks.setStackInSlot(i, stack);
                            continue;
                        }

                        ensureToken(stack, netId, maid.getUUID());
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

    private static DimensionsNet scanInv(net.minecraftforge.items.IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            int netId = NetedItem.getNetId(stack);
            if (netId < 0) continue;

            if (handleInvalidToken(stack, netId)) continue;

            ensureToken(stack, netId, null);
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net != null) return net;
        }
        return null;
    }

    /** @return true if token was invalid and binding was cleared */
    private static boolean handleInvalidToken(ItemStack stack, int netId) {
        if (!CommandConfig.enableTokenSystem()) return false;
        UUID token = MaidTokenUtil.readToken(stack);
        if (token == null) return false;
        if (!BindingTokenManager.isTokenValid(netId, token)) {
            MaidTokenUtil.clearBinding(stack);
            return true;
        }
        return false;
    }

    private static void ensureToken(ItemStack stack, int netId, UUID ownerUuid) {
        if (!CommandConfig.enableTokenSystem()) return;
        if (BindingTokenManager.getInstance() == null) return;
        if (MaidTokenUtil.readToken(stack) != null) return;

        UUID owner = ownerUuid != null ? ownerUuid : UUID.randomUUID();
        UUID token = BindingTokenManager.getOrCreateToken(netId, owner);
        MaidTokenUtil.writeToken(stack, token);
        if (ownerUuid != null) {
            stack.getOrCreateTag().putString("beyond$bindingOwner", ownerUuid.toString());
        }
    }

    private static DimensionsNet cache(LivingEntity entity, DimensionsNet net) {
        MaidNetworkCache.put(entity.getUUID(), net.getId());
        return net;
    }
}
