package com.solr98.beyondintegration.feature.ftb;

import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB 奖励领取（1.21.1）：复刻 {@code ClaimAllRewardsMessage.handle} 的遍历逻辑，
 * 在"领取进网络"标记生效期间逐个领取玩家已完成任务的未领取奖励。
 * <p>
 * 本类引用 FTB 类，仅在 ftbquests 已加载时由数据包处理调用（异常由调用方兜底）。
 */
public final class FtbRewardClaimer {

    private FtbRewardClaimer() {}

    /** 领取玩家所有未领取奖励（excludeFromClaimAll 的奖励除外） */
    public static void claimAll(ServerPlayer player) {
        if (player == null) return;
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) return;
        file.getTeamData(player).ifPresent(data -> file.forAllQuests(quest -> {
            if (!data.isCompleted(quest)) return;
            for (Reward reward : quest.getRewards()) {
                if (reward.getExcludeFromClaimAll()) continue;
                data.claimReward(player, reward, true);
            }
        }));
    }
}
