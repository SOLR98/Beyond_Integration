package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.ManaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 网络魔力同步包（S2C）：玩家主网络魔力 + 上限 + 是否有源，供 HUD 显示（法条叠加 / 独立 HUD）。 */
public class ManaSyncPacket {

    private final long amount;
    private final long cap;
    private final boolean source;

    public ManaSyncPacket(long amount, long cap, boolean source) {
        this.amount = amount;
        this.cap = cap;
        this.source = source;
    }

    public static void encode(ManaSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarLong(msg.amount);
        buf.writeVarLong(msg.cap);
        buf.writeBoolean(msg.source);
    }

    public static ManaSyncPacket decode(FriendlyByteBuf buf) {
        return new ManaSyncPacket(buf.readVarLong(), buf.readVarLong(), buf.readBoolean());
    }

    public static void handle(ManaSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ManaState.set(msg.amount, msg.cap, msg.source)));
        ctx.get().setPacketHandled(true);
    }
}
