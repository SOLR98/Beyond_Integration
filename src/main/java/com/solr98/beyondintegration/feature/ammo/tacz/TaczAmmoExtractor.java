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
import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.solr98.beyondintegration.compat.tud.TudAmmoCompat;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.feature.ammo.sw.SwAmmoStorage;
import com.solr98.beyondintegration.handler.NetTerminalHelper;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

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
     * 统计玩家主网络中的 SW 弹药（按弹药物品键；无限弹药箱在网时返回 Long.MAX_VALUE）
     */
    public static long countSuperbAmmoAcrossNets(ServerPlayer player, String ammoType) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        Ammo ammo = Ammo.getType(ammoType);
        if (net == null || ammo == null) return 0;
        if (SwAmmoStorage.hasInfinite(net)) return Long.MAX_VALUE;
        return SwAmmoStorage.count(net, ammo);
    }

    /**
     * 从玩家主网络消耗 SW 弹药（扣散装弹药物品；无限弹药箱在网时不扣）
     */
    public static long consumeSuperbAmmoAcrossNets(ServerPlayer player, String ammoType, long amount) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        Ammo ammo = Ammo.getType(ammoType);
        if (net == null || ammo == null) return 0;
        return SwAmmoStorage.consume(net, ammo, amount);
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
     * 在玩家背包中查找携带终端物品对应的第一个网络
     */
    public static DimensionsNet findTerminalInInventory(ServerPlayer player) {
        IItemHandler inv = player.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ENTITY);
        if (inv == null) return null;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (!NetTerminalHelper.isTerminal(stack)) continue;
                int netId = com.solr98.beyondintegration.core.util.BdItemData.netId(stack);
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
        IItemHandler inv = player.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ENTITY);
        if (inv == null) return nets;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (!NetTerminalHelper.isTerminal(stack)) continue;
                int netId = com.solr98.beyondintegration.core.util.BdItemData.netId(stack);
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
     * 获取枪械所需的弹药 ID（服务端，从通用数据索引读取）。
     * TUD 通用弹药枪优先走 TUD 当前弹药解析（动态跟随枪 NBT/配置，含 $ 物品弹药）。
     */
    public static ResourceLocation getAmmoId(ItemStack gunStack) {
        ResourceLocation tudAmmo = TudAmmoCompat.resolve(gunStack);
        if (tudAmmo != null) return tudAmmo;
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) return null;
        ResourceLocation gunId = iGun.getGunId(gunStack);
        if (gunId == null) return null;
        var opt = TimelessAPI.getCommonGunIndex(gunId);
        if (opt.isEmpty()) return null;
        return opt.get().getGunData().getAmmoId();
    }

    /**
     * 获取枪械所需的弹药 ID（仅客户端，从客户端数据索引读取）。
     * TUD 通用弹药枪优先走 TUD 当前弹药解析（TOML 双端可用；JSON 依赖客户端本地配置，
     * 配置缺失时回退原版路径），含 $ 物品弹药。
     */
    @OnlyIn(Dist.CLIENT)
    public static ResourceLocation getAmmoIdClient(ItemStack gunStack) {
        ResourceLocation tudAmmo = TudAmmoCompat.resolve(gunStack);
        if (tudAmmo != null) return tudAmmo;
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) return null;
        ResourceLocation gunId = iGun.getGunId(gunStack);
        if (gunId == null) return null;
        var opt = com.tacz.guns.api.TimelessAPI.getClientGunIndex(gunId);
        if (opt.isEmpty()) return null;
        return opt.get().getGunData().getAmmoId();
    }

    /**
     * 全量统计网络内全部 TACZ 弹药（服务端索引查询）。
     * <p>物理弹药由 {@link TaczAmmoIndex} 按 delta 增量维护（O(弹药键数)，不再全桶遍历），
     * 创造箱虚拟计数由 {@link TaczAmmoTracker} 维护并在此合并（{@code "*"} = 全类型无限）。
     */
    public static Map<String, Integer> countAllAmmoInNetwork(DimensionsNet net) {
        return TaczAmmoIndex.snapshot(net);
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
     * 构建指定弹药 ID 的参考物品堆：
     * TUD 物品弹药（$ 前缀）→ 对应普通物品堆（无 NBT，1 个物品 = 1 发）；
     * 其余（TACZ 弹药）→ tacz:ammo 物品 + 弹药 ID NBT。
     */
    private static ItemStack buildAmmoStack(ResourceLocation ammoId) {
        ItemStack itemAmmo = TudAmmoCompat.getItemAmmoStack(ammoId);
        if (itemAmmo != null && !itemAmmo.isEmpty()) {
            return itemAmmo;
        }
        ItemStack ref = new ItemStack(ModItems.AMMO.get());
        if (ref.getItem() instanceof IAmmo iAmmo) {
            iAmmo.setAmmoId(ref, ammoId);
        }
        return ref;
    }
    /** 统计玩家主网络中的弹药总数（仅主网络）。 */
    /**
     * 统计玩家**主网络**内的弹药数量（与 {@link #tryConsumeFromAll} 同口径，名称保留兼容调用方）。
     */
    public static int countAmmoFromAll(ServerPlayer player, ItemStack gunStack) {
        DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (primary == null) return 0;
        return countAmmoInNetwork(gunStack, primary);
    }

    /**
     * 从玩家**主网络**消耗弹药，成功后记录网络使用情况。
     * <p>名称保留 {@code FromAll} 是为兼容 taczaddon 1.21.1 的直调（{@code BeyondAmmoServerCompatInner}），
     * 实际只查主网络：多网络循环扣弹在实现上异常复杂且易错（跨网络部分扣减/回滚/计数一致性），
     * 因此有意限制为单一主网络。
     */
    public static int tryConsumeFromAll(ServerPlayer player, ItemStack gunStack, int neededAmount) {
        DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (primary == null) return 0;
        int taken = consumeAmmoDirectly(gunStack, neededAmount, primary);
        if (taken > 0) {
            com.solr98.beyondintegration.feature.ammo.tacz.PlayerNetUsageTracker
                    .record(player.getUUID(), primary.getId());
            return taken;
        }
        return 0;
    }

    /** 统计女仆（touhou_little_maid）终端所在网络的弹药数量（模组未加载时返回 0）。 */
    public static int countAmmoFromMaid(net.minecraft.world.entity.LivingEntity entity, ItemStack gunStack) {
        if (!net.neoforged.fml.ModList.get().isLoaded("touhou_little_maid")) return 0;
        DimensionsNet net = com.solr98.beyondintegration.maid.MaidNetworkHelper.findTerminal(entity);
        if (net == null) return 0;
        return countAmmoInNetwork(gunStack, net);
    }

    /** 从女仆终端所在网络尝试消耗弹药（模组未加载时返回 0）。 */
    public static int tryConsumeFromMaid(net.minecraft.world.entity.LivingEntity entity, ItemStack gunStack, int neededAmount) {
        if (!net.neoforged.fml.ModList.get().isLoaded("touhou_little_maid")) return 0;
        DimensionsNet net = com.solr98.beyondintegration.maid.MaidNetworkHelper.findTerminal(entity);
        if (net == null) return 0;
        return consumeAmmoDirectly(gunStack, neededAmount, net);
    }
}
