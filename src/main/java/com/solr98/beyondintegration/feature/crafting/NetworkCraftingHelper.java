package com.solr98.beyondintegration.feature.crafting;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import com.tacz.guns.crafting.GunSmithTableRecipe;

/**
 * Extracted logic from {@code GunSmithTableScreenMixin} for TACZ network crafting.
 * <p>
 * Handles ingredient counting from BD network storage and merging network counts
 * into the player's ingredient display in the GunSmithTable GUI.
 */
public final class NetworkCraftingHelper {

    private NetworkCraftingHelper() {
        throw new AssertionError("No instances");
    }

    /**
     * Calculate how many of each ingredient are available across all the player's networks.
     * @param recipeId the full recipe identifier string, used as cache key prefix
     * @param recipe the recipe whose ingredients to check
     * @param countProvider a function that returns the network stock for a given cache key
     * @return an int array of the same length as the recipe's input list
     */
    public static int[] calcNetworkCounts(String recipeId, GunSmithTableRecipe recipe,
                                           java.util.function.Function<String, Long> countProvider) {
        var inputs = recipe.getInputs();
        if (inputs == null || inputs.isEmpty()) return new int[0];
        int[] counts = new int[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            counts[i] = (int) Math.min(countProvider.apply(recipeId + "|" + i), Integer.MAX_VALUE);
        }
        return counts;
    }

    /**
     * Merge network ingredient counts into the existing player ingredient count map.
     */
    public static void mergeNetworkCounts(Int2IntArrayMap playerCounts, int[] networkCounts) {
        int max = Math.min(networkCounts.length, playerCounts.size());
        for (int i = 0; i < max; i++) {
            int net = networkCounts[i];
            if (net > 0) {
                long before = playerCounts.get(i);
                long after = Math.min(before + net, Integer.MAX_VALUE);
                playerCounts.put(i, (int) after);
            }
        }
    }
}
