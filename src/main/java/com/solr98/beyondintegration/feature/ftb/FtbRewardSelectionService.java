package com.solr98.beyondintegration.feature.ftb;

import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * FTB 奖励领取服务（服务端）：
 * <ul>
 *   <li>单条奖励领取进网络：Shift+点击奖励按钮时按奖励 ID 领取（标记网络领取，物品奖励入网）；</li>
 *   <li>批量按选择领取：奖励选择界面确认后逐条按"网络/背包"去向领取。</li>
 * </ul>
 * 每条奖励领取前校验 {@code getClaimType(...).canClaim()}；网络标记仅在该条领取期间生效
 * （由 {@code FtbItemRewardMixin} 接管物品奖励方向），非物品奖励标记无效果、保持原版发放。
 * <p>
 * 本类引用 FTB 类，仅在 ftbquests 已加载且集成启用时由数据包调用。
 */
public final class FtbRewardSelectionService {

    /** 单次批量领取的奖励数量上限 */
    public static final int MAX_ENTRIES = 128;

    private FtbRewardSelectionService() {}

    /** 单条奖励领取（toNetwork 时物品奖励入网）；返回是否成功领取 */
    public static boolean claimSingle(ServerPlayer player, long rewardId, boolean toNetwork) {
        if (player == null) return false;
        if (!FtbIntegrationHelper.isEnabled()) return false;
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) return false;
        TeamData data = file.getTeamData(player).orElse(null);
        if (data == null) return false;
        Reward reward = file.getReward(rewardId);
        if (reward == null) return false;
        if (!data.getClaimType(player.getUUID(), reward).canClaim()) return false;
        return claimOne(player, data, reward, toNetwork);
    }

    /**
     * 批量领取：按 rewardIds 顺序逐条校验并领取，toNetwork 标志对应各条去向。
     * 返回成功领取条数；-1 表示集成未启用/文件或队伍数据不可用。
     */
    public static int claimSelection(ServerPlayer player, List<Long> rewardIds, List<Boolean> toNetwork) {
        if (player == null || rewardIds == null || rewardIds.isEmpty()) return 0;
        if (!FtbIntegrationHelper.isEnabled()) return -1;
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) return -1;
        TeamData data = file.getTeamData(player).orElse(null);
        if (data == null) return -1;
        int claimed = 0;
        int limit = Math.min(rewardIds.size(), MAX_ENTRIES);
        for (int i = 0; i < limit; i++) {
            Reward reward = file.getReward(rewardIds.get(i));
            if (reward == null) continue;
            if (!data.getClaimType(player.getUUID(), reward).canClaim()) continue;
            boolean toNet = toNetwork != null && i < toNetwork.size() && Boolean.TRUE.equals(toNetwork.get(i));
            if (claimOne(player, data, reward, toNet)) claimed++;
        }
        return claimed;
    }

    /** 领取单条奖励：网络去向时仅在本次领取期间标记"领取进网络" */
    private static boolean claimOne(ServerPlayer player, TeamData data, Reward reward, boolean toNetwork) {
        if (toNetwork) FtbIntegrationHelper.beginNetworkClaim(player);
        try {
            data.claimReward(player, reward, true);
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (toNetwork) FtbIntegrationHelper.endNetworkClaim(player);
        }
    }
}
