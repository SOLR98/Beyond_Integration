package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.WorkstationActivationCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作台状态同步包（S2C）：
 * 下发献祭激活开关、当前网络已激活的工作台 ID 列表，以及服务端启用的工作台列表
 * （配置权威值，供客户端工作站按钮的显示/隐藏与锁定状态判断）。
 */
public record WorkstationActivationSyncPayload(boolean enabled, List<String> activated,
                                               List<String> enabledWorkstations) implements CustomPacketPayload {
    public static final Type<WorkstationActivationSyncPayload> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":workstation_activation_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkstationActivationSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, WorkstationActivationSyncPayload::enabled,
                    ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), WorkstationActivationSyncPayload::activated,
                    ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), WorkstationActivationSyncPayload::enabledWorkstations,
                    WorkstationActivationSyncPayload::new);

    public static void handle(final WorkstationActivationSyncPayload packet, final IPayloadContext context) {
        context.enqueueWork(() -> WorkstationActivationCache.update(
                packet.enabled(), packet.activated(), packet.enabledWorkstations()));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
