package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "领取所有奖励到网络"请求包（C2S，空载荷）：
 * 客户端在任务界面 Shift+点击"领取所有"时发送；服务端在"领取进网络"标记生效期间
 * 逐个领取奖励，物品奖励由 {@code FtbItemRewardMixin} 直接插入主网络
 * （背包类奖励保持原版、无网络时回退原版）。
 */
public class ClaimAllToNetworkPacket {

    /** 空参构造：该请求包不携带数据 */
    public ClaimAllToNetworkPacket() {}

    /** 无数据可写 */
    public static void encode(ClaimAllToNetworkPacket msg, FriendlyByteBuf buf) {}

    /** 无数据可读，直接还原空包 */
    public static ClaimAllToNetworkPacket decode(FriendlyByteBuf buf) {
        return new ClaimAllToNetworkPacket();
    }

    /** 服务端执行：标记"领取进网络"并领取全部奖励 */
    public static void handle(ClaimAllToNetworkPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
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
        ctx.get().setPacketHandled(true);
    }
}
