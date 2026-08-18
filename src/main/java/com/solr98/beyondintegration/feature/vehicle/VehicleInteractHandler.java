package com.solr98.beyondintegration.feature.vehicle;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

/**
 * 载具绑定交互处理器：玩家手持已接入网络的 NetedItem
 * 右键载具（Superb Warfare）时，将该网络绑定到载具的网络缓存，
 * 并向玩家发送全量状态（弹药、能量、网络名、附魔开关）。
 */
public class VehicleInteractHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** 是否已探测过载具类（惰性加载缓存） */
    private static boolean vehicleChecked = false;
    /** 缓存的载具实体类引用，null 表示目标模组未加载 */
    private static Class<?> vehicleClass = null;

    /**
     * 实体交互事件：拦截对载具的右键操作并执行网络绑定。
     * 服务端才执行绑定逻辑；无网络或未持接入物品时直接忽略。
     */
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!isVehicleEntity(event.getTarget())) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        int netId = NetedItem.getNetId(stack);
        if (netId < 0) return;

        if (event.getSide().isClient()) return;

        event.setCanceled(true);

        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            LOGGER.warn("Vehicle bind failed: net {} not found", netId);
            return;
        }

        VehicleEntity vehicle = (VehicleEntity) event.getTarget();
        VehicleNetCache cache = ((INetCachedVehicle) vehicle).getNetCache();
        cache.attach(netId, null);

        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.beyond_integration.vehicle_bound", netId));

            VehicleNetCache.PushData push = cache.refresh();
            if (push != null && push.full()) {
                PacketHandler.sendToPlayer(serverPlayer, new SuperbAmmoStatusResponsePacket(
                        netId, push.netName(), push.energy(), push.enchantSeparation(),
                        push.ammo(), cache.getAmmoList()));
            }
        }
    }

    /** 判断目标实体是否为载具（类名反射探测，仅探测一次） */
    private static boolean isVehicleEntity(Object target) {
        if (!vehicleChecked) {
            vehicleChecked = true;
            try {
                vehicleClass = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            } catch (Exception ignored) {
            }
        }
        return vehicleClass != null && vehicleClass.isInstance(target);
    }
}
