package com.solr98.beyondintegration.modules.tacz.client;

import com.solr98.beyondintegration.client.NetworkItemCache;
import com.solr98.beyondintegration.ClientConfig;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.resources.ResourceLocation;
import com.solr98.beyondintegration.mixin.tacz.GunSmithTableScreenMixin;

/**
 * TACZ 枪械工作台"网络材料模式"状态与网络计数计算（普通类，供
 * GunSmithTableScreenMixin 与 taczaddon 兼容 Mixin 共用）。
 *
 * Mixin 类不允许 public static 方法（会导致整个 mixin 应用失败），
 * 故将跨 mixin 共享的静态状态与计算放置于此。
 */
public final class GunSmithNetMode {

    /** 工作台是否使用网络材料模式（默认开启） */
    private static boolean useNetwork = true;
    /** 工作台合成产物是否输出到网络（默认关闭） */
    private static boolean outputToNetwork = false;

    private GunSmithNetMode() {}

    public static boolean isNetworkMode() {
        return useNetwork;
    }

    public static void setNetworkMode(boolean v) {
        useNetwork = v;
        // TACZ 1.2.0 的 GunSmithTableScreen 不再 override onClose，mixin 的 onClose 保存点失效，
        // 改为在状态变更时直接落盘（调用频率仅按钮点击/加载一次）。
        try { com.solr98.beyondintegration.ClientConfig.setTaczSmithUseNetwork(v); } catch (Throwable ignored) {}
    }

    public static boolean isOutputToNetwork() {
        return outputToNetwork;
    }

    public static void setOutputToNetwork(boolean v) {
        outputToNetwork = v;
        try { com.solr98.beyondintegration.ClientConfig.setTaczSmithOutputToNetwork(v); } catch (Throwable ignored) {}
    }

    /**
     * 从网络物品缓存读取配方各原料的数量（键：配方ID|下标）
     */
    public static int[] calcNetworkCounts(ResourceLocation recipeId, GunSmithTableRecipe recipe) {
        var inputs = recipe.getInputs();
        if (inputs == null || inputs.isEmpty()) return new int[0];
        int size = inputs.size();
        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            String key = recipeId + "|" + i;
            long raw = NetworkItemCache.getCount(key);
            counts[i] = (int) Math.min(raw, Integer.MAX_VALUE);
        }
        return counts;
    }
}
