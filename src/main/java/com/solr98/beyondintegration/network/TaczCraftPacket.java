package com.solr98.beyondintegration.network;

import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
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

            var recipeOpt = player.getServer().getRecipeManager().byKey(msg.recipeId);
            if (recipeOpt.isEmpty()) return;

            var recipe = recipeOpt.get();
            if (!(recipe instanceof GunSmithTableRecipe taczRecipe)) return;
            List<GunSmithTableIngredient> inputs = taczRecipe.getInputs();
            if (inputs == null || inputs.isEmpty()) return;

            RequestNetworkItemsPacket.ensureIndex(player);
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
                    if (!ingredientHasNbt[ii] && m.hasTag() && !m.getTag().isEmpty()) {
                        ingredientHasNbt[ii] = true;
                    }
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

                    List<RequestNetworkItemsPacket.TaczIngredient> related =
                        RequestNetworkItemsPacket.TACZ_INDEX.get(itemId);
                    if (related != null) {
                        for (var ti : related) {
                            if (!ti.hasNbt()) continue;
                            if (!ti.ingredient().test(stored)) continue;
                            exactCounts.merge(ti.recipeId() + "|" + ti.idx(), amount, Long::sum);
                            if (ti.recipeId().equals(msg.recipeId)) {
                                ingredientKeys.computeIfAbsent(ti.idx(), k -> new ArrayList<>()).add(ik);
                            }
                        }
                    }

                    for (int ii = 0; ii < inputs.size(); ii++) {
                        if (ingredientHasNbt[ii]) continue;
                        Set<String> ids = ingredientIdSets[ii];
                        if (ids != null && ids.contains(itemId)) {
                            ingredientKeys.computeIfAbsent(ii, k -> new ArrayList<>()).add(ik);
                            continue;
                        }
                        GunSmithTableIngredient gi = inputs.get(ii);
                        if (gi == null) continue;
                        Ingredient ing = gi.getIngredient();
                        if (ing != null && !ing.isEmpty() && ing.test(stored)) {
                            ingredientKeys.computeIfAbsent(ii, k -> new ArrayList<>()).add(ik);
                        }
                    }
                }
            });

            int requested = msg.count;
            int crafted = 0;

            for (int c = 0; c < 64; c++) {
                if (requested > 0 && crafted >= requested) break;

                String missing = null;
                for (int i = 0; i < inputs.size(); i++) {
                    GunSmithTableIngredient gi = inputs.get(i);
                    if (gi == null) continue;
                    Ingredient ing = gi.getIngredient();
                    int need = gi.getCount();
                    if (ing == null || ing.isEmpty() || need <= 0) continue;

                    int inInv = 0;
                    for (int j = 0; j < player.getInventory().getContainerSize(); j++) {
                        ItemStack stack = player.getInventory().getItem(j);
                        if (!stack.isEmpty() && ing.test(stack)) inInv += stack.getCount();
                    }

                    String exactKey = msg.recipeId + "|" + i;
                    long exact = exactCounts.getOrDefault(exactKey, 0L);
                    long inNet;
                    if (exact > 0) {
                        inNet = exact;
                    } else {
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
                                    ex.isEmpty() ? Component.translatable("command.beyond_integration.error.unknown") : ex.getHoverName()).getString();
                        }
                    }
                }

                if (missing != null) {
                    if (crafted == 0) {
                        player.sendSystemMessage(Component.literal(missing));
                    }
                    break;
                }

                boolean satisfied = true;
                for (int i = 0; i < inputs.size(); i++) {
                    GunSmithTableIngredient gi = inputs.get(i);
                    if (gi == null) continue;
                    Ingredient ing = gi.getIngredient();
                    int need = gi.getCount();
                    if (ing == null || ing.isEmpty() || need <= 0) continue;

                    for (int j = 0; j < player.getInventory().getContainerSize() && need > 0; j++) {
                        ItemStack stack = player.getInventory().getItem(j);
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
                            KeyAmount extractResult = storage.extract(ik, take, false, false);
                            long extracted = extractResult.amount();
                            if (extracted > 0) {
                                need -= extracted;
                                String cid = ik.getReadOnlyStack().getItem().toString();
                                idCounts.merge(cid, -extracted, Long::sum);
                                List<RequestNetworkItemsPacket.TaczIngredient> rel =
                                    RequestNetworkItemsPacket.TACZ_INDEX.get(cid);
                                if (rel != null) {
                                    for (var ti : rel) {
                                        if (ti.hasNbt() && ti.ingredient().test(ik.getReadOnlyStack())) {
                                            exactCounts.merge(ti.recipeId() + "|" + ti.idx(), -extracted, Long::sum);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (need > 0) {
                        satisfied = false;
                        break;
                    }
                }

                if (!satisfied) break;

                ItemStack result = recipe.getResultItem(player.level().registryAccess());
                if (!result.isEmpty()) {
                    if (msg.toNetwork) {
                        ItemStackKey resultKey = new ItemStackKey(result);
                        net.getUnifiedStorage().insert(resultKey, result.getCount(), false);
                    } else {
                        var entity = new net.minecraft.world.entity.item.ItemEntity(
                                player.level(), player.getX(), player.getY() + 0.5, player.getZ(), result.copy());
                        entity.setPickUpDelay(0);
                        player.level().addFreshEntity(entity);
                    }
                }

                crafted++;
            }

            if (player.containerMenu instanceof com.tacz.guns.inventory.GunSmithTableMenu menu) {
                player.inventoryMenu.broadcastFullState();
                com.tacz.guns.network.NetworkHandler.sendToClientPlayer(
                        new com.tacz.guns.network.message.ServerMessageCraft(menu.containerId), player);
            }

            for (var e : exactCounts.entrySet()) {
                idCounts.put(e.getKey(), e.getValue());
            }
            idCounts.values().removeIf(v -> v <= 0);
            ItemStack toastItem = crafted > 0 ? recipe.getResultItem(player.level().registryAccess()) : ItemStack.EMPTY;
            PacketHandler.sendToPlayer(player, new NetworkItemCountsPacket(idCounts, true, true,
                    net != null ? net.getId() : -1, toastItem, crafted));
        });
        ctx.get().setPacketHandled(true);
    }
}
