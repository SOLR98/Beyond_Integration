package com.solr98.beyondintegration.feature.crafting;

import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.*;

public final class TaczCraftManager {

    public static int executeCraft(ServerPlayer sp, DimensionsNet net, GunSmithTableRecipe recipe,
                                   ResourceLocation recipeId, int requested, boolean toNetwork) {

        List<GunSmithTableIngredient> inputs = recipe.getInputs();
        if (inputs == null || inputs.isEmpty()) return 0;

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
                if (!ingredientHasNbt[ii] && m.has(DataComponents.CUSTOM_DATA)
                        && !m.get(DataComponents.CUSTOM_DATA).isEmpty()) {
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

                for (int ii = 0; ii < inputs.size(); ii++) {
                    if (ingredientIdSets[ii] == null || !ingredientIdSets[ii].contains(itemId)) continue;
                    if (!ingredientHasNbt[ii]) {
                        ingredientKeys.computeIfAbsent(ii, k -> new ArrayList<>()).add(ik);
                        continue;
                    }
                    Ingredient ing = inputs.get(ii).getIngredient();
                    if (ing != null && !ing.isEmpty() && ing.test(stored)) {
                        ingredientKeys.computeIfAbsent(ii, k -> new ArrayList<>()).add(ik);
                        exactCounts.merge(recipeId + "|" + ii, amount, Long::sum);
                    }
                }
            }
        });

        int crafted = 0;
        boolean creative = sp.isCreative();

        for (int c = 0; c < 64; c++) {
            if (requested > 0 && crafted >= requested) break;

            boolean satisfied = true;
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

                String exactKey = recipeId + "|" + i;
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
                        var items = ing.getItems();
                        var name = items.length > 0 && !items[0].isEmpty()
                                ? items[0].getHoverName().getString() : "?";
                        missing = Component.translatable("message.beyond_integration.material_insufficient", name).getString();
                    }
                    satisfied = false;
                    break;
                }
            }

            if (!satisfied) {
                if (missing != null && crafted == 0) {
                    sp.sendSystemMessage(Component.literal(missing));
                }
                break;
            }

            for (int i = 0; i < inputs.size(); i++) {
                Ingredient ing = inputs.get(i).getIngredient();
                int need = inputs.get(i).getCount();
                if (ing == null || ing.isEmpty() || need <= 0) continue;

                if (!creative) {
                    for (int j = 0; j < sp.getInventory().getContainerSize() && need > 0; j++) {
                        ItemStack stack = sp.getInventory().getItem(j);
                        if (stack.isEmpty() || !ing.test(stack)) continue;
                        int take = Math.min(need, stack.getCount());
                        stack.shrink(take);
                        need -= take;
                    }
                }

                if (need <= 0) continue;

                if (!creative) {
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
                            }
                        }
                    }
                }

                if (need > 0 && !creative) {
                    satisfied = false;
                    break;
                }
            }

            if (!satisfied) break;

            ItemStack result = recipe.getResultItem(sp.level().registryAccess());
            if (!result.isEmpty()) {
                if (toNetwork) {
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

        return crafted;
    }
}
