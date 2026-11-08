package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler;

import com.solr98.beyondintegration.feature.enchant.EnchantMergeService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C：「附魔合并」页的状态（候选列表 + 网络 XP 余额）。
 * <p>候选来自服务端 {@link EnchantMergeService#scan}；客户端只读展示，
 * 提交走 {@link SubmitEnchantMergePacket}，服务端会重新扫描并校验，防止客改。
 */
public record EnchantMergeStatePacket(List<EnchantMergeService.Candidate> candidates, long xp)
        implements CustomPacketPayload {

    public static final Type<EnchantMergeStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "enchant_merge_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantMergeStatePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public EnchantMergeStatePacket decode(RegistryFriendlyByteBuf buf) {
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

                @Override
                public void encode(RegistryFriendlyByteBuf buf, EnchantMergeStatePacket msg) {
                    buf.writeVarInt(msg.candidates().size());
                    for (EnchantMergeService.Candidate c : msg.candidates()) {
                        buf.writeVarInt(c.enchId());
                        buf.writeVarInt(c.current());
                        buf.writeVarInt(c.max());
                        buf.writeVarInt((int) Math.min(c.books(), Integer.MAX_VALUE));
                        buf.writeVarInt((int) Math.min(c.cost(), Integer.MAX_VALUE));
                        buf.writeBoolean(c.assume());
                    }
                    buf.writeVarInt((int) Math.min(msg.xp(), Integer.MAX_VALUE));
                }
            };

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

    public static void handle(final EnchantMergeStatePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                com.solr98.beyondintegration.client.EnchantMergeClientState
                        .set(packet.candidates(), packet.xp());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
