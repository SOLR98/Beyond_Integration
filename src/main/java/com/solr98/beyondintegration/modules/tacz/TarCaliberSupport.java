package com.solr98.beyondintegration.modules.tacz;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * TacZ Caliber Ammo（tacz_caliber_ammo / Tacz-Ammo-Reload）兼容支持。
 *
 * <p>全部通过反射访问 TAR 的类型，保证 TAR 未安装时 BI 不产生任何硬依赖。
 * <p>职责：
 * <ul>
 *   <li>{@link #candidateAmmoIds(ItemStack)}：给出这把枪当前可用的弹药 id 集合——
 *       先放枪自身配置的 ammoId，再放同一口径下的其它变体（TAR 的「口径」概念）。</li>
 *   <li>{@link #newRound(ResourceLocation, int)} 等：构造/读取 TAR 的 {@code Round} 记录，
 *       供换弹序列注入使用。</li>
 * </ul>
 */
public final class TarCaliberSupport {

    /** TAR 的口径管理器 */
    private static final String CALIBER_MANAGER = "com.tacz_caliber_ammo.caliber.CaliberManager";
    /** TAR 的逐发序列元素 */
    private static final String ROUND = "com.tacz_caliber_ammo.caliber.Round";

    private static Boolean loaded;

    private TarCaliberSupport() {}

    /** TAR 是否已加载（结果缓存）。 */
    public static boolean loaded() {
        if (loaded == null) {
            try {
                loaded = ModList.get() != null && ModList.get().isLoaded("tacz_caliber_ammo");
            } catch (Throwable t) {
                loaded = false;
            }
        }
        return loaded;
    }

    /**
     * 这把枪可用的弹药 id 候选（有序）：枪自身 ammoId 在前，其后是同口径的其它变体。
     * TAR 未安装或解析失败时退化为「只有枪自身 ammoId」。
     */
    public static List<ResourceLocation> candidateAmmoIds(ItemStack gun) {
        List<ResourceLocation> out = new ArrayList<>(4);
        ResourceLocation primary = TaczAmmoExtractor.getAmmoId(gun);
        if (primary != null) {
            out.add(primary);
        }
        if (!loaded()) {
            return out;
        }
        try {
            Class<?> manager = Class.forName(CALIBER_MANAGER);
            Method getAmmoCaliber = manager.getMethod("getAmmoCaliber", ResourceLocation.class);
            Method getAmmosForCaliber = manager.getMethod("getAmmosForCaliber", ResourceLocation.class);
            for (ResourceLocation id : new ArrayList<>(out)) {
                Object caliber = getAmmoCaliber.invoke(null, id);
                if (!(caliber instanceof ResourceLocation caliberId)) {
                    continue;
                }
                Object ammos = getAmmosForCaliber.invoke(null, caliberId);
                if (ammos instanceof Collection<?> collection) {
                    for (Object o : collection) {
                        if (o instanceof ResourceLocation variant && !out.contains(variant)) {
                            out.add(variant);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // TAR 结构变化时静默退化为单变体
        }
        return out;
    }

    /** 玩家主网络中「该枪口径下全部变体」的可用弹药总数（不修改网络）。 */
    public static long countAvailable(ItemStack gun, net.minecraft.server.level.ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) {
            return 0L;
        }
        long total = 0L;
        for (ResourceLocation id : candidateAmmoIds(gun)) {
            total += TaczAmmoExtractor.countAmmoInNetworkByAmmoId(id, net);
        }
        return total;
    }

    /** 构造 TAR 的 {@code Round(ResourceLocation, int)}；失败返回 null。 */
    public static Object newRound(ResourceLocation ammoId, int count) {
        try {
            Class<?> round = Class.forName(ROUND);
            return round.getConstructor(ResourceLocation.class, int.class).newInstance(ammoId, count);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 读取 TAR {@code Round.count()}。 */
    public static int roundCount(Object round) {
        try {
            Object v = round.getClass().getMethod("count").invoke(round);
            return v instanceof Integer i ? i : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 读取 TAR {@code Round.ammoId()}。 */
    public static ResourceLocation roundAmmoId(Object round) {
        try {
            Object v = round.getClass().getMethod("ammoId").invoke(round);
            return v instanceof ResourceLocation rl ? rl : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 是否装了这个模组（给外部显示用）。 */
    public static boolean hasMod() {
        return loaded();
    }
}
