package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.enchant.EnchantMergeService;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：请求「附魔合并」页的候选（携带网络里的那件装备）。
 * <p>服务端按当前打开的网络菜单（回退主网络）扫描网络里的单附魔书，回推
 * {@link EnchantMergeStatePacket}（候选 + XP 余额）。页面只在客户端做展示与选择。
 */
public record RequestEnchantMergePacket(ItemStack equipment) implements CustomPacketPayload {

    public static final Type<RequestEnchantMergePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "request_enchant_merge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestEnchantMergePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestEnchantMergePacket decode(RegistryFriendlyByteBuf buf) {
                    return new RequestEnchantMergePacket(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, RequestEnchantMergePacket msg) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, msg.equipment());
                }
            };

    /** 构造：只保留一件作为模板。 */
    public static RequestEnchantMergePacket of(ItemStack stack) {
        return new RequestEnchantMergePacket(stack == null || stack.isEmpty()
                ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    public static void handle(final RequestEnchantMergePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (packet.equipment().isEmpty()) return;
            DimensionsNet net = NetMenuResolver.resolve(player);
            if (net == null) return;
            EnchantMergeStatePacket.sendTo(player, net, packet.equipment());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
