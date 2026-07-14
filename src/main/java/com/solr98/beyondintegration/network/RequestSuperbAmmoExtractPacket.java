package com.solr98.beyondintegration.network;

import com.atsuishio.superbwarfare.data.gun.Ammo;
// DELETED: import com.solr98.beyondintegration.handler.BDNetworkHelper;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RequestSuperbAmmoExtractPacket {

    private final String ammoType;
    private final long count;

    public RequestSuperbAmmoExtractPacket(String ammoType, long count) {
        this.ammoType = ammoType;
        this.count = count;
    }

    public static void encode(RequestSuperbAmmoExtractPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.ammoType);
        buf.writeVarLong(msg.count);
    }

    public static RequestSuperbAmmoExtractPacket decode(FriendlyByteBuf buf) {
        return new RequestSuperbAmmoExtractPacket(buf.readUtf(), buf.readVarLong());
    }

    public static void handle(RequestSuperbAmmoExtractPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || msg.count <= 0) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null || !(net instanceof SuperbAmmoAccessor acc)) return;

            Map<String, Long> ammoMap = acc.getSuperbAmmo();

            if ("__infinite__".equals(msg.ammoType)) return;

            long infinite = ammoMap.getOrDefault("__infinite__", 0L);
            long current = ammoMap.getOrDefault(msg.ammoType, 0L);
            if (current <= 0 && infinite <= 0) return;

            long toExtract;
            if (infinite > 0) {
                toExtract = msg.count;
            } else {
                toExtract = Math.min(current, msg.count);
                long remaining = current - toExtract;
                if (remaining <= 0) {
                    ammoMap.remove(msg.ammoType);
                } else {
                    ammoMap.put(msg.ammoType, remaining);
                }
            }
            net.setDirty();

            Ammo ammo = findAmmoBySerialization(msg.ammoType);
            if (ammo != null) {
                long giveCount = toExtract;
                while (giveCount > 0) {
                    int stackSize = (int) Math.min(giveCount, ammo.getItem().getMaxStackSize());
                    ItemStack ammoStack = new ItemStack(ammo.getItem(), stackSize);
                    if (!player.getInventory().add(ammoStack)) {
                        player.drop(ammoStack, false);
                    }
                    giveCount -= stackSize;
                }
            }

            Map<String, Long> fullMap = new HashMap<>(ammoMap);
            if (fullMap.containsKey("__infinite__")) {
                fullMap.put("__infinite__", Long.MAX_VALUE);
            }
            long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
            String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
            PacketHandler.sendToPlayer(player,
                SuperbAmmoStatusResponsePacket.fromNet(net, fullMap, energy, 0, netName));
        });
        ctx.get().setPacketHandled(true);
    }

    private static Ammo findAmmoBySerialization(String name) {
        for (Ammo ammo : Ammo.values()) {
            if (ammo.serializationName.equals(name)) return ammo;
        }
        return null;
    }
}
