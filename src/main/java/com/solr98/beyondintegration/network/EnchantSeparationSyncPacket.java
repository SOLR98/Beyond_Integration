package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.EnchantSeparationState;
import com.solr98.beyondintegration.client.ClientMenuNet;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 附魔分离开关同步包（S2C）：服务端将指定网络的附魔分离启用状态同步给客户端，
 * 客户端写入独立状态 EnchantSeparationState（不依赖 SW 弹药快照，仅 BD+BI 时也生效）。
 * 由切换应答（ToggleEnchantSeparationPacket）与状态请求应答（RequestEnchantSeparationPacket）触发。
 */
public record EnchantSeparationSyncPacket(int netId, boolean enabled) implements CustomPacketPayload {

    public static final Type<EnchantSeparationSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "enchant_separation_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantSeparationSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EnchantSeparationSyncPacket::netId,
                    ByteBufCodecs.BOOL, EnchantSeparationSyncPacket::enabled,
                    EnchantSeparationSyncPacket::new);

    public static void handle(final EnchantSeparationSyncPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!context.flow().isClientbound()) return;
            if (packet.netId() >= 0) {
                EnchantSeparationState.INSTANCE.set(packet.netId(), packet.enabled());
            }
            int local = ClientMenuNet.netId();
            if (local >= 0 && local == packet.netId()) {
                SuperbAmmoCache.INSTANCE.setEnchantSeparation(packet.enabled());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
