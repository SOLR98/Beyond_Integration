package com.solr98.beyondintegration.feature.ammo.tacz;

import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.solr98.beyondintegration.core.sync.NetSyncDebug;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TACZ 枪械工作台配方索引（预构建缓存）。
 * <p>结构：物品注册名 → 该物品参与的所有配方槽位 {@link TaczIngredient}（含配方 ID、输入下标、匹配用
 * {@link Ingredient}、是否含 NBT）。构建后常驻内存；配方加载 / 数据包同步后由事件重建；请求路径直接读缓存。
 * <p>参考 JEI {@code RecipeMap} 的「反向索引」思路：索引在加载阶段预构建，查询 O(1)。
 */
public final class TaczRecipeIndex {

    /** 一条 TACZ 配方输入记录。 */
    public record TaczIngredient(ResourceLocation recipeId, int idx, Ingredient ingredient, boolean hasNbt) {}

    private static volatile Map<String, List<TaczIngredient>> index = Map.of();
    private static volatile int recipeCount = -1;
    private static volatile boolean ready = false;
    private static volatile RecipeManager lastManager = null;

    private TaczRecipeIndex() {}

    /** 索引是否已就绪。 */
    public static boolean isReady() {
        return ready;
    }

    /** 当前索引（只读）。 */
    public static Map<String, List<TaczIngredient>> index() {
        return index;
    }

    /** 由配方加载事件调用：一次性构建并缓存。 */
    public static synchronized void build(RecipeManager manager) {
        if (manager == null) return;
        long t0 = NetSyncDebug.start();
        Map<String, List<TaczIngredient>> map = new HashMap<>();
        for (var recipe : manager.getRecipes()) {
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
                    map.computeIfAbsent(id, k -> new ArrayList<>())
                            .add(new TaczIngredient(rid, idx, ing, hasNbt));
                }
            }
        }
        index = map;
        recipeCount = manager.getRecipes().size();
        lastManager = manager;
        ready = true;
        NetSyncDebug.perf("taczRecipeIndex.build", t0, "recipes", recipeCount, "keys", map.size());
    }

    /** 惰性兜底：未就绪或配方数变化时构建，返回索引（供请求路径在事件未触发时兜底）。 */
    public static synchronized Map<String, List<TaczIngredient>> ensure(RecipeManager manager) {
        if (manager == null) return index;
        int count = manager.getRecipes().size();
        if (!ready || lastManager != manager || recipeCount != count) {
            build(manager);
        }
        return index;
    }

    /** 清空缓存（配方重载前的显式失效；事件重建会覆盖）。 */
    public static synchronized void reset() {
        index = Map.of();
        recipeCount = -1;
        lastManager = null;
        ready = false;
    }
}
