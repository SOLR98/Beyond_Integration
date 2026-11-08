package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.ClientMenuNet;
import com.solr98.beyondintegration.client.PotionCharmState;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 网络药水护符状态同步包（S2C）：同步「生效目标 + 经验修补是否已献祭解锁」，供 UI 显示使用 */
public record PotionCharmSyncPacket(int mode, boolean mendingUnlocked) implements CustomPacketPayload {

    public static final Type<PotionCharmSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "potion_charm_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PotionCharmSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PotionCharmSyncPacket::mode,
                    ByteBufCodecs.BOOL, PotionCharmSyncPacket::mendingUnlocked,
                    PotionCharmSyncPacket::new);

    public static void handle(final PotionCharmSyncPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!context.flow().isClientbound()) return;
            int netId = ClientMenuNet.netId();
            if (netId >= 0) PotionCharmState.set(netId, packet.mode(), packet.mendingUnlocked());
            // 药水护符状态由 PotionCharmState 独立维护，不再叠加到 SW 弹药快照
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
