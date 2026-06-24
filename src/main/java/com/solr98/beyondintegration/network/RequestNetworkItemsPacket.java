package com.solr98.beyondintegration.network;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record RequestNetworkItemsPacket() implements CustomPacketPayload {
    public static final Type<RequestNetworkItemsPacket> TYPE = new Type<>(ResourceLocation.parse("beyond_integration:request_network_items"));
    public static final StreamCodec<FriendlyByteBuf, RequestNetworkItemsPacket> STREAM_CODEC = StreamCodec.unit(new RequestNetworkItemsPacket());

    static final Map<String, List<TaczIngredient>> TACZ_INDEX = new HashMap<>();
    static boolean INDEX_BUILT = false;

    record TaczIngredient(ResourceLocation recipeId, int idx, Ingredient ingredient, boolean hasNbt) {}

    public static void handle(RequestNetworkItemsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (!(player instanceof ServerPlayer sp)) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
            if (net == null) {
                PacketHandler.sendToPlayer(sp, new NetworkItemCountsPacket(new HashMap<>(), true, false, -1, ""));
                return;
            }
            if (!INDEX_BUILT) buildIndex(sp);
            PacketHandler.sendToPlayer(sp, new NetworkItemCountsPacket(scanNetworkItems(net), true, true, net.getId(), net.getCustomName()));
        });
    }

    static Map<String, Long> scanNetworkItems(DimensionsNet net) {
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
                        if (ti.ingredient().test(stored))
                            counts.merge(ti.recipeId() + "|" + ti.idx(), amount, Long::sum);
                    }
                }
            }
        });
        return counts;
    }

    static synchronized void buildIndex(ServerPlayer player) {
        if (INDEX_BUILT) return;
        for (var holder : player.getServer().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof GunSmithTableRecipe taczRecipe)) continue;
            ResourceLocation rid = holder.id();
            List<GunSmithTableIngredient> inputs = taczRecipe.getInputs();
            if (inputs == null) continue;
            for (int idx = 0; idx < inputs.size(); idx++) {
                GunSmithTableIngredient gi = inputs.get(idx);
                if (gi == null) continue;
                Ingredient ing = gi.getIngredient();
                if (ing == null || ing.isEmpty()) continue;
                boolean hasNbt = false;
                for (ItemStack m : ing.getItems()) {
                    if (!m.isEmpty() && m.has(DataComponents.CUSTOM_DATA) && !m.get(DataComponents.CUSTOM_DATA).isEmpty()) { hasNbt = true; break; }
                }
                for (ItemStack m : ing.getItems()) {
                    if (m.isEmpty()) continue;
                    TACZ_INDEX.computeIfAbsent(m.getItem().toString(), k -> new ArrayList<>())
                        .add(new TaczIngredient(rid, idx, ing, hasNbt));
                }
            }
        }
        INDEX_BUILT = true;
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
