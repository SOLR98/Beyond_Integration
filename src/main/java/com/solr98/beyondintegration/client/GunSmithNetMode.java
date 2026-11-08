package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.client.mirror.SharedNetData;
import com.solr98.beyondintegration.core.sync.NetDataType;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczRecipeIndex;
import com.solr98.beyondintegration.network.RequestNetworkItemsPacket;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.Map;

/**
 * TACZ 枪械工作台"网络材料模式"状态与网络计数计算（普通类，供
 * GunSmithTableScreenMixin 与 taczaddon 兼容 Mixin 共用）。
 *
 * <p>网络计数来源优先「主网络镜像按配方重算」（{@code SharedNetData.available(ITEM)} 时，
 * 用同一份 {@link RequestNetworkItemsPacket#scanNetworkItems} 逻辑跑客户端镜像 + 本地配方索引，
 * 参考 JEI/Fast Recipe Search 的预构建索引思路）；不可用时回退到服务端请求通道的
 * {@link NetworkItemCache}。配方索引由 {@link TaczRecipeIndex} 预构建缓存。
 *
 * <p>Mixin 类不允许 public static 方法（会导致整个 mixin 应用失败），
 * 故将跨 mixin 共享的静态状态与计算放置于此。
 */
public final class GunSmithNetMode {

    /** 工作台是否使用网络材料模式（默认开启） */
    private static boolean useNetwork = true;
    /** 工作台合成产物是否输出到网络（默认关闭） */
    private static boolean outputToNetwork = false;

    /** 镜像重算缓存：按配方 + 两数据源版本失效。 */
    private static GunSmithTableRecipe cachedRecipe;
    private static int cachedNetVer = -1;
    private static int cachedMirrorVer = -1;
    private static int[] cachedCounts = new int[0];

    private GunSmithNetMode() {}

    public static boolean isNetworkMode() {
        return useNetwork;
    }

    public static void setNetworkMode(boolean v) {
        useNetwork = v;
    }

    public static boolean isOutputToNetwork() {
        return outputToNetwork;
    }

    public static void setOutputToNetwork(boolean v) {
        outputToNetwork = v;
    }

    /** 是否有可用网络数据源（服务端请求缓存 或 主网络镜像）。 */
    public static boolean hasNetwork() {
        return NetworkItemCache.hasNetwork() || SharedNetData.available(NetDataType.ITEM);
    }

    /** 网络数据是否为空（两者都空才算空）。 */
    public static boolean isEmpty() {
        return NetworkItemCache.isEmpty() && !SharedNetData.available(NetDataType.ITEM);
    }

    /** 数据版本（两数据源组合，供界面缓存失效判断）。 */
    public static int dataVersion() {
        return NetworkItemCache.getVersion() * 131 + PrimaryNetClientStorage.INSTANCE.version();
    }

    /**
     * 从网络读取配方各原料的数量（键：配方ID|下标）。
     * <p>优先主网络镜像按配方重算；不可用时回退 {@link NetworkItemCache}。
     */
    public static int[] calcNetworkCounts(GunSmithTableRecipe recipe) {
        if (recipe == null) return new int[0];
        var inputs = recipe.getInputs();
        if (inputs == null || inputs.isEmpty()) return new int[0];
        int size = inputs.size();

        int netVer = NetworkItemCache.getVersion();
        int mirVer = PrimaryNetClientStorage.INSTANCE.version();
        if (cachedRecipe == recipe && cachedNetVer == netVer && cachedMirrorVer == mirVer) {
            return cachedCounts;
        }

        Map<String, Long> netCounts = null;
        if (SharedNetData.available(NetDataType.ITEM)) {
            RecipeManager manager = clientRecipeManager();
            if (manager != null) {
                netCounts = RequestNetworkItemsPacket.scanNetworkItems(
                        PrimaryNetClientStorage.INSTANCE, TaczRecipeIndex.ensure(manager));
            }
        }

        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            String key = recipe.getId().toString() + "|" + i;
            long raw = netCounts != null ? netCounts.getOrDefault(key, 0L) : NetworkItemCache.getCount(key);
            counts[i] = (int) Math.min(raw, Integer.MAX_VALUE);
        }

        cachedRecipe = recipe;
        cachedNetVer = netVer;
        cachedMirrorVer = mirVer;
        cachedCounts = counts;
        return counts;
    }

    private static RecipeManager clientRecipeManager() {
        try {
            var level = Minecraft.getInstance().level;
            return level == null ? null : level.getRecipeManager();
        } catch (Throwable t) {
            return null;
        }
    }
}
