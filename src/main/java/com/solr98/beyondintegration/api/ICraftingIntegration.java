package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Map;

/**
 * Extension point for mod-specific crafting systems that pull ingredients
 * from a BD network storage instead of (or in addition to) player inventory.
 * <p>
 * Implement this to add BD-network-aware crafting for Tacz, SuperbWarfare,
 * YWZJ, or any other mod that has a recipe system.
 */
/**
 * 合成集成扩展点：为特定模组的合成系统提供从 BD 网络存储取料的合成能力。
 * <p>
 * 实现此接口可为 Tacz、SuperbWarfare、YWZJ 或其他带配方系统的模组
 * 接入 BD 网络感知的合成（替代或补充玩家背包取料）。
 */
public interface ICraftingIntegration {

    /** The mod ID this integration handles (e.g. "tacz", "superbwarfare"). */
    String modId();

    /** Get all known recipe IDs for this mod's crafting system. */
    Collection<ResourceLocation> getRecipeIds(net.minecraft.world.item.crafting.RecipeManager manager);

    /** Check whether a given recipe ID can be handled by this integration. */
    boolean canCraft(ResourceLocation recipeId, net.minecraft.world.item.crafting.RecipeManager manager);

    /**
     * Execute a craft operation.
     * @return the result with count crafted, output item, and remaining network item counts
     */
    CraftResult executeCraft(ServerPlayer player, DimensionsNet net,
                             ResourceLocation recipeId, int count, boolean toNetwork);

    /** Result of a craft operation. */
    record CraftResult(int crafted, ItemStack output, Map<String, Long> remainingItemCounts) {}
}
