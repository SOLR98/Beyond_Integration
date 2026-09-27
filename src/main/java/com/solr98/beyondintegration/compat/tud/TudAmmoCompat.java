package com.solr98.beyondintegration.compat.tud;

import com.scarasol.tud.configuration.CommonConfig;
import com.scarasol.tud.data.AmmoData;
import com.scarasol.tud.manager.AmmoManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Tacz-Unidict（TUD，modid: {@code tacz_unidict}）兼容层：动态解析通用弹药枪的当前弹药，
 * 同时支持 TACZ 弹药与 {@code $} 前缀物品弹药（普通物品当子弹）。
 *
 * <p>当前弹药由 TOML 类型映射 / gun_data JSON + 枪 NBT {@code TudCurrentAmmo} 决定，
 * TACZ 原版静态路径（{@code CommonGunIndex.getGunData().getAmmoId()}）无法解析。本类调用：
 * <ul>
 *   <li>{@code AmmoManager.getAmmo(ItemStack)} → 当前弹药 {@code Tuple<ResourceLocation, Boolean>}
 *       （A=弹药 ID；B=true 表示 {@code $} 物品弹药）</li>
 *   <li>{@code AmmoManager.getAmmoData(ResourceLocation)} + {@code AmmoData.getAmmo()} →
 *       判断任意弹药 ID/存储物品是否为 TUD 物品弹药</li>
 * </ul>
 * 物品弹药以物品注册名作为弹药 ID，网络查询/扣除使用对应普通物品堆
 * （每 1 个物品 = 1 发，与 TUD 背包扣除语义一致），与 TACZ 弹药共用同一套 BD 存储键查询逻辑。
 *
 * <p>TUD 为编译期依赖（build.gradle），但运行时可选：TUD 未加载时所有查询直接返回 null，
 * 调用方回退 TACZ 原版路径；TUD 类引用隔离在 {@link TudBridge} 内部类，未加载时不会触发其类加载。
 */
public final class TudAmmoCompat {

    /** TUD 是否已加载（false 时所有查询直接返回 null，不触碰 TUD 类） */
    private static final boolean ENABLED = ModList.get() != null && ModList.get().isLoaded("tacz_unidict");

    /** TOML 类型映射（TYPE_TO_AMMO）中的 "$" 物品弹药 ID 集合（懒加载一次，与 TUD 自身 init 行为对齐） */
    private static volatile Set<ResourceLocation> tomlItemAmmoIds;

    private TudAmmoCompat() {}

    /** TUD 是否已加载 */
    public static boolean isLoaded() {
        return ENABLED;
    }

    /**
     * 解析枪的 TUD 当前弹药 ID（TACZ 弹药 ID 或物品注册名）。
     *
     * <p>返回 null：TUD 未加载 / 非 TUD 通用弹药枪（白名单枪、无数据）/ 解析异常，
     * 调用方回退 TACZ 原版路径。
     */
    @Nullable
    public static ResourceLocation resolve(ItemStack gun) {
        if (!ENABLED || gun == null || gun.isEmpty()) return null;
        return TudBridge.getAmmo(gun);
    }

    /**
     * 存储物品是否为 TUD 注册的 {@code $} 物品弹药；是则返回弹药 ID（物品注册名），否则 null。
     */
    @Nullable
    public static ResourceLocation getItemAmmoId(ItemStack stack) {
        if (!ENABLED || stack == null || stack.isEmpty()) return null;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null || !isItemAmmo(itemId)) return null;
        return itemId;
    }

    /**
     * 按弹药 ID 构造物品弹药参考堆（普通物品堆，无 NBT）；
     * 非 TUD 物品弹药返回 null（调用方回退 TACZ 弹药参考堆）。
     */
    @Nullable
    public static ItemStack getItemAmmoStack(ResourceLocation ammoId) {
        if (!ENABLED || ammoId == null || !isItemAmmo(ammoId)) return null;
        Item item = ForgeRegistries.ITEMS.getValue(ammoId);
        if (item == null || item == Items.AIR) return null;
        return new ItemStack(item);
    }

    /**
     * 该弹药 ID 是否属于 TUD 物品弹药：
     * ① TOML 类型映射的 "$" 前缀条目（默认配置，不产生 AmmoData 注册）；
     * ② gun_data JSON 注册的 AmmoData（isItem=true）。
     */
    private static boolean isItemAmmo(ResourceLocation ammoId) {
        if (getTomlItemAmmoIds().contains(ammoId)) return true;
        return TudBridge.isJsonItemAmmo(ammoId);
    }

    /** TOML "$" 物品弹药 ID 集合（双检锁懒加载；配置未就绪时返回空集合且不缓存，下次重试） */
    private static Set<ResourceLocation> getTomlItemAmmoIds() {
        Set<ResourceLocation> ids = tomlItemAmmoIds;
        if (ids != null) return ids;
        synchronized (TudAmmoCompat.class) {
            if (tomlItemAmmoIds == null) {
                Set<ResourceLocation> parsed = TudBridge.getTomlItemAmmoIds();
                if (parsed == null) return java.util.Collections.emptySet();
                tomlItemAmmoIds = parsed;
            }
            return tomlItemAmmoIds;
        }
    }

    /** TUD 类引用隔离层：仅在 TUD 已加载时被加载/执行（内部异常一律降级为回退原版） */
    private static final class TudBridge {
        private TudBridge() {}

        @Nullable
        static ResourceLocation getAmmo(ItemStack gun) {
            try {
                Tuple<ResourceLocation, Boolean> ammo = AmmoManager.getAmmo(gun);
                return ammo != null ? ammo.getA() : null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        /** gun_data JSON 注册的物品弹药判断（AmmoData.isItem=true） */
        static boolean isJsonItemAmmo(ResourceLocation ammoId) {
            try {
                AmmoData ammoData = AmmoManager.getAmmoData(ammoId);
                if (ammoData == null) return false;
                Tuple<ResourceLocation, Boolean> ammo = ammoData.getAmmo();
                return ammo != null && Boolean.TRUE.equals(ammo.getB());
            } catch (Throwable ignored) {
                return false;
            }
        }

        /** 解析 CommonConfig.TYPE_TO_AMMO 中 "$" 前缀条目 → 物品弹药 ID 集合；配置未就绪返回 null */
        @Nullable
        static Set<ResourceLocation> getTomlItemAmmoIds() {
            Set<ResourceLocation> ids = new HashSet<>();
            try {
                for (String entry : CommonConfig.TYPE_TO_AMMO.get()) {
                    if (entry == null) continue;
                    String[] parts = entry.split(",");
                    if (parts.length < 2) continue;
                    String ammo = parts[1].trim();
                    if (!ammo.startsWith("$")) continue;
                    ResourceLocation id = ResourceLocation.tryParse(ammo.substring(1).trim());
                    if (id != null) ids.add(id);
                }
            } catch (Throwable ignored) {
                return null;
            }
            return ids;
        }
    }
}
