package com.solr98.beyondintegration.feature.ammo.tacz;

import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.tacz.guns.api.TimelessAPI;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.resources.ResourceLocation;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 网络弹药处理工具（通用实体入口）
 * 按 玩家网络 → 实体身上终端 → 女仆终端 的优先级顺序，
 * 提供弹药可用性判断与弹药消耗（提取）功能。
 */
public class NetworkAmmoHandler {

    /**
     * 判断射击者是否拥有可用网络弹药（依次检查玩家网络、实体终端、女仆终端）
     */
    public static boolean hasNetworkAmmo(LivingEntity shooter, ItemStack gun) {
        ServerPlayer player = findServerPlayer(shooter);
        if (player != null) {
            int count = TaczAmmoExtractor.countAmmoInNetwork(gun, player);
            if (count > 0) return true;
        }

        DimensionsNet net = findTerminalOnEntity(shooter);
        if (net != null && TaczAmmoExtractor.countAmmoInNetwork(gun, net) > 0) return true;

        if (ModList.get().isLoaded("touhou_little_maid")) {
            net = MaidNetworkHelper.findTerminal(shooter);
            if (net != null && TaczAmmoExtractor.countAmmoInNetwork(gun, net) > 0) return true;
        }

        return false;
    }

    /**
     * 从网络消耗弹药（依次尝试玩家网络、实体终端、女仆终端）
     *
     * @return 实际消耗数量；0 表示无可用弹药
     */
    public static int consumeFromNetwork(LivingEntity shooter, ItemStack gun, int needed) {
        if (needed <= 0) return 0;

        ServerPlayer player = findServerPlayer(shooter);
        if (player != null) {
            int taken = TaczAmmoExtractor.consumeAmmoDirectly(gun, needed, player);
            if (taken > 0) return taken;
        }

        DimensionsNet net = findTerminalOnEntity(shooter);
        if (net != null) {
            int taken = TaczAmmoExtractor.consumeAmmoDirectly(gun, needed, net);
            if (taken > 0) return taken;
        }

        if (ModList.get().isLoaded("touhou_little_maid")) {
            net = MaidNetworkHelper.findTerminal(shooter);
            if (net != null) {
                int taken = TaczAmmoExtractor.consumeAmmoDirectly(gun, needed, net);
                if (taken > 0) return taken;
            }
        }

        return 0;
    }

    /**
     * 将实体转换为服务端玩家（非玩家实体返回 null）
     */
    @Nullable
    private static ServerPlayer findServerPlayer(LivingEntity shooter) {
        return shooter instanceof ServerPlayer sp ? sp : null;
    }

    /**
     * 在实体背包/物品栏中查找携带终端物品对应的第一个网络
     */
    @Nullable
    public static DimensionsNet findTerminalOnEntity(LivingEntity entity) {
        Optional<IItemHandler> opt = entity.getCapability(
                net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).resolve();
        if (opt.isEmpty()) return null;
        IItemHandler inv = opt.get();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) return net;
            }
        }
        return null;
    }

    /**
     * 在指定物品栏处理器中查找携带终端物品对应的第一个网络
     */
    @Nullable
    public static DimensionsNet findTerminalInHandler(IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (net != null) return net;
            }
        }
        return null;
    }

    /**
     * 获取枪械所需的弹药 ID（服务端）
     */
    @Nullable
    public static ResourceLocation getAmmoId(ItemStack gun) {
        if (gun.isEmpty()) return null;
        Optional<com.tacz.guns.resource.index.CommonGunIndex> opt = TimelessAPI.getCommonGunIndex(
                com.tacz.guns.api.item.IGun.getIGunOrNull(gun).getGunId(gun));
        return opt.map(index -> index.getGunData().getAmmoId()).orElse(null);
    }
}
