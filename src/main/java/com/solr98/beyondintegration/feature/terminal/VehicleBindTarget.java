package com.solr98.beyondintegration.feature.terminal;

import com.solr98.beyondintegration.feature.vehicle.VehicleNetCache;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 实体绑定目标（右键）：手持终端的玩家右键 Superb Warfare 载具 →
 * 把终端所绑定的网络绑定到载具的网络缓存，并推送全量状态（弹药/能量/名称/附魔开关）。
 * <p>等价迁移自原 {@code VehicleInteractHandler}。
 */
public final class VehicleBindTarget implements TerminalBindTarget {

    /** 是否已探测过载具类（惰性） */
    private static boolean checked = false;
    private static Class<?> vehicleClass = null;

    @Override
    public boolean supports(TerminalBindContext ctx) {
        ensure();
        return ctx.entity != null && vehicleClass != null && vehicleClass.isInstance(ctx.entity);
    }

    @Override
    public boolean bind(TerminalBindContext ctx) {
        if (ctx.clientSide) return true; // 客户端仅用于取消右键交互
        ServerPlayer sp = ctx.serverPlayer();
        if (sp == null) return true;

        DimensionsNet net = DimensionsNet.getNetFromId(ctx.netId);
        if (net == null) return true;

        com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity vehicle =
                (com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity) ctx.entity;
        VehicleNetCache cache = ((INetCachedVehicle) vehicle).getNetCache();
        cache.attach(ctx.netId, null);

        sp.sendSystemMessage(Component.translatable("message.beyond_integration.vehicle_bound", ctx.netId));
        VehicleNetCache.PushData push = cache.refresh();
        if (push != null && push.full()) {
            PacketHandler.sendToPlayer(sp, new SuperbAmmoStatusResponsePacket(
                    ctx.netId, push.netName(), push.energy(), push.enchantSeparation(),
                    push.ammo(), cache.getAmmoList()));
        }
        return true;
    }

    private static void ensure() {
        if (checked) return;
        checked = true;
        try {
            vehicleClass = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
        } catch (Exception ignored) {
        }
    }
}
