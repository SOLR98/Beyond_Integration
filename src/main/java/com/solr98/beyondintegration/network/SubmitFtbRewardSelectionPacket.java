package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import com.solr98.beyondintegration.feature.ftb.FtbRewardSelectionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 奖励选择界面提交（C2S）：携带奖励 ID 列表与各条去向（true = 进网络），
 * 服务端逐条校验并领取；物品奖励按去向入网/入背包，其他奖励保持原版发放。
 */
public record SubmitFtbRewardSelectionPacket(List<Long> rewardIds, List<Boolean> toNetwork)
        implements CustomPacketPayload {

    public static final Type<SubmitFtbRewardSelectionPacket> TYPE = new Type<>(
            ResourceLocation.parse(BeyondIntegration.MODID + ":submit_ftb_reward_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitFtbRewardSelectionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(FtbRewardSelectionService.MAX_ENTRIES)),
                    SubmitFtbRewardSelectionPacket::rewardIds,
                    ByteBufCodecs.BOOL.apply(ByteBufCodecs.list(FtbRewardSelectionService.MAX_ENTRIES)),
                    SubmitFtbRewardSelectionPacket::toNetwork,
                    SubmitFtbRewardSelectionPacket::new);

    public static void handle(final SubmitFtbRewardSelectionPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!ModList.get().isLoaded("ftbquests")) return;
            boolean simplify = com.solr98.beyondintegration.CommandConfig.ftbSimplifyRewardNotify();
            if (simplify) FtbIntegrationHelper.beginBatchNotify(player);
            try {
                FtbRewardSelectionService.claimSelection(player, packet.rewardIds(), packet.toNetwork());
            } finally {
                if (simplify) FtbIntegrationHelper.sendBatchNotifySummary(player);
            }
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
