package com.solr98.beyondintegration.feature.crafting;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.api.ICraftingIntegration;
import com.solr98.beyondintegration.feature.bind.AuditEntry;
import com.solr98.beyondintegration.feature.bind.BindingAuditLog;
import com.solr98.beyondintegration.network.NetworkItemCountsPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestNetworkItemsPacket;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.*;

public final class TaczCraftManager implements ICraftingIntegration {

    private static final TaczCraftManager INSTANCE = new TaczCraftManager();

    private TaczCraftManager() {}

    public static TaczCraftManager get() { return INSTANCE; }

    @Override
    public String modId() { return "tacz"; }

    @Override
    public Collection<ResourceLocation> getRecipeIds(RecipeManager manager) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (GunSmithTableRecipe recipe : manager.getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())) {
            ids.add(recipe.getId());
        }
        return ids;
    }

    @Override
    public boolean canCraft(ResourceLocation recipeId, RecipeManager manager) {
        return manager.byKey(recipeId).map(r -> r instanceof GunSmithTableRecipe).orElse(false);
    }

    @Override
    public CraftResult executeCraft(ServerPlayer player, DimensionsNet net,
                                     ResourceLocation recipeId, int requested, boolean toNetwork) {
        var recipeOpt = player.getServer().getRecipeManager().byKey(recipeId);
        if (recipeOpt.isEmpty() || !(recipeOpt.get() instanceof GunSmithTableRecipe recipe))
            return new CraftResult(0, ItemStack.EMPTY, Collections.emptyMap());

        List<GunSmithTableIngredient> inputs = recipe.getInputs();
        if (inputs == null || inputs.isEmpty())
            return new CraftResult(0, ItemStack.EMPTY, Collections.emptyMap());

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
                        if (ti.recipeId().equals(recipeId)) {
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

        int crafted = 0;
        for (int c = 0; c < 64; c++) {
            if (requested > 0 && crafted >= requested) break;
            String missing = checkIngredients(player, inputs, idCounts, exactCounts, recipeId);
            if (missing != null) {
                if (crafted == 0) player.sendSystemMessage(Component.literal(missing));
                break;
            }
            if (!consumeInputs(player, net, inputs, ingredientKeys, idCounts, exactCounts, recipeId)) break;

            ItemStack result = recipe.getResultItem(player.level().registryAccess());
            if (!result.isEmpty()) {
                if (toNetwork) {
                    net.getUnifiedStorage().insert(new ItemStackKey(result), result.getCount(), false);
                } else {
                    var entity = new net.minecraft.world.entity.item.ItemEntity(
                            player.level(), player.getX(), player.getY() + 0.5, player.getZ(), result.copy());
                    entity.setPickUpDelay(0);
                    player.level().addFreshEntity(entity);
                }
                if (CommandConfig.enableAuditLog()) {
                    BindingAuditLog.log(new AuditEntry(
                            System.currentTimeMillis(), "GUI_CRAFT",
                            player.getName().getString(), player.getUUID(),
                            net.getId(), "ITEM", recipe.getId().toString(),
                            true, "tacz_crafted " + result.getCount() + "x " + result.getDisplayName().getString()));
                }
            }
            crafted++;
        }

        updateClient(player);

        ItemStack toastItem = crafted > 0 ? recipe.getResultItem(player.level().registryAccess()) : ItemStack.EMPTY;
        PacketHandler.sendToPlayer(player, new NetworkItemCountsPacket(idCounts, true, true,
                net.getId(), toastItem, crafted));

        return new CraftResult(crafted, toastItem, idCounts);
    }

    private static String checkIngredients(ServerPlayer player, List<GunSmithTableIngredient> inputs,
                                            Map<String, Long> idCounts, Map<String, Long> exactCounts,
                                            ResourceLocation recipeId) {
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

            String exactKey = recipeId + "|" + i;
            long exact = exactCounts.getOrDefault(exactKey, 0L);
            long inNet = exact > 0 ? exact : countFromIdMap(idCounts, ing);
            if (inInv + inNet < need) {
                ItemStack ex = ing.getItems().length > 0 ? ing.getItems()[0] : ItemStack.EMPTY;
                return Component.translatable("message.beyond_integration.material_insufficient",
                        ex.isEmpty() ? Component.translatable("command.beyond_integration.error.unknown") : ex.getHoverName()).getString();
            }
        }
        return null;
    }

    private static long countFromIdMap(Map<String, Long> idCounts, Ingredient ing) {
        long total = 0;
        for (ItemStack m : ing.getItems()) {
            if (m.isEmpty()) continue;
            total += idCounts.getOrDefault(m.getItem().toString(), 0L);
        }
        return total;
    }

    private static boolean consumeInputs(ServerPlayer player, DimensionsNet net,
                                          List<GunSmithTableIngredient> inputs,
                                          Map<Integer, List<ItemStackKey>> ingredientKeys,
                                          Map<String, Long> idCounts, Map<String, Long> exactCounts,
                                          ResourceLocation recipeId) {
        var storage = net.getUnifiedStorage();
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
                        String itemId = ik.getReadOnlyStack().getItem().toString();
                        idCounts.merge(itemId, -extracted, Long::sum);
                        List<RequestNetworkItemsPacket.TaczIngredient> rel =
                            RequestNetworkItemsPacket.TACZ_INDEX.get(itemId);
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
            if (need > 0) return false;
        }
        net.setDirty();
        return true;
    }

    private static void updateClient(ServerPlayer player) {
        if (player.containerMenu instanceof com.tacz.guns.inventory.GunSmithTableMenu menu) {
            player.inventoryMenu.broadcastFullState();
            com.tacz.guns.network.NetworkHandler.sendToClientPlayer(
                    new com.tacz.guns.network.message.ServerMessageCraft(menu.containerId), player);
        }
    }
}
