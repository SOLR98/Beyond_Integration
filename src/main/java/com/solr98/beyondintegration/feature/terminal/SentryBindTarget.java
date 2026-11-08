package com.solr98.beyondintegration.feature.terminal;

import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;

import java.lang.reflect.Method;

/**
 * 方块绑定目标（左键）：手持终端的玩家左键哨戒机械臂底座 →
 * 绑定网络（写入 {@link SentryNetIdAccessor}）并装填弹药箱。等价迁移自原
 * {@code BeyondIntegration.onLeftClickBlock} 的哨戒炮分支。
 */
public final class SentryBindTarget implements TerminalBindTarget {

    private static final String SENTRY_NAMESPACE = "sentrymechanicalarm";

    @Override
    public boolean supports(TerminalBindContext ctx) {
        if (ctx.pos == null) return false;
        Block block = ctx.level.getBlockState(ctx.pos).getBlock();
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        return id != null && SENTRY_NAMESPACE.equals(id.getNamespace());
    }

    @Override
    public boolean bind(TerminalBindContext ctx) {
        if (ctx.clientSide) return true; // 客户端仅用于取消挖掘
        ServerPlayer sp = ctx.serverPlayer();
        if (sp == null) return true;

        BlockEntity be = ctx.level.getBlockEntity(ctx.pos);
        if (be == null) return true;

        if (be instanceof SentryNetIdAccessor accessor) {
            accessor.setSentryNetId(ctx.netId);
        }
        try {
            Method getHeld = be.getClass().getMethod("getHeldItem");
            ItemStack held = (ItemStack) getHeld.invoke(be);
            if (held.isEmpty()) {
                sp.displayClientMessage(
                        Component.translatable("message.beyond_integration.sentry_no_gun"), true);
                return true;
            }
            Method addBox = be.getClass().getMethod("addAmmoBox", ItemStack.class);
            boolean ok = (boolean) addBox.invoke(be, ctx.terminal);
            if (ok) {
                if (!sp.isCreative()) ctx.terminal.shrink(1);
                sp.displayClientMessage(
                        Component.translatable("message.beyond_integration.sentry_net_bound", ctx.netId), true);
            } else {
                sp.displayClientMessage(
                        Component.translatable("sentry.tooltip.ammobox_1"), true);
            }
        } catch (Exception ignored) {
        }
        return true;
    }
}
