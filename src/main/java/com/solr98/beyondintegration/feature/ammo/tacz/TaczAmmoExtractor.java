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
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.TaczCreativeAccessor;
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
 * TACZ 弹药提取工具类
 * 提供从玩家主网络/备用网络中统计、消耗（提取）TACZ 与 SW 弹药的统一入口，
 * 支持创造弹药箱无限弹药判定、弹药盒拆箱、按弹药 ID 统计以及终端物品定位。
 */
public class TaczAmmoExtractor {

    /**
     * 从玩家主网络直接消耗弹药（仅主网络）
     *
     * @return 实际消耗数量；0 表示无可用弹药
     */
    public static int consumeAmmoDirectly(ItemStack gunStack, int neededAmount, ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        int taken = consumeAmmoDirectly(gunStack, neededAmount, net);
        if (taken > 0) {
            PlayerNetUsageTracker.record(player.getUUID(), net.getId());
        }
        return taken;
    }

    /**
     * 从指定网络直接消耗弹药（创造箱无限时直接满足需求；扣弹后同步缓存）
     */
    public static int consumeAmmoDirectly(ItemStack gunStack, int neededAmount, DimensionsNet net) {
        if (hasCreativeAmmoBoxInNetwork(gunStack, net)) return neededAmount;
        ItemStack reference = getAmmoReference(gunStack);
        if (reference != null) {
            ItemStackKey key = new ItemStackKey(reference);
            KeyAmount extracted = net.getUnifiedStorage().extract(key, neededAmount, false, false);
            if (extracted.amount() > 0) {
                net.setDirty();
                syncConsumed(net, reference, extracted.amount());
                return (int) extracted.amount();
            }
        }
        return 0;
    }

    /** 扣弹成功后同步扣减查询缓存（保持即时一致，无缓存则跳过） */
    private static void syncConsumed(DimensionsNet net, ItemStack ref, long amount) {
        if (ref.getItem() instanceof IAmmo iAmmo) {
            ResourceLocation ammoId = iAmmo.getAmmoId(ref);
            if (ammoId != null) {
                TaczAmmoTracker.notifyConsumed(net, ammoId.toString(),
                        (int) Math.min(amount, Integer.MAX_VALUE));
            }
        }
    }

    /**
     * 从玩家主网络提取弹药到玩家背包（背包满则丢弃；提取后同步缓存）
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
            syncConsumed(net, reference, extracted.amount());
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
     * 统计玩家主网络中的 Superb 弹药（无限时返回 Long.MAX_VALUE）
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
     * 从玩家主网络消耗 ITEM 弹药
     */
    public static long consumeItemAcrossNets(ServerPlayer player, ItemStackKey itemKey, long amount) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        KeyAmount extracted = net.getUnifiedStorage().extract(itemKey, amount, false, true);
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
     * 统计玩家主网络中的可用弹药数量（仅主网络）
     */
    public static int countAmmoInNetwork(ItemStack gunStack, ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return 0;
        return countAmmoInNetwork(gunStack, net);
    }

    /**
     * 统计指定网络中的弹药数量（创造箱无限时返回 Integer.MAX_VALUE；走查询缓存 O(1)）
     */
    public static int countAmmoInNetwork(ItemStack gunStack, DimensionsNet net) {
        if (net == null) return 0;
        ResourceLocation ammoId = getAmmoId(gunStack);
        if (ammoId == null) return 0;
        return TaczAmmoTracker.countAvailable(net, ammoId);
    }

    /**
     * 按弹药 ID 统计指定网络中的弹药数量（走查询缓存 O(1)）
     */
    public static int countAmmoInNetworkByAmmoId(ResourceLocation ammoId, DimensionsNet net) {
        if (ammoId == null || net == null) return 0;
        return TaczAmmoTracker.countAvailable(net, ammoId);
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
     * 检查网络中是否存在匹配弹药 ID 的创造弹药箱（O(1) 只读虚拟计数，
     * 计数由 BD 统一输入输出 delta 事件维护并持久化；物理扫描仅在对账时执行）
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
     * 全量统计网络内全部 TACZ 弹药（虚拟创造箱计数 + 实体弹药/弹药箱），
     * 全类型创造弹药箱时结果仅含 "*" -> Integer.MAX_VALUE
     */
    public static Map<String, Integer> countAllAmmoInNetwork(DimensionsNet net) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (net == null) return result;

        // Check TaczCreativeAccessor for creative boxes
        if (net instanceof TaczCreativeAccessor tacz) {
            Map<String, Integer> creativeCounts = tacz.getTaczCreativeCounts();
            for (var entry : creativeCounts.entrySet()) {
                if (entry.getValue() > 0) {
                    result.put(entry.getKey(), Integer.MAX_VALUE);
                }
            }
            if (creativeCounts.containsKey("*") && creativeCounts.get("*") > 0) {
                result.put("*", Integer.MAX_VALUE);
                return result;
            }
        }

        // Scan storage (single pass, KeyAmount already carries amounts) for ammo items and ammo boxes
        UnifiedStorage storage = net.getUnifiedStorage();
        for (KeyAmount ka : storage.getStorage()) {
            if (!(ka.key() instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.getReadOnlyStack();

            if (stack.getItem() instanceof IAmmoBox box) {
                if (box.isAllTypeCreative(stack)) {
                    result.clear();
                    result.put("*", Integer.MAX_VALUE);
                    return result;
                }
                ResourceLocation boxAmmoId = box.getAmmoId(stack);
                if (box.isCreative(stack) && boxAmmoId != null) {
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
                            // 同弹药 id 可能分布多个 key（不同 NBT），按 id 累加而非覆盖
                            long sum = (existing == null ? 0L : (long) existing) + count;
                            result.put(idStr, (int) Math.min(sum, Integer.MAX_VALUE));
                        }
                    }
                }
            }
        }

        return result;
    }

    // 按具体 ItemStackKey 统计网络中的 TACZ 弹药（供 delta 增量追踪对齐全量快照）
    public static Map<ItemStackKey, Integer> countAmmoByKey(DimensionsNet net) {
        Map<ItemStackKey, Integer> result = new LinkedHashMap<>();
        if (net == null) return result;
        for (KeyAmount ka : net.getUnifiedStorage().getStorage()) {
            if (!(ka.key() instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.getReadOnlyStack();
            if (!(stack.getItem() instanceof IAmmo)) continue;
            long count = ka.amount();
            if (count > 0) result.put(ik, (int) Math.min(count, Integer.MAX_VALUE));
        }
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
