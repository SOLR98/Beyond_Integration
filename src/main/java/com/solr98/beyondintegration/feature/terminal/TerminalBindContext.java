package com.solr98.beyondintegration.feature.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 终端绑定上下文：一次"手持已接入网络的终端物品 → 对方块/实体绑定网络"的调用参数。
 */
public final class TerminalBindContext {

    /** 交互玩家（双端）。 */
    public final Player player;
    public final Level level;
    /** 手持的终端 / 网络物品。 */
    public final ItemStack terminal;
    /** 手持物携带的 BD 网络 ID（>=0）。 */
    public final int netId;
    public final boolean clientSide;
    /** 方块目标（方块绑定时非空）。 */
    public final @Nullable BlockPos pos;
    /** 实体目标（实体绑定时非空）。 */
    public final @Nullable Entity entity;

    public TerminalBindContext(Player player, Level level, ItemStack terminal, int netId,
                               @Nullable BlockPos pos, @Nullable Entity entity) {
        this.player = player;
        this.level = level;
        this.terminal = terminal;
        this.netId = netId;
        this.clientSide = level.isClientSide();
        this.pos = pos;
        this.entity = entity;
    }

    public @Nullable ServerPlayer serverPlayer() {
        return player instanceof ServerPlayer sp ? sp : null;
    }
}
