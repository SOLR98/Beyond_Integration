package com.solr98.beyondintegration.network;
import com.solr98.beyondintegration.feature.crafting.TaczCraftManager;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;

public record TaczCraftPacket(ResourceLocation recipeId, int count, boolean toNetwork) implements CustomPacketPayload {
    public static final Type<TaczCraftPacket> TYPE = new Type<>(ResourceLocation.parse("beyond_integration:tacz_craft"));
    public static final StreamCodec<FriendlyByteBuf, TaczCraftPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public @NotNull TaczCraftPacket decode(FriendlyByteBuf buf) {
            return new TaczCraftPacket(buf.readResourceLocation(), buf.readVarInt(), buf.readBoolean());
        }
        @Override public void encode(FriendlyByteBuf buf, TaczCraftPacket p) {
            buf.writeResourceLocation(p.recipeId); buf.writeVarInt(p.count); buf.writeBoolean(p.toNetwork);
        }
    };

    public static void handle(final TaczCraftPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer sp)) return;
            if (sp.level() == null) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
            if (net == null) {
                sp.sendSystemMessage(Component.translatable("message.beyond_integration.no_network"));
                return;
            }

            var recipeOpt = sp.getServer().getRecipeManager().byKey(packet.recipeId);
            if (recipeOpt.isEmpty()) {
                sp.sendSystemMessage(Component.translatable("message.beyond_integration.recipe_not_found", packet.recipeId.toString()));
                return;
            }
            var holder = recipeOpt.get();
            if (!(holder.value() instanceof GunSmithTableRecipe recipe)) {
                sp.sendSystemMessage(Component.translatable("message.beyond_integration.recipe_type_mismatch"));
                return;
            }

            int crafted = TaczCraftManager.executeCraft(sp, net, recipe, packet.recipeId, packet.count, packet.toNetwork);

            ItemStack resultItem = crafted > 0 ? recipe.getResultItem(sp.level().registryAccess()) : ItemStack.EMPTY;
            PacketHandler.sendToPlayer(sp, new NetworkItemCountsPacket(new HashMap<>(), true, true,
                    net.getId(), net.getCustomName(), resultItem, crafted));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
