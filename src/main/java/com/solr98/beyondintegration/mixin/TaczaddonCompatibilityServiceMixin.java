package com.solr98.beyondintegration.mixin;

import com.mafuyu404.taczaddon.compat.tacz.api.TaczGunSmithScreenAccess;
import com.mafuyu404.taczaddon.client.GunSmithCompatibilityService;
import com.solr98.beyondintegration.client.NetworkItemCache;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 与 taczaddon 的来源快照计数路径兼容（仅 1.1.8.1，MixinPlugin 门控）：
 *
 * addon 收到来源快照时经 GunSmithCompatibilityService.applyExternalIngredientCounts
 * 整体替换 playerIngredientCount = 背包 + 外部容器（不经过 getPlayerIngredientCount）。
 * 网络模式下（外部源已被 TaczaddonIngredientCountMixin 隐藏为空），该替换会把计数
 * 重置为纯背包，使网络数量显示闪断。本 Mixin 在其 RETURN 处重新叠加网络数量，
 * 保证网络模式计数恒为 背包 + 网络。
 */
@Mixin(value = GunSmithCompatibilityService.class, remap = false)
public abstract class TaczaddonCompatibilityServiceMixin {

    /** 来源快照应用后叠加网络数量（仅网络模式；非网络模式 addon 原样） */
    @Inject(method = "applyExternalIngredientCounts", at = @At("RETURN"), remap = false)
    private static void beyond$mergeNetworkAfterSnapshot(
            TaczGunSmithScreenAccess access,
            List<ItemStack> externalStacks,
            CallbackInfo ci
    ) {
        if (!com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode()) return;
        if (!NetworkItemCache.hasNetwork() || NetworkItemCache.isEmpty()) return;

        GunSmithTableRecipe recipe = access.taczaddon$getSelectedRecipe();
        if (recipe == null) return;
        Int2IntArrayMap map = access.taczaddon$getPlayerIngredientCount();
        if (map == null) return;

        int[] networkCounts = com.solr98.beyondintegration.client.GunSmithNetMode.calcNetworkCounts(recipe);
        int max = Math.min(networkCounts.length, map.size());
        for (int i = 0; i < max; i++) {
            int net = networkCounts[i];
            if (net > 0) {
                long before = map.get(i);
                long after = Math.min(before + net, Integer.MAX_VALUE);
                map.put(i, (int) after);
            }
        }
        access.taczaddon$setPlayerIngredientCount(map);
    }
}
