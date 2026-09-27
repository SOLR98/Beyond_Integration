package com.solr98.beyondintegration.feature.ftb;

import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * FTB 队伍助手：取玩家所属队伍 ID（自动检测按队伍去重，进度由 TeamData 按队伍共享）。
 * <p>
 * 经 FTB Quests 的 TeamData 获取（teamId 即 FTB Teams 队伍 ID），无需额外编译依赖；
 * 异常回退玩家自身 UUID。
 */
public final class FtbTeamHelper {

    private static final UUID NIL = new UUID(0L, 0L);

    private FtbTeamHelper() {}

    /** 玩家所属队伍 ID（无队伍/异常回退玩家自身 UUID） */
    public static UUID teamKey(ServerPlayer player) {
        if (player == null) return NIL;
        try {
            TeamData data = TeamData.get(player);
            if (data != null) return data.getTeamId();
        } catch (Throwable ignored) {}
        return player.getUUID();
    }
}
