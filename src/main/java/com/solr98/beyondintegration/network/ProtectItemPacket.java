package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.handler.ItemTooltipHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ProtectItemPacket(int slotIndex) implements CustomPacketPayload {
    public static final Type<ProtectItemPacket> TYPE = new Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":protect_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProtectItemPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeInt(p.slotIndex),
            buf -> new ProtectItemPacket(buf.readInt())
    );

    public static void handle(final ProtectItemPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack stack;
            if (packet.slotIndex >= 0) {
                stack = player.getInventory().getItem(packet.slotIndex);
            } else {
                stack = player.getMainHandItem();
            }
            if (stack.isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.beyond_integration.protect.no_item"));
                return;
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
                player.sendSystemMessage(Component.translatable("message.beyond_integration.protect.removed", stack.getDisplayName()));
            } else {
                CompoundTag tag = stack.has(DataComponents.CUSTOM_DATA)
                        ? stack.get(DataComponents.CUSTOM_DATA).copyTag()
                        : new CompoundTag();
                tag.putBoolean(ItemTooltipHandler.PROTECT_TAG, true);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                player.sendSystemMessage(Component.translatable("message.beyond_integration.protect.added", stack.getDisplayName()));
            }
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
