package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ftb.FtbRewardSelectionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 单条奖励领取到网络（C2S）：客户端在任务/奖励界面 Shift+点击奖励按钮时发送，
 * 服务端校验可领取后标记"领取进网络"并领取该奖励（物品奖励入网，其他奖励原版）。
 */
public record ClaimRewardToNetworkPacket(long rewardId) {

    public static void encode(ClaimRewardToNetworkPacket msg, FriendlyByteBuf buf) {
        buf.writeVarLong(msg.rewardId);
    }

    public static ClaimRewardToNetworkPacket decode(FriendlyByteBuf buf) {
        return new ClaimRewardToNetworkPacket(buf.readVarLong());
    }

    public static void handle(ClaimRewardToNetworkPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!ModList.get().isLoaded("ftbquests")) return;
            FtbRewardSelectionService.claimSingle(player, msg.rewardId, true);
        });
        ctx.get().setPacketHandled(true);
    }
}
