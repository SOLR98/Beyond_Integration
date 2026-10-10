package com.solr98.beyondintegration.feature.soul;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 网络方舟献祭激活（平衡门槛，网络级、一次性、随存档持久化）。
 * <p>玩家向网络献祭 1 个配置物品（默认 {@code goety:arca}「灵魂方舟」）以解锁该网络的灵魂源；
 * 未激活时网络不参与取魂，行为与现状一致。复用 {@link NetworkAmmoData} 持久化。
 */
public final class SoulArkActivation {

    public enum Result {
        OK, ALREADY, NOT_ENABLED, NO_ARK_ITEM, INSUFFICIENT, NO_NET
    }

    private SoulArkActivation() {
    }

    /** 尝试为网络献祭激活灵魂源。 */
    public static Result tryActivate(ServerPlayer player, DimensionsNet net) {
        if (!CommandConfig.soulEnabled()) return Result.NOT_ENABLED;
        if (net == null) return Result.NO_NET;

        NetworkAmmoData.Attachment att = NetworkAmmoData.getOrCreate(net.getId());
        if (att.isSoulArkActivated()) return Result.ALREADY;

        if (!CommandConfig.soulRequireArkSacrifice()) {
            activate(net, att);
            notifyActivated(player);
            return Result.OK;
        }

        ItemStack cost = arkStack();
        if (cost.isEmpty()) return Result.NO_ARK_ITEM;

        ItemStackKey key = new ItemStackKey(cost.copyWithCount(1));
        UnifiedStorage storage = net.getUnifiedStorage();
        long need = cost.getCount();
        if (storage.getStackByKey(key).amount() < need) return Result.INSUFFICIENT;
        if (storage.extract(key, need, false, false).amount() < need) return Result.INSUFFICIENT;

        activate(net, att);
        notifyActivated(player);
        return Result.OK;
    }

    /** 献祭成功提示（发给触发的玩家）。 */
    private static void notifyActivated(ServerPlayer player) {
        if (player != null) {
            player.sendSystemMessage(message(Result.OK));
        }
    }
    public static boolean reset(DimensionsNet net) {
        if (net == null) return false;
        NetworkAmmoData.Attachment att = NetworkAmmoData.getOrCreate(net.getId());
        if (!att.isSoulArkActivated()) return false;
        att.setSoulArkActivated(false);
        NetworkAmmoData.markDirty();
        net.setDirty();
        return true;
    }

    private static void activate(DimensionsNet net, NetworkAmmoData.Attachment att) {
        att.setSoulArkActivated(true);
        NetworkAmmoData.markDirty();
        net.setDirty();
        SoulDebug.log("soul ark ACTIVATED for net {}", net.getId());
    }

    /** 配置的献祭物品（默认 goety:arca）。 */
    private static ItemStack arkStack() {
        String id = CommandConfig.soulArkItem();
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(rl);
        return (item == null || item == Items.AIR) ? ItemStack.EMPTY : new ItemStack(item, 1);
    }

    /** 结果本地化提示。 */
    public static Component message(Result result) {
        return switch (result) {
            case OK -> Component.translatable("message.beyond_integration.soul_ark.activated");
            case ALREADY -> Component.translatable("message.beyond_integration.soul_ark.already");
            case NOT_ENABLED -> Component.translatable("message.beyond_integration.soul_ark.disabled");
            case NO_ARK_ITEM -> Component.translatable("message.beyond_integration.soul_ark.no_ark_item");
            case INSUFFICIENT -> Component.translatable("message.beyond_integration.soul_ark.insufficient");
            case NO_NET -> Component.translatable("message.beyond_integration.soul.net_missing");
        };
    }
}
