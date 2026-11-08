package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.crafting.TaczCraftManager;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * TACZ 枪械台批量合成请求包（客户端 → 服务端）。
 * <p>指定配方 ID、合成次数与产物去向（{@code toNetwork=true} 放入网络，否则丢到脚下），
 * 服务端委托 {@link TaczCraftManager} 执行（材料匹配/预检/扣料/产物/回传计数）。
 * <p>TYPE: {@code beyond_integration:tacz_craft}；STREAM_CODEC 读写 recipeId/count/toNetwork。
 */
public record TaczCraftPacket(ResourceLocation recipeId, int count, boolean toNetwork) implements CustomPacketPayload {

    public static final Type<TaczCraftPacket> TYPE = new Type<>(ResourceLocation.parse("beyond_integration:tacz_craft"));

    public static final StreamCodec<FriendlyByteBuf, TaczCraftPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull TaczCraftPacket decode(FriendlyByteBuf buf) {
            return new TaczCraftPacket(buf.readResourceLocation(), buf.readVarInt(), buf.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buf, TaczCraftPacket packet) {
            buf.writeResourceLocation(packet.recipeId);
            buf.writeVarInt(packet.count);
            buf.writeBoolean(packet.toNetwork);
        }
    };

    public static void handle(final TaczCraftPacket packet, final IPayloadContext context) {
        // 服务端处理：校验网络后交由 TaczCraftManager 执行（逻辑集中在 feature/crafting）
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.level() == null) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) {
                player.sendSystemMessage(Component.translatable("message.beyond_integration.no_network"));
                return;
            }
            TaczCraftManager.get().executeCraft(player, net, packet.recipeId, packet.count, packet.toNetwork);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
