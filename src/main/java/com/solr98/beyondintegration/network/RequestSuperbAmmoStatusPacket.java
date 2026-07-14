package com.solr98.beyondintegration.network;

import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RequestSuperbAmmoStatusPacket {

    public RequestSuperbAmmoStatusPacket() {}

    public static void encode(RequestSuperbAmmoStatusPacket msg, FriendlyByteBuf buf) {}

    public static RequestSuperbAmmoStatusPacket decode(FriendlyByteBuf buf) {
        return new RequestSuperbAmmoStatusPacket();
    }

    public static void handle(RequestSuperbAmmoStatusPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;

            Map<String, Long> fullMap = buildAmmoMap(net);

            ItemStack held = player.getMainHandItem();
            if (held.getItem() instanceof GunItem) {
                GunData data = GunData.from(held);
                AmmoConsumer consumer = data.selectedAmmoConsumer();
                if (consumer != null && consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM && !consumer.stack().isEmpty()) {
                    long itemCount = net.getUnifiedStorage().getStackByKey(new ItemStackKey(consumer.stack())).amount();
                    var regKey = ForgeRegistries.ITEMS.getKey(consumer.stack().getItem());
                    if (regKey != null) {
                        fullMap.put("ITEM:" + regKey, itemCount);
                    }
                }
            }

            int netId = net.getId();
            long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
            boolean enchantSep = !(net instanceof EnchantSeparationAccessor ea)
                    || ea.beyond$isEnchantSeparationEnabled();
            PacketHandler.sendToPlayer(player, new SuperbAmmoStatusResponsePacket(netId, fullMap, energy, 0,
                    ((NetworkNameProvider) net).getCustomName(), enchantSep));
        });
        ctx.get().setPacketHandled(true);
    }

    private static Map<String, Long> buildAmmoMap(DimensionsNet net) {
        if (net instanceof SuperbAmmoAccessor acc) {
            return new HashMap<>(acc.getSuperbAmmo());
        }
        return new HashMap<>();
    }
}
