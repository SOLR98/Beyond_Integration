package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import com.solr98.beyondintegration.feature.ftb.FtbRewardSelectionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 奖励选择界面提交（C2S）：携带奖励 ID 列表与各条去向（true = 进网络），
 * 服务端逐条校验并领取；物品奖励按去向入网/入背包，其他奖励保持原版发放。
 */
public record SubmitFtbRewardSelectionPacket(List<Long> rewardIds, List<Boolean> toNetwork) {

    public static void encode(SubmitFtbRewardSelectionPacket msg, FriendlyByteBuf buf) {
        int size = Math.min(msg.rewardIds.size(), FtbRewardSelectionService.MAX_ENTRIES);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeVarLong(msg.rewardIds.get(i));
            buf.writeBoolean(i < msg.toNetwork.size() && Boolean.TRUE.equals(msg.toNetwork.get(i)));
        }
    }

    public static SubmitFtbRewardSelectionPacket decode(FriendlyByteBuf buf) {
        int size = Math.max(0, Math.min(buf.readVarInt(), FtbRewardSelectionService.MAX_ENTRIES));
        List<Long> ids = new ArrayList<>(size);
        List<Boolean> flags = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readVarLong());
            flags.add(buf.readBoolean());
        }
        return new SubmitFtbRewardSelectionPacket(ids, flags);
    }

    public static void handle(SubmitFtbRewardSelectionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!ModList.get().isLoaded("ftbquests")) return;
            boolean simplify = CommandConfig.ftbSimplifyRewardNotify();
            if (simplify) FtbIntegrationHelper.beginBatchNotify(player);
            try {
                FtbRewardSelectionService.claimSelection(player, msg.rewardIds, msg.toNetwork);
            } finally {
                if (simplify) FtbIntegrationHelper.sendBatchNotifySummary(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
