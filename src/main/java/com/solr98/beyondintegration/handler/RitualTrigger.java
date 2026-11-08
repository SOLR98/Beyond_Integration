package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.common.init.BDBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * 仪式「网络触发条件」判定：祭坛半径内的维度网络通道 / 维度连接方块数量。
 * <p>
 * 这是本模组所有仪式网络功能（环境绕过、结构方块由网络提供）的<b>统一前置条件</b>，
 * 两处必须用同一份判定，避免行为不一致。
 * <p><b>性能</b>：单次判定要扫 {@code (2*radius+1)^3} 个方块（默认半径 8 → 17³ = 4913），
 * 而仪式 tick 会每 tick 询问，故做<b>短时缓存</b>（同一 level+位置在 {@link #TTL_TICKS}
 * 个游戏 tick 内只算一次；放置/拆掉通道方块的生效延迟最多 0.5 秒）。
 */
public final class RitualTrigger {

    /** 判定结果缓存时长（游戏 tick；10 tick = 0.5 秒）。 */
    private static final int TTL_TICKS = 10;

    /** 缓存字段：仅服务端主线程使用，无并发问题。 */
    private static Level cachedLevel;
    private static BlockPos cachedPos;
    private static long cachedTick = Long.MIN_VALUE;
    private static boolean cachedResult;

    private RitualTrigger() {
    }

    /** 前置条件是否满足（未配置任何触发要求时返回 false = 不生效）。 */
    public static boolean met(Level level, BlockPos altarPos) {
        if (level == null || altarPos == null) return false;

        long tick = level.getGameTime();
        if (level == cachedLevel && altarPos.equals(cachedPos)
                && cachedTick != Long.MIN_VALUE && tick - cachedTick <= TTL_TICKS) {
            return cachedResult;
        }

        boolean result = compute(level, altarPos);
        cachedLevel = level;
        cachedPos = altarPos.immutable();
        cachedTick = tick;
        cachedResult = result;
        return result;
    }

    /** 清空缓存（服务器停止 / 换图时调用）。 */
    public static void clear() {
        cachedLevel = null;
        cachedPos = null;
        cachedTick = Long.MIN_VALUE;
        cachedResult = false;
    }

    private static boolean compute(Level level, BlockPos altarPos) {
        int pathways = CommandConfig.ritualEnvPathwayCount();
        int connects = CommandConfig.ritualEnvConnectCount();
        if (pathways <= 0 && connects <= 0) return false; // 未配置触发 → 不生效
        int radius = CommandConfig.ritualEnvRadius();
        if (pathways > 0 && countWithin(level, altarPos, radius, BDBlocks.NET_PATHWAY.get()) < pathways) {
            return false;
        }
        if (connects > 0
                && countWithin(level, altarPos, radius, BDBlocks.DIMENSIONAL_CONNECT_BLOCK.get()) < connects) {
            return false;
        }
        return true;
    }

    /** 半径内某方块的数量（一次遍历同时数两种方块，少扫一遍）。 */
    public static int countWithin(Level level, BlockPos center, int radius, Block block) {
        int count = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (level.getBlockState(p).is(block)) count++;
        }
        return count;
    }
}
