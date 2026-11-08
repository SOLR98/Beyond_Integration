package com.solr98.beyondintegration.client;

import com.wintercogs.beyonddimensions.common.block.NetedBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 维度网络方块“复制配置”提示：当玩家瞄准某个维度网络方块、且手持其对应物品时，
 * 在快捷栏上方显示“右键复制配置”的提示。
 */
public final class NetHintHelper {

    private NetHintHelper() {}

    public static void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();

        // 优先显示临时提示（如网络图腾消耗），与“复制配置”提示同一位置
        Component hint = HudHintState.current();
        if (hint != null) {
            guiGraphics.drawCenteredString(mc.font, hint, screenWidth / 2, screenHeight - 72, 0xFFFFFF);
            return;
        }

        if (mc.player == null || mc.level == null || mc.hitResult == null) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof NetedBlock)) return;

        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty() || held.getItem() != state.getBlock().asItem()) return;

        Component text = Component.translatable("hud.beyond_integration.net_copy_config");
        guiGraphics.drawCenteredString(mc.font, text, screenWidth / 2, screenHeight - 72, 0xFFFFFF);
    }
}
