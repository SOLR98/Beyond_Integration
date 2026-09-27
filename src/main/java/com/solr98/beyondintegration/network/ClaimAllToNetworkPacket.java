package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * "领取所有奖励到网络"请求包（C2S，空载荷）：
 * 客户端在任务界面 Shift+点击"领取所有"时发送；服务端在"领取进网络"标记生效期间
 * 逐个领取奖励，物品奖励由 {@code FtbItemRewardMixin} 直接插入主网络
 * （其他奖励类型保持原版、无网络时回退原版）。
 */
public record ClaimAllToNetworkPacket() implements CustomPacketPayload {
    public static final Type<ClaimAllToNetworkPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":claim_all_to_network"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimAllToNetworkPacket> STREAM_CODEC =
            StreamCodec.unit(new ClaimAllToNetworkPacket());

    public static void handle(final ClaimAllToNetworkPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!ModList.get().isLoaded("ftbquests")) return;
            if (!FtbIntegrationHelper.isEnabled()) return;
            FtbIntegrationHelper.beginNetworkClaim(player);
            boolean simplify = com.solr98.beyondintegration.CommandConfig.ftbSimplifyRewardNotify();
            if (simplify) FtbIntegrationHelper.beginBatchNotify(player);
            try {
                com.solr98.beyondintegration.feature.ftb.FtbRewardClaimer.claimAll(player);
            } catch (Throwable ignored) {
            } finally {
                FtbIntegrationHelper.endNetworkClaim(player);
                if (simplify) FtbIntegrationHelper.sendBatchNotifySummary(player);
            }
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
