package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.enchant.EnchantMergeService;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S：提交「附魔合并」选择（装备 + 每项附魔的目标等级）。
 * <p>服务端重新扫描网络并校验库存/费用（{@link EnchantMergeService#merge}），
 * 执行后回推新的 {@link EnchantMergeStatePacket} 与一条结果消息。
 */
public record SubmitEnchantMergePacket(ItemStack equipment, List<EnchantMergeService.Selection> selections)
        implements CustomPacketPayload {

    public static final Type<SubmitEnchantMergePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "submit_enchant_merge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitEnchantMergePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SubmitEnchantMergePacket decode(RegistryFriendlyByteBuf buf) {
                    ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    int n = buf.readVarInt();
                    List<EnchantMergeService.Selection> list = new ArrayList<>(Math.max(0, n));
                    for (int i = 0; i < n; i++) {
                        list.add(new EnchantMergeService.Selection(buf.readVarInt(), buf.readVarInt()));
                    }
                    return new SubmitEnchantMergePacket(stack, list);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SubmitEnchantMergePacket msg) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, msg.equipment());
                    buf.writeVarInt(msg.selections().size());
                    for (EnchantMergeService.Selection s : msg.selections()) {
                        buf.writeVarInt(s.enchId());
                        buf.writeVarInt(s.level());
                    }
                }
            };

    /** 构造：只保留一件作为模板。 */
    public static SubmitEnchantMergePacket of(ItemStack stack, List<EnchantMergeService.Selection> selections) {
        return new SubmitEnchantMergePacket(stack == null || stack.isEmpty()
                ? ItemStack.EMPTY : stack.copyWithCount(1), selections);
    }

    public static void handle(final SubmitEnchantMergePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (packet.equipment().isEmpty()) return;
            DimensionsNet net = NetMenuResolver.resolve(player);
            if (net == null) return;
            EnchantMergeService.Outcome outcome = EnchantMergeService.merge(
                    net, new ItemStackKey(packet.equipment().copyWithCount(1)), packet.selections());
            if (outcome.messageKey() != null) {
                player.sendSystemMessage(Component.translatable(outcome.messageKey()));
            }
            EnchantMergeStatePacket.sendTo(player, net, packet.equipment());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
