package com.solr98.beyondintegration.handler;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.init.ModItems;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler.TypeBucket;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NetworkAmmoExtractor {

    public static int consumeAmmoDirectly(ItemStack gunStack, int neededAmount, ServerPlayer player) {
        List<DimensionsNet> nets = DimensionsNet.getAllNetFromPlayer(player);
        for (DimensionsNet net : nets) {
            int taken = consumeAmmoDirectly(gunStack, neededAmount, net);
            if (taken > 0) return taken;
        }
        return 0;
    }

    public static int consumeAmmoDirectly(ItemStack gunStack, int neededAmount, DimensionsNet net) {
        if (hasCreativeAmmoBoxInNetwork(gunStack, net)) return neededAmount;
        ItemStack reference = getAmmoReference(gunStack);
        if (reference != null) {
            ItemStackKey key = new ItemStackKey(reference);
            KeyAmount extracted = net.getUnifiedStorage().extract(key, neededAmount, false, false);
            if (extracted.amount() > 0) {
                net.setDirty();
                return (int) extracted.amount();
            }
        }
        return 0;
    }

    public static int extractAmmoFromNetwork(ItemStack gunStack, int neededAmount, ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;

        ItemStack reference = getAmmoReference(gunStack);
        if (reference == null) return 0;

        ItemStackKey key = new ItemStackKey(reference);
        KeyAmount extracted = net.getUnifiedStorage().extract(key, neededAmount, false, false);
        if (extracted.amount() > 0) {
            net.setDirty();
            ItemStack ammo = key.copyStackWithCount(extracted.amount());
            if (!player.getInventory().add(ammo)) {
                player.drop(ammo, false);
            }
        }
        return (int) extracted.amount();
    }

    public static DimensionsNet findNetworkForPlayer(ServerPlayer player) {
        return DimensionsNet.getPrimaryNetFromPlayer(player);
    }

    // ── 从玩家首个有可用弹药的网络统计 Superb 弹药 ──
    public static long countSuperbAmmoAcrossNets(ServerPlayer player, String ammoType) {
        List<DimensionsNet> nets = DimensionsNet.getAllNetFromPlayer(player);
        for (DimensionsNet net : nets) {
            if (!(net instanceof SuperbAmmoAccessor acc)) continue;
            var map = acc.getSuperbAmmo();
            if (map.getOrDefault("__infinite__", 0L) > 0) return Long.MAX_VALUE;
            long count = map.getOrDefault(ammoType, 0L);
            if (count > 0) return count;
        }
        return 0;
    }

    // ── 从玩家首个有可用弹药的网络消耗 Superb 弹药 ──
    public static long consumeSuperbAmmoAcrossNets(ServerPlayer player, String ammoType, long amount) {
        List<DimensionsNet> nets = DimensionsNet.getAllNetFromPlayer(player);
        for (DimensionsNet net : nets) {
            if (!(net instanceof SuperbAmmoAccessor acc)) continue;
            var map = acc.getSuperbAmmo();
            if (map.getOrDefault("__infinite__", 0L) > 0) return amount;
            long current = map.getOrDefault(ammoType, 0L);
            if (current <= 0) continue;
            long take = Math.min(current, amount);
            long left = current - take;
            if (left <= 0) map.remove(ammoType);
            else map.put(ammoType, left);
            net.setDirty();
            return take;
        }
        return 0;
    }

    // ── 从玩家首个有可用物品的网络消耗 ITEM 弹药 ──
    public static long consumeItemAcrossNets(ServerPlayer player, ItemStackKey itemKey, long amount) {
        List<DimensionsNet> nets = DimensionsNet.getAllNetFromPlayer(player);
        for (DimensionsNet net : nets) {
            KeyAmount extracted = net.getUnifiedStorage().extract(itemKey, amount, false, true);
            if (extracted.amount() > 0) {
                net.setDirty();
                return extracted.amount();
            }
        }
        return 0;
    }

    public static SuperbAmmoAccessor resolveSuperbAmmo(ServerPlayer player) {
        var net = findNetworkForPlayer(player);
        if (net instanceof SuperbAmmoAccessor acc) return acc;
        return null;
    }

    public static DimensionsNet findTerminalInInventory(ServerPlayer player) {
        var capOpt = player.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if (capOpt.isEmpty()) return null;
        IItemHandler inv = capOpt.get();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                int netId = NetedItem.getNetId(stack);
                if (netId >= 0) {
                    DimensionsNet found = DimensionsNet.getNetFromId(netId);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    public static List<DimensionsNet> findAllTerminalsInInventory(ServerPlayer player) {
        List<DimensionsNet> nets = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        var capOpt = player.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if (capOpt.isEmpty()) return nets;
        IItemHandler inv = capOpt.get();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                int netId = NetedItem.getNetId(stack);
                if (netId >= 0 && !seen.contains(netId)) {
                    seen.add(netId);
                    DimensionsNet net = DimensionsNet.getNetFromId(netId);
                    if (net != null) nets.add(net);
                }
            }
        }
        return nets;
    }

    public static int countAmmoInNetwork(ItemStack gunStack, ServerPlayer player) {
        List<DimensionsNet> nets = DimensionsNet.getAllNetFromPlayer(player);
        for (DimensionsNet net : nets) {
            int count = countAmmoInNetwork(gunStack, net);
            if (count > 0) return count;
        }
        return 0;
    }

    public static int countAmmoInNetwork(ItemStack gunStack, DimensionsNet net) {
        if (hasCreativeAmmoBoxInNetwork(gunStack, net)) return Integer.MAX_VALUE;
        ItemStack reference = getAmmoReference(gunStack);
        if (reference != null) {
            ItemStackKey key = new ItemStackKey(reference);
            KeyAmount found = net.getUnifiedStorage().getStackByKey(key);
            long count = found.amount();
            if (count > 0) return (int) Math.min(count, Integer.MAX_VALUE);
        }
        return 0;
    }

    public static int countAmmoInNetworkByAmmoId(ResourceLocation ammoId, DimensionsNet net) {
        if (ammoId == null || net == null) return 0;
        if (hasCreativeAmmoBoxInNetwork(ammoId, net)) return Integer.MAX_VALUE;
        ItemStackKey key = buildAmmoKey(ammoId);
        if (key == null) return 0;
        KeyAmount found = net.getUnifiedStorage().getStackByKey(key);
        long count = found.amount();
        if (count > 0) return (int) Math.min(count, Integer.MAX_VALUE);
        return 0;
    }

    public static int countAmmoInNetworkByAmmoId(ResourceLocation ammoId, ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        return countAmmoInNetworkByAmmoId(ammoId, net);
    }

    public static boolean hasCreativeAmmoBoxInNetwork(ResourceLocation ammoId, DimensionsNet net) {
        if (ammoId == null) return false;
        if (net instanceof TaczCreativeAccessor tacz) {
            Map<String, Integer> counts = tacz.getTaczCreativeCounts();
            if (counts.getOrDefault("*", 0) > 0) return true;
            if (counts.getOrDefault(ammoId.toString(), 0) > 0) return true;
        }
        UnifiedStorage storage = net.getUnifiedStorage();
        var opt = storage.getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return false;
        TypeBucket bucket = opt.get();
        for (int i = 0; i < bucket.size(); i++) {
            IStackKey<?> rawKey = bucket.get(i);
            if (!(rawKey instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.getReadOnlyStack();
            if (!(stack.getItem() instanceof IAmmoBox box)) continue;
            if (box.isAllTypeCreative(stack)) return true;
            if (box.isCreative(stack) && ammoId.equals(box.getAmmoId(stack))) return true;
        }
        return false;
    }

    public static ResourceLocation getAmmoId(ItemStack gunStack) {
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) return null;
        ResourceLocation gunId = iGun.getGunId(gunStack);
        if (gunId == null) return null;
        var opt = TimelessAPI.getCommonGunIndex(gunId);
        if (opt.isEmpty()) return null;
        return opt.get().getGunData().getAmmoId();
    }

    @OnlyIn(Dist.CLIENT)
    public static ResourceLocation getAmmoIdClient(ItemStack gunStack) {
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) return null;
        ResourceLocation gunId = iGun.getGunId(gunStack);
        if (gunId == null) return null;
        var opt = com.tacz.guns.api.TimelessAPI.getClientGunIndex(gunId);
        if (opt.isEmpty()) return null;
        return opt.get().getGunData().getAmmoId();
    }

    public static boolean hasCreativeAmmoBoxInNetwork(ItemStack gunStack, ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return false;
        return hasCreativeAmmoBoxInNetwork(gunStack, net);
    }

    public static boolean hasCreativeAmmoBoxInNetwork(ItemStack gunStack, DimensionsNet net) {
        ResourceLocation ammoId = getAmmoId(gunStack);
        if (ammoId == null) return false;
        if (net instanceof TaczCreativeAccessor tacz) {
            Map<String, Integer> counts = tacz.getTaczCreativeCounts();
            if (counts.getOrDefault("*", 0) > 0) return true;
            if (counts.getOrDefault(ammoId.toString(), 0) > 0) return true;
        }
        UnifiedStorage storage = net.getUnifiedStorage();
        var opt = storage.getBucket(ItemStackKey.ID);
        if (opt.isEmpty()) return false;
        TypeBucket bucket = opt.get();
        for (int i = 0; i < bucket.size(); i++) {
            IStackKey<?> rawKey = bucket.get(i);
            if (!(rawKey instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.getReadOnlyStack();
            if (!(stack.getItem() instanceof IAmmoBox box)) continue;
            if (box.isAllTypeCreative(stack)) return true;
            if (box.isCreative(stack) && ammoId.equals(box.getAmmoId(stack))) return true;
        }
        return false;
    }

    private static ItemStack getAmmoReference(ItemStack gunStack) {
        ResourceLocation ammoId = getAmmoId(gunStack);
        if (ammoId == null) return null;
        return buildAmmoStack(ammoId);
    }

    private static ItemStackKey buildAmmoKey(ResourceLocation ammoId) {
        ItemStack ref = buildAmmoStack(ammoId);
        return ref != null ? new ItemStackKey(ref) : null;
    }

    private static ItemStack buildAmmoStack(ResourceLocation ammoId) {
        ItemStack ref = new ItemStack(ModItems.AMMO.get());
        if (ref.getItem() instanceof IAmmo iAmmo) {
            iAmmo.setAmmoId(ref, ammoId);
        }
        return ref;
    }
}
