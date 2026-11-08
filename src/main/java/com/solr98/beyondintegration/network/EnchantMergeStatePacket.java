package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.enchant.EnchantMergeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C：「附魔合并」页的状态（候选列表 + 网络 XP 余额）。
 * <p>候选来自服务端 {@link EnchantMergeService#scan}；客户端只读展示，
 * 提交走 {@link SubmitEnchantMergePacket}，服务端会重新扫描并校验，防止客改。
 */
public class EnchantMergeStatePacket {

    private final List<EnchantMergeService.Candidate> candidates;
    private final long xp;

    public EnchantMergeStatePacket(List<EnchantMergeService.Candidate> candidates, long xp) {
        this.candidates = List.copyOf(candidates == null ? List.of() : candidates);
        this.xp = xp;
    }

    /** 服务端按当前网络扫描并下发。 */
    public static void sendTo(ServerPlayer player, com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet net,
                              ItemStack equipment) {
        if (player == null || net == null) return;
        List<EnchantMergeService.Candidate> list = EnchantMergeService.scan(net, equipment);
        long xp = net.getUnifiedStorage()
                .extract(com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler.xpFluidKey(),
                        Long.MAX_VALUE, true, false).amount();
        PacketHandler.sendToPlayer(player, new EnchantMergeStatePacket(list, xp));
    }

    public static void encode(EnchantMergeStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.candidates.size());
        for (EnchantMergeService.Candidate c : msg.candidates) {
            buf.writeVarInt(c.enchId());
            buf.writeVarInt(c.current());
            buf.writeVarInt(c.max());
            buf.writeVarInt((int) Math.min(c.books(), Integer.MAX_VALUE));
            buf.writeVarInt((int) Math.min(c.cost(), Integer.MAX_VALUE));
            buf.writeBoolean(c.assume());
        }
        buf.writeVarInt((int) Math.min(msg.xp, Integer.MAX_VALUE));
    }

    public static EnchantMergeStatePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<EnchantMergeService.Candidate> list = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            list.add(new EnchantMergeService.Candidate(
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }
        long xp = buf.readVarInt();
        return new EnchantMergeStatePacket(list, xp);
    }

    public static void handle(EnchantMergeStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                com.solr98.beyondintegration.client.EnchantMergeClientState.set(msg.candidates, msg.xp);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
