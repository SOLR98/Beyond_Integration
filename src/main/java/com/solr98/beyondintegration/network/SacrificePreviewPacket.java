package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.ApothSacrificePreviewState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 献祭预览结果（S2C）：当前四属性 + 献祭后四属性 + 可否献祭。
 * <p>四属性均为 {@code {eterna, quanta, arcana, rectification}}（1.20.1 的 Apotheosis
 * 保留真实的 rectification，不做 stable 转换）；{@code blockKey} 为空串表示可献祭。
 */
public class SacrificePreviewPacket {

    private final int containerId;
    private final boolean ok;
    private final String blockKey;
    private final int[] cur;
    private final int[] next;

    public SacrificePreviewPacket(int containerId, boolean ok, String blockKey, int[] cur, int[] next) {
        this.containerId = containerId;
        this.ok = ok;
        this.blockKey = blockKey;
        this.cur = cur;
        this.next = next;
    }

    public static void encode(SacrificePreviewPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeBoolean(msg.ok);
        buf.writeUtf(msg.blockKey == null ? "" : msg.blockKey);
        for (int i = 0; i < 4; i++) buf.writeVarInt(msg.cur[i]);
        for (int i = 0; i < 4; i++) buf.writeVarInt(msg.next[i]);
    }

    public static SacrificePreviewPacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        boolean ok = buf.readBoolean();
        String blockKey = buf.readUtf();
        int[] cur = new int[]{buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()};
        int[] next = new int[]{buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()};
        return new SacrificePreviewPacket(containerId, ok, blockKey, cur, next);
    }

    public static void handle(SacrificePreviewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                ApothSacrificePreviewState.set(msg.containerId, msg.ok, msg.blockKey, msg.cur, msg.next);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
