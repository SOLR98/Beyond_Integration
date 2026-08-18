package com.solr98.beyondintegration.feature.crafting;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import com.tacz.guns.crafting.GunSmithTableRecipe;

/**
 * 网络合成数量计算工具（TACZ 合成配套）：纯静态方法，不可实例化。
 * 按配方输入顺序查询网络可用数量并并入玩家背包计数。
 */
public final class NetworkCraftingHelper {

    private NetworkCraftingHelper() {
        throw new AssertionError("No instances");
    }

    // 按配方输入顺序查询网络可用数量（键：recipeId|输入索引），钳制到 int 上限
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

    // 将网络数量并入玩家背包计数（按索引对齐，越界忽略，溢出钳制）
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
