package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.crafting.TaczCraftManager;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TaczCraftPacket {

    private final ResourceLocation recipeId;
    private final int count;
    private final boolean toNetwork;

    public TaczCraftPacket(ResourceLocation recipeId) {
        this(recipeId, 1, false);
    }

    public TaczCraftPacket(ResourceLocation recipeId, int count) {
        this(recipeId, count, false);
    }

    public TaczCraftPacket(ResourceLocation recipeId, int count, boolean toNetwork) {
        this.recipeId = recipeId;
        this.count = count;
        this.toNetwork = toNetwork;
    }

    ResourceLocation getRecipeId() { return recipeId; }
    int getCount() { return count; }
    boolean isToNetwork() { return toNetwork; }

    public static void encode(TaczCraftPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.recipeId);
        buf.writeVarInt(msg.count);
        buf.writeBoolean(msg.toNetwork);
    }

    public static TaczCraftPacket decode(FriendlyByteBuf buf) {
        return new TaczCraftPacket(buf.readResourceLocation(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(TaczCraftPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) {
                player.sendSystemMessage(Component.translatable("message.beyond_integration.no_network"));
                return;
            }

            TaczCraftManager.get().executeCraft(player, net, msg.recipeId, msg.count, msg.toNetwork);
        });
        ctx.get().setPacketHandled(true);
    }
}
