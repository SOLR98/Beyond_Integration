package com.solr98.beyondintegration.network;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import java.util.*;

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
            if (!RequestNetworkItemsPacket.INDEX_BUILT) RequestNetworkItemsPacket.buildIndex(sp);

            var recipeOpt = sp.getServer().getRecipeManager().byKey(packet.recipeId);
            if (recipeOpt.isEmpty()) return;
            var holder = recipeOpt.get();
            if (!(holder.value() instanceof GunSmithTableRecipe taczRecipe)) return;
            List<GunSmithTableIngredient> inputs = taczRecipe.getInputs();
            if (inputs == null || inputs.isEmpty()) return;

            Map<String, Long> idCounts = new HashMap<>();
            Map<String, Long> exactCounts = new HashMap<>();
            Map<Integer, List<ItemStackKey>> ingredientKeys = new HashMap<>();

            boolean[] ingredientHasNbt = new boolean[inputs.size()];
            Set<String>[] ingredientIdSets = new Set[inputs.size()];
            for (int ii = 0; ii < inputs.size(); ii++) {
                Ingredient ing = inputs.get(ii).getIngredient();
                if (ing == null) continue;
                Set<String> ids = new HashSet<>();
                for (ItemStack m : ing.getItems()) {
                    if (m.isEmpty()) continue;
                    ids.add(m.getItem().toString());
                    if (!ingredientHasNbt[ii] && m.has(DataComponents.CUSTOM_DATA) && !m.get(DataComponents.CUSTOM_DATA).isEmpty()) ingredientHasNbt[ii] = true;
                }
                ingredientIdSets[ii] = ids;
            }

            var storage = net.getUnifiedStorage();
            storage.getBucket(ItemStackKey.ID).ifPresent(bucket -> {
                for (int bi = 0; bi < bucket.size(); bi++) {
                    IStackKey<?> rawKey = bucket.get(bi);
                    if (!(rawKey instanceof ItemStackKey ik)) continue;
                    long amount = storage.getStackByKey(ik).amount();
                    if (amount <= 0) continue;
                    ItemStack stored = ik.getReadOnlyStack();
                    if (stored.isEmpty()) continue;

                    String itemId = stored.getItem().toString();
                    idCounts.merge(itemId, amount, Long::sum);

                    List<RequestNetworkItemsPacket.TaczIngredient> related = RequestNetworkItemsPacket.TACZ_INDEX.get(itemId);
                    if (related != null) {
                        for (var ti : related) {
                            if (!ti.hasNbt()) continue;
                            if (!ti.ingredient().test(stored)) continue;
                            exactCounts.merge(ti.recipeId() + "|" + ti.idx(), amount, Long::sum);
                            if (ti.recipeId().equals(packet.recipeId))
                                ingredientKeys.computeIfAbsent(ti.idx(), k -> new ArrayList<>()).add(ik);
                        }
                    }

                    for (int ii = 0; ii < inputs.size(); ii++) {
                        if (ingredientHasNbt[ii]) continue;
                        if (ingredientIdSets[ii] != null && ingredientIdSets[ii].contains(itemId)) {
                            ingredientKeys.computeIfAbsent(ii, k -> new ArrayList<>()).add(ik);
                            continue;
                        }
                        Ingredient ing = inputs.get(ii).getIngredient();
                        if (ing != null && !ing.isEmpty() && ing.test(stored))
                            ingredientKeys.computeIfAbsent(ii, k -> new ArrayList<>()).add(ik);
                    }
                }
            });

            int crafted = 0;
            int requested = packet.count;

            for (int c = 0; c < 64; c++) {
                if (requested > 0 && crafted >= requested) break;

                String missing = null;
                for (int i = 0; i < inputs.size(); i++) {
                    Ingredient ing = inputs.get(i).getIngredient();
                    int need = inputs.get(i).getCount();
                    if (ing == null || ing.isEmpty() || need <= 0) continue;

                    int inInv = 0;
                    for (int j = 0; j < sp.getInventory().getContainerSize(); j++) {
                        ItemStack stack = sp.getInventory().getItem(j);
                        if (!stack.isEmpty() && ing.test(stack)) inInv += stack.getCount();
                    }

                    String exactKey = packet.recipeId + "|" + i;
                    long exact = exactCounts.getOrDefault(exactKey, 0L);
                    long inNet;
                    if (exact > 0) inNet = exact;
                    else {
                        inNet = 0;
                        for (ItemStack m : ing.getItems()) {
                            if (m.isEmpty()) continue;
                            inNet += idCounts.getOrDefault(m.getItem().toString(), 0L);
                        }
                    }

                    if (inInv + inNet < need) {
                        if (missing == null) {
                            ItemStack ex = ing.getItems().length > 0 ? ing.getItems()[0] : ItemStack.EMPTY;
                            missing = Component.translatable("message.beyond_integration.material_insufficient",
                                    ex.isEmpty() ? "?" : ex.getHoverName().getString()).getString();
                        }
                    }
                }

                if (missing != null) {
                    if (crafted == 0) sp.sendSystemMessage(Component.literal(missing));
                    break;
                }

                for (int i = 0; i < inputs.size(); i++) {
                    Ingredient ing = inputs.get(i).getIngredient();
                    int need = inputs.get(i).getCount();
                    if (ing == null || ing.isEmpty() || need <= 0) continue;

                    for (int j = 0; j < sp.getInventory().getContainerSize() && need > 0; j++) {
                        ItemStack stack = sp.getInventory().getItem(j);
                        if (stack.isEmpty() || !ing.test(stack)) continue;
                        int take = Math.min(need, stack.getCount());
                        stack.shrink(take);
                        need -= take;
                    }

                    if (need <= 0) continue;

                    List<ItemStackKey> keys = ingredientKeys.get(i);
                    if (keys != null) {
                        for (ItemStackKey ik : keys) {
                            if (need <= 0) break;
                            long avail = storage.getStackByKey(ik).amount();
                            if (avail <= 0) continue;
                            long take = Math.min(need, avail);
                            KeyAmount extracted = storage.extract(ik, take, false, false);
                            if (extracted.amount() > 0) {
                                need -= extracted.amount();
                                String cid = ik.getReadOnlyStack().getItem().toString();
                                idCounts.merge(cid, -extracted.amount(), Long::sum);
                                List<RequestNetworkItemsPacket.TaczIngredient> rel =
                                    RequestNetworkItemsPacket.TACZ_INDEX.get(cid);
                                if (rel != null) {
                                    for (var ti : rel) {
                                        if (ti.hasNbt() && ti.ingredient().test(ik.getReadOnlyStack()))
                                            exactCounts.merge(ti.recipeId() + "|" + ti.idx(), -extracted.amount(), Long::sum);
                                    }
                                }
                            }
                        }
                    }
                }

                ItemStack result = taczRecipe.getResultItem(sp.level().registryAccess());
                if (!result.isEmpty()) {
                    if (packet.toNetwork) {
                        net.getUnifiedStorage().insert(new ItemStackKey(result), result.getCount(), false);
                    } else {
                        var entity = new net.minecraft.world.entity.item.ItemEntity(
                                sp.level(), sp.getX(), sp.getY() + 0.5, sp.getZ(), result.copy());
                        entity.setPickUpDelay(0);
                        sp.level().addFreshEntity(entity);
                    }
                }
                crafted++;
            }

            net.setDirty();

            if (sp.containerMenu instanceof com.tacz.guns.inventory.GunSmithTableMenu menu) {
                sp.inventoryMenu.broadcastFullState();
                com.tacz.guns.network.NetworkHandler.sendToClientPlayer(
                        new com.tacz.guns.network.message.ServerMessageCraft(menu.containerId), sp);
            }

            ItemStack resultItem = crafted > 0 ? taczRecipe.getResultItem(sp.level().registryAccess()) : ItemStack.EMPTY;
            Map<String, Long> responseCounts = new HashMap<>(idCounts);
            responseCounts.putAll(exactCounts);
            responseCounts.values().removeIf(v -> v <= 0);
            PacketHandler.sendToPlayer(sp, new NetworkItemCountsPacket(responseCounts, true, true,
                    net != null ? net.getId() : -1, net != null ? net.getCustomName() : "", resultItem, crafted));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
