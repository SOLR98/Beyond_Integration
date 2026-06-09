package com.solr98.beyondintegration.network;

import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class RequestNetworkItemsPacket {

    static final Map<String, List<TaczIngredient>> TACZ_INDEX = new HashMap<>();
    static boolean INDEX_BUILT = false;

    record TaczIngredient(ResourceLocation recipeId, int idx, Ingredient ingredient, boolean hasNbt) {}

    public static void ensureIndex(ServerPlayer player) {
        if (!INDEX_BUILT) buildIndex(player);
    }

    public static void encode(RequestNetworkItemsPacket msg, FriendlyByteBuf buf) {}

    public static RequestNetworkItemsPacket decode(FriendlyByteBuf buf) {
        return new RequestNetworkItemsPacket();
    }

    public static void handle(RequestNetworkItemsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) {
                PacketHandler.sendToPlayer(player, new NetworkItemCountsPacket(new HashMap<>(), true, false, -1));
                return;
            }

            if (!INDEX_BUILT) buildIndex(player);

            Map<String, Long> counts = scanNetworkItems(net);

            PacketHandler.sendToPlayer(player, new NetworkItemCountsPacket(counts, true, true, net.getId()));
        });
        ctx.get().setPacketHandled(true);
    }

    public static Map<String, Long> scanNetworkItems(DimensionsNet net) {
        Map<String, Long> counts = new HashMap<>();
        net.getUnifiedStorage().getBucket(ItemStackKey.ID).ifPresent(bucket -> {
            for (int i = 0; i < bucket.size(); i++) {
                IStackKey<?> rawKey = bucket.get(i);
                if (!(rawKey instanceof ItemStackKey ik)) continue;
                long amount = net.getUnifiedStorage().getStackByKey(ik).amount();
                if (amount <= 0) continue;
                ItemStack stored = ik.getReadOnlyStack();
                if (stored.isEmpty()) continue;
                List<TaczIngredient> related = TACZ_INDEX.get(stored.getItem().toString());
                if (related != null) {
                    for (var ti : related) {
                        if (ti.ingredient().test(stored)) {
                            counts.merge(ti.recipeId() + "|" + ti.idx(), amount, Long::sum);
                        }
                    }
                }
            }
        });
        return counts;
    }

    private static void buildIndex(ServerPlayer player) {
        for (var recipe : player.getServer().getRecipeManager().getRecipes()) {
            if (!(recipe instanceof GunSmithTableRecipe taczRecipe)) continue;
            ResourceLocation rid = taczRecipe.getId();
            List<GunSmithTableIngredient> inputs = taczRecipe.getInputs();
            if (inputs == null) continue;
            for (int idx = 0; idx < inputs.size(); idx++) {
                GunSmithTableIngredient gi = inputs.get(idx);
                if (gi == null) continue;
                Ingredient ing = gi.getIngredient();
                if (ing == null || ing.isEmpty()) continue;
                boolean hasNbt = false;
                for (ItemStack m : ing.getItems()) {
                    if (!m.isEmpty() && m.hasTag() && !m.getTag().isEmpty()) {
                        hasNbt = true;
                        break;
                    }
                }
                for (ItemStack m : ing.getItems()) {
                    if (m.isEmpty()) continue;
                    String id = m.getItem().toString();
                    TACZ_INDEX.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(new TaczIngredient(rid, idx, ing, hasNbt));
                }
            }
        }
        INDEX_BUILT = true;
    }
}
