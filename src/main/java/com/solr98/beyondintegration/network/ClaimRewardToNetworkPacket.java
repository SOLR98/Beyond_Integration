package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.ftb.FtbRewardSelectionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 单条奖励领取到网络（C2S）：客户端在任务/奖励界面 Shift+点击奖励按钮时发送，
 * 服务端校验可领取后标记"领取进网络"并领取该奖励（物品奖励入网，其他奖励原版）。
 */
public record ClaimRewardToNetworkPacket(long rewardId) implements CustomPacketPayload {

    public static final Type<ClaimRewardToNetworkPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":claim_reward_to_network"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimRewardToNetworkPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, ClaimRewardToNetworkPacket::rewardId,
                    ClaimRewardToNetworkPacket::new);

    public static void handle(final ClaimRewardToNetworkPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!ModList.get().isLoaded("ftbquests")) return;
            FtbRewardSelectionService.claimSingle(player, packet.rewardId(), true);
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
