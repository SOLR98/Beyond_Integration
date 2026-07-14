package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SuperbAmmoStatusResponsePacket {

    private final int netId;
    private final Map<String, Long> ammo;
    private final long energy;
    private final int type;
    private final String name;
    private final boolean enchantSeparation;

    public SuperbAmmoStatusResponsePacket(int netId, Map<String, Long> ammo, long energy) {
        this(netId, ammo, energy, 0, "", true);
    }

    public SuperbAmmoStatusResponsePacket(int netId, Map<String, Long> ammo, long energy, int type) {
        this(netId, ammo, energy, type, "", true);
    }

    public SuperbAmmoStatusResponsePacket(int netId, Map<String, Long> ammo, long energy, int type, String name, boolean enchantSeparation) {
        this.netId = netId;
        this.ammo = ammo;
        this.energy = energy;
        this.type = type;
        this.name = name;
        this.enchantSeparation = enchantSeparation;
    }

    public static SuperbAmmoStatusResponsePacket fromNet(DimensionsNet net, Map<String, Long> ammo, long energy, int type, String name) {
        boolean enchantSep = net instanceof EnchantSeparationAccessor ea && ea.beyond$isEnchantSeparationEnabled();
        return new SuperbAmmoStatusResponsePacket(net.getId(), ammo, energy, type, name, enchantSep);
    }

    public static void encode(SuperbAmmoStatusResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.netId);
        buf.writeVarInt(msg.type);
        buf.writeUtf(msg.name);
        buf.writeVarLong(msg.energy);
        buf.writeBoolean(msg.enchantSeparation);
        buf.writeVarInt(msg.ammo.size());
        for (var entry : msg.ammo.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarLong(entry.getValue());
        }
    }

    public static SuperbAmmoStatusResponsePacket decode(FriendlyByteBuf buf) {
        int netId = buf.readVarInt();
        int type = buf.readVarInt();
        String name = buf.readUtf();
        long energy = buf.readVarLong();
        boolean enchantSep = buf.readBoolean();
        int size = buf.readVarInt();
        Map<String, Long> ammo = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ammo.put(buf.readUtf(), buf.readVarLong());
        }
        return new SuperbAmmoStatusResponsePacket(netId, ammo, energy, type, name, enchantSep);
    }

    public static void handle(SuperbAmmoStatusResponsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                if (msg.type == 1) {
                    SuperbAmmoCache.updateVehicle(msg.netId, msg.ammo, msg.energy, msg.name);
                } else {
                    SuperbAmmoCache.update(msg.netId, msg.ammo, msg.energy, msg.name);
                }
                SuperbAmmoCache.setEnchantSeparation(msg.enchantSeparation);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
