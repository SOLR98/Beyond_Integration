package com.solr98.beyondintegration.feature.ammo.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.init.ModItems;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TACZ 弹药提取工具类（API 直查 + 返回值驱动扣减）
 *
 * 查询：物理弹药直接遍历 BD 存储（按弹药 ID 累加，跨 NBT 变种聚合）；
 *       创造箱走 TaczAmmoTracker 虚拟计数缓存（O(1)，不扫存储）。
 * 扣减：主路径以 reference key 精确 extract（返回值即实扣量），
 *       不足时按弹药 ID 兜底遍历真实 key 逐个扣除——正确性完全由 BD 返回值保证，
 *       不依赖任何查询缓存。
 */
public class TaczAmmoExtractor {

    /**
     * 从玩家主网络直接消耗弹药（仅主网络；扣弹后立即推送快照给玩家，HUD 实时更新）
     *
     * @return 实际消耗数量；0 表示无可用弹药
     */
    public static int consumeAmmoDirectly(ItemStack gunStack, int neededAmount, ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        int taken = consumeAmmoDirectly(gunStack, neededAmount, net);
        if (taken > 0) {
            PlayerNetUsageTracker.record(player.getUUID(), net.getId());
            TaczAmmoPollingService.pushSnapshotToPlayer(player, net.getId());
        }
        return taken;
    }

    /**
     * 从指定网络按弹药 ID 直接消耗弹药（公开 API，供其他模组/女仆调用；
     * 创造箱无限时直接满足需求；只扣精确 reference key，返回值驱动）
     *
     * @return 实际消耗数量；0 表示无可用弹药
     */
    public static int consumeAmmoByAmmoId(ResourceLocation ammoId, int neededAmount, DimensionsNet net) {
        if (ammoId == null || net == null || neededAmount <= 0) return 0;
        if (TaczAmmoTracker.isInfinite(net, ammoId)) return neededAmount;

        ItemStackKey refKey = new ItemStackKey(buildAmmoStack(ammoId));
        KeyAmount r = net.getUnifiedStorage().extract(refKey, neededAmount, false, false);
        if (r.amount() > 0) {
            net.setDirty();
            return (int) r.amount();
        }
        return 0;
    }

    /**
     * 从指定网络直接消耗弹药（创造箱无限时直接满足需求；只扣精确 reference key，返回值驱动）
     */
    public static int consumeAmmoDirectly(ItemStack gunStack, int neededAmount, DimensionsNet net) {
        if (hasCreativeAmmoBoxInNetwork(gunStack, net)) return neededAmount;
        ResourceLocation ammoId = getAmmoId(gunStack);
        if (ammoId == null) return 0;

        ItemStackKey refKey = new ItemStackKey(buildAmmoStack(ammoId));
        KeyAmount r = net.getUnifiedStorage().extract(refKey, neededAmount, false, false);
        if (r.amount() > 0) {
            net.setDirty();
            return (int) r.amount();
        }
        return 0;
    }

    /**
     * 从玩家主网络提取弹药到玩家背包（背包满则丢弃；返回值驱动）
     *
     * @return 实际提取数量
     */
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

    /**
     * 获取玩家所在的主网络
     */
    public static DimensionsNet findNetworkForPlayer(ServerPlayer player) {
        return DimensionsNet.getPrimaryNetFromPlayer(player);
    }

    /**
     * 统计玩家主网络中的 Superb 弹药（无限时返回 Long.MAX_VALUE；map 直读）
     */
    public static long countSuperbAmmoAcrossNets(ServerPlayer player, String ammoType) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (!(net instanceof SuperbAmmoAccessor acc)) return 0;
        var map = acc.getSuperbAmmo();
        if (map.getOrDefault("__infinite__", 0L) > 0) return Long.MAX_VALUE;
        return map.getOrDefault(ammoType, 0L);
    }

    /**
     * 从玩家主网络消耗 Superb 弹药（无限时不消耗）
     */
    public static long consumeSuperbAmmoAcrossNets(ServerPlayer player, String ammoType, long amount) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (!(net instanceof SuperbAmmoAccessor acc)) return 0;
        var map = acc.getSuperbAmmo();
        if (map.getOrDefault("__infinite__", 0L) > 0) return amount;
        long current = map.getOrDefault(ammoType, 0L);
        if (current <= 0) return 0;
        long take = Math.min(current, amount);
        long left = current - take;
        if (left <= 0) map.remove(ammoType);
        else map.put(ammoType, left);
        net.setDirty();
        return take;
    }

    /**
     * 从玩家主网络消耗 ITEM 弹药（SW 弹药无 NBT 变种，精确 key 直扣）
     */
    public static long consumeItemAcrossNets(ServerPlayer player, ItemStackKey itemKey, long amount) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        KeyAmount extracted = net.getUnifiedStorage().extract(itemKey, amount, false, false);
        if (extracted.amount() > 0) {
            net.setDirty();
            return extracted.amount();
        }
        return 0;
    }

    /**
     * 解析玩家主网络对应的 SW 虚拟弹药访问器（非 SW 网络返回 null）
     */
    public static SuperbAmmoAccessor resolveSuperbAmmo(ServerPlayer player) {
        var net = findNetworkForPlayer(player);
        if (net instanceof SuperbAmmoAccessor acc) return acc;
        return null;
    }

    /**
     * 在玩家背包中查找携带终端物品对应的第一个网络
     */
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

    /**
     * 在玩家背包中查找全部终端物品对应的网络（去重）
     */
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

    /**
     * 统计玩家主网络中的可用弹药数量（仅主网络；API 直查）
     */
    public static int countAmmoInNetwork(ItemStack gunStack, ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        return countAmmoInNetwork(gunStack, net);
    }

    /**
     * 统计指定网络中的弹药数量（API 直查：创造箱缓存 O(1) 无限判定 + 存储按弹药 ID 累加）
     */
    public static int countAmmoInNetwork(ItemStack gunStack, DimensionsNet net) {
        if (net == null) return 0;
        ResourceLocation ammoId = getAmmoId(gunStack);
        if (ammoId == null) return 0;
        return countAmmoInNetworkByAmmoId(ammoId, net);
    }

    /**
     * 按弹药 ID 统计指定网络中的弹药数量（API 精确查询：创造箱缓存 O(1) + reference key getStackByKey）
     */
    public static int countAmmoInNetworkByAmmoId(ResourceLocation ammoId, DimensionsNet net) {
        if (ammoId == null || net == null) return 0;
        if (TaczAmmoTracker.isInfinite(net, ammoId)) return Integer.MAX_VALUE;
        ItemStackKey refKey = new ItemStackKey(buildAmmoStack(ammoId));
        KeyAmount found = net.getUnifiedStorage().getStackByKey(refKey);
        return (int) Math.min(found.amount(), Integer.MAX_VALUE);
    }

    /**
     * 按弹药 ID 统计玩家主网络中的弹药数量
     */
    public static int countAmmoInNetworkByAmmoId(ResourceLocation ammoId, ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        return countAmmoInNetworkByAmmoId(ammoId, net);
    }

    /**
     * 检查网络中是否存在匹配弹药 ID 的创造弹药箱（O(1) 虚拟计数缓存直读，不扫存储）
     */
    public static boolean hasCreativeAmmoBoxInNetwork(ResourceLocation ammoId, DimensionsNet net) {
        return TaczAmmoTracker.isInfinite(net, ammoId);
    }

    /**
     * 获取枪械所需的弹药 ID（服务端，从通用数据索引读取）
     */
    public static ResourceLocation getAmmoId(ItemStack gunStack) {
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) return null;
        ResourceLocation gunId = iGun.getGunId(gunStack);
        if (gunId == null) return null;
        var opt = TimelessAPI.getCommonGunIndex(gunId);
        if (opt.isEmpty()) return null;
        return opt.get().getGunData().getAmmoId();
    }

    /**
     * 获取枪械所需的弹药 ID（仅客户端，从客户端数据索引读取）
     */
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

    /**
     * 全量统计网络内全部 TACZ 弹药（API 直查：存储遍历 + 创造箱虚拟计数），
     * 供服务端推送客户端快照使用。遍历时顺带对账创造箱虚拟计数（只增不删，
     * 变化 markDirty 落盘），使创造弹更新不依赖 delta 事件，随快照周期自动收敛。
     */
    public static Map<String, Integer> countAllAmmoInNetwork(DimensionsNet net) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (net == null) return result;

        Map<String, Integer> creativeCounts = net instanceof com.solr98.beyondintegration.handler.TaczCreativeAccessor tacz
                ? tacz.getTaczCreativeCounts() : null;
        boolean creativeChanged = false;

        // 存储遍历：物理弹药按弹药 ID 累加 + 创造箱对账/无限标记
        UnifiedStorage storage = net.getUnifiedStorage();
        for (KeyAmount ka : storage.getStorage()) {
            if (!(ka.key() instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.getReadOnlyStack();

            if (stack.getItem() instanceof IAmmoBox box) {
                if (box.isAllTypeCreative(stack)) {
                    if (creativeCounts != null && creativeCounts.getOrDefault("*", 0) <= 0) {
                        creativeCounts.put("*", 1);
                        creativeChanged = true;
                    }
                    result.clear();
                    result.put("*", Integer.MAX_VALUE);
                    if (creativeChanged) NetworkAmmoData.markDirty();
                    return result;
                }
                ResourceLocation boxAmmoId = box.getAmmoId(stack);
                if (box.isCreative(stack) && boxAmmoId != null) {
                    if (creativeCounts != null && creativeCounts.getOrDefault(boxAmmoId.toString(), 0) <= 0) {
                        creativeCounts.put(boxAmmoId.toString(), 1);
                        creativeChanged = true;
                    }
                    result.put(boxAmmoId.toString(), Integer.MAX_VALUE);
                }
            } else if (stack.getItem() instanceof IAmmo iAmmo) {
                ResourceLocation ammoId = iAmmo.getAmmoId(stack);
                if (ammoId != null) {
                    String idStr = ammoId.toString();
                    Integer existing = result.get(idStr);
                    if (existing == null || existing != Integer.MAX_VALUE) {
                        long count = ka.amount();
                        if (count > 0) {
                            long sum = (existing == null ? 0L : (long) existing) + count;
                            result.put(idStr, (int) Math.min(sum, Integer.MAX_VALUE));
                        }
                    }
                }
            }
        }

        // 虚拟计数中的创造箱标记并入结果（delta 维护的条目，遍历兜底已补齐）
        if (creativeCounts != null) {
            if (creativeCounts.getOrDefault("*", 0) > 0) {
                result.clear();
                result.put("*", Integer.MAX_VALUE);
                return result;
            }
            for (var entry : creativeCounts.entrySet()) {
                if (!"*".equals(entry.getKey()) && entry.getValue() > 0) {
                    result.put(entry.getKey(), Integer.MAX_VALUE);
                }
            }
        }

        if (creativeChanged) NetworkAmmoData.markDirty();
        return result;
    }

    /**
     * 检查玩家主网络中是否有对应枪械的创造弹药箱
     */
    public static boolean hasCreativeAmmoBoxInNetwork(ItemStack gunStack, ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return false;
        return hasCreativeAmmoBoxInNetwork(gunStack, net);
    }

    /**
     * 检查指定网络中是否有对应枪械的创造弹药箱（O(1) 虚拟计数）
     */
    public static boolean hasCreativeAmmoBoxInNetwork(ItemStack gunStack, DimensionsNet net) {
        ResourceLocation ammoId = getAmmoId(gunStack);
        if (ammoId == null) return false;
        return TaczAmmoTracker.isInfinite(net, ammoId);
    }

    /**
     * 获取枪械对应弹药类型的参考物品堆（用于构建 ItemStackKey 查询键）
     */
    private static ItemStack getAmmoReference(ItemStack gunStack) {
        ResourceLocation ammoId = getAmmoId(gunStack);
        if (ammoId == null) return null;
        return buildAmmoStack(ammoId);
    }

    /**
     * 构建指定弹药 ID 的 tacz 弹药物品堆（写入弹药 ID NBT）
     */
    private static ItemStack buildAmmoStack(ResourceLocation ammoId) {
        ItemStack ref = new ItemStack(ModItems.AMMO.get());
        if (ref.getItem() instanceof IAmmo iAmmo) {
            iAmmo.setAmmoId(ref, ammoId);
        }
        return ref;
    }
}
