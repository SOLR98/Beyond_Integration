package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.SoulBeamClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 灵魂源信标光柱包（S2C）：要求客户端在指定位置用原版 {@code BeaconRenderer.renderBeaconBeam} 渲染一道光柱。
 */
public class SoulBeamPacket {

    private final BlockPos start;
    private final int height;
    private final int durationTicks;
    private final float r;
    private final float g;
    private final float b;
    private final float beamRadius;
    private final float glowRadius;

    public SoulBeamPacket(BlockPos start, int height, int durationTicks,
                          float r, float g, float b, float beamRadius, float glowRadius) {
        this.start = start;
        this.height = height;
        this.durationTicks = durationTicks;
        this.r = r;
        this.g = g;
        this.b = b;
        this.beamRadius = beamRadius;
        this.glowRadius = glowRadius;
    }

    public static void encode(SoulBeamPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.start);
        buf.writeVarInt(msg.height);
        buf.writeVarInt(msg.durationTicks);
        buf.writeFloat(msg.r);
        buf.writeFloat(msg.g);
        buf.writeFloat(msg.b);
        buf.writeFloat(msg.beamRadius);
        buf.writeFloat(msg.glowRadius);
    }

    public static SoulBeamPacket decode(FriendlyByteBuf buf) {
        return new SoulBeamPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(SoulBeamPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SoulBeamClient.add(msg.start, msg.height, msg.durationTicks,
                        msg.r, msg.g, msg.b, msg.beamRadius, msg.glowRadius)));
        ctx.get().setPacketHandled(true);
    }
}
