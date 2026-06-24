package com.solr98.beyondintegration.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.solr98.beyondintegration.handler.ItemTooltipHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class ProtectCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("protect")
                .executes(ctx -> toggleProtect(ctx.getSource()));
    }

    private static int toggleProtect(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) {
                src.sendFailure(Component.translatable("message.beyond_integration.protect.no_item"));
                return 0;
            }

            boolean isProtected = ItemTooltipHandler.isProtected(stack);

            if (isProtected) {
                CustomData data = stack.get(DataComponents.CUSTOM_DATA);
                if (data != null) {
                    CompoundTag tag = data.copyTag();
                    tag.remove(ItemTooltipHandler.PROTECT_TAG);
                    if (tag.isEmpty()) {
                        stack.remove(DataComponents.CUSTOM_DATA);
                    } else {
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    }
                }
                src.sendSuccess(() -> Component.translatable("message.beyond_integration.protect.removed", stack.getDisplayName()), true);
            } else {
                CompoundTag tag = stack.has(DataComponents.CUSTOM_DATA)
                        ? stack.get(DataComponents.CUSTOM_DATA).copyTag()
                        : new CompoundTag();
                tag.putBoolean(ItemTooltipHandler.PROTECT_TAG, true);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                src.sendSuccess(() -> Component.translatable("message.beyond_integration.protect.added", stack.getDisplayName()), true);
            }
        } catch (Exception e) {
            src.sendFailure(Component.translatable("message.beyond_integration.protect.error", e.getMessage()));
        }
        return 1;
    }
}
