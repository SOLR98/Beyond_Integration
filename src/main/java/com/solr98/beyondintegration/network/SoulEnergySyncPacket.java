package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.SoulEnergyState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 网络灵魂量同步包（S2C）：玩家主网络灵魂量 + 是否有源，供 HUD 叠加"网络魂"段（方案 B）。 */
public class SoulEnergySyncPacket {

    private final int amount;
    private final boolean source;

    public SoulEnergySyncPacket(int amount, boolean source) {
        this.amount = amount;
        this.source = source;
    }

    public static void encode(SoulEnergySyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.amount);
        buf.writeBoolean(msg.source);
    }

    public static SoulEnergySyncPacket decode(FriendlyByteBuf buf) {
        return new SoulEnergySyncPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(SoulEnergySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SoulEnergyState.set(msg.amount, msg.source)));
        ctx.get().setPacketHandled(true);
    }
}
