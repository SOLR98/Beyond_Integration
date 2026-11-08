package com.solr98.beyondintegration.feature.soul;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 网络灵魂源激活（网络级、一次性、随存档持久化）。
 * <p>默认通过本模组仪式（{@link SoulSourceRitual}）激活；未激活时网络不参与取魂。
 */
public final class SoulArkActivation {

    public enum Result {
        OK, ALREADY, NOT_ENABLED, NO_ARK_ITEM, INSUFFICIENT, NO_NET
    }

    private SoulArkActivation() {
    }

    /** 直接激活（供 OP 命令使用；不消耗物品）。 */
    public static Result tryActivate(ServerPlayer player, DimensionsNet net) {
        if (!CommandConfig.soulEnabled()) return Result.NOT_ENABLED;
        if (net == null) return Result.NO_NET;
        NetworkAmmoData.Attachment data = NetworkAmmoData.getOrCreate(net.getId());
        if (data.isSoulArkActivated()) return Result.ALREADY;
        activate(net, data);
        return Result.OK;
    }

    /** 仪式激活：由 {@link SoulSourceRitual} 调用。 */
    public static void activateByRitual(DimensionsNet net, net.minecraft.world.entity.player.Player player) {
        if (net == null || !CommandConfig.soulEnabled()) return;
        NetworkAmmoData.Attachment data = NetworkAmmoData.getOrCreate(net.getId());
        if (data.isSoulArkActivated()) {
            if (player instanceof ServerPlayer sp) sp.sendSystemMessage(message(Result.ALREADY));
            return;
        }
        activate(net, data);
    }

    /** 重置网络的灵魂源激活状态。 */
    public static boolean reset(DimensionsNet net) {
        if (net == null) return false;
        NetworkAmmoData.Attachment data = NetworkAmmoData.getOrCreate(net.getId());
        if (!data.isSoulArkActivated()) return false;
        data.setSoulArkActivated(false);
        net.setDirty();
        return true;
    }

    private static void activate(DimensionsNet net, NetworkAmmoData.Attachment data) {
        data.setSoulArkActivated(true);
        net.setDirty();
        SoulDebug.log("soul source ACTIVATED for net {}", net.getId());
        broadcastToNet(net, activatedMessage(net));
    }

    private static Component activatedMessage(DimensionsNet net) {
        String custom = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        Component name = custom != null && !custom.isEmpty()
                ? Component.literal(custom)
                : Component.translatable("menu.text.beyonddimensions.net.default_name", net.getId());
        return Component.translatable("message.beyond_integration.soul_ark.activated", name, net.getId());
    }

    private static void broadcastToNet(DimensionsNet net, Component message) {
        if (net == null || message == null) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (isMember(net, sp.getUUID())) sp.sendSystemMessage(message);
        }
    }

    private static boolean isMember(DimensionsNet net, java.util.UUID id) {
        return id.equals(net.getOwner()) || net.getManagers().contains(id) || net.getPlayers().contains(id);
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
