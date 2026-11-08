package com.solr98.beyondintegration.feature.feeder;

import net.minecraft.world.item.ItemStack;

/**
 * 网络喂食器口渴/饱食相关设置的 NBT 读写（存放在喂食物品的 ItemStack 上，键名统一加 {@code bi_} 前缀）。
 * <p>全部用<b>阈值</b>判定，四项独立可调（0~20）：
 * <ul>
 *   <li>{@code bi_thirst_threshold}：口渴值 ≤ 该值 → 补水</li>
 *   <li>{@code bi_quenched_threshold}：水饱和 ≤ 该值 → 补水（注意：补水会同时抬口渴值，
 *       阈值调高会让口渴长期贴上限）</li>
 *   <li>{@code bi_food_threshold}：饱食度 ≤ 该值 → 喂食</li>
 *   <li>{@code bi_saturation_threshold}：饥饿饱和度 ≤ 该值 → 喂食（同样会把饱食度顶到上限）</li>
 * </ul>
 * <p>旧的枚举档位键 {@code bi_thirst_mode} 与临时开关 {@code bi_regen_mode} 均已废弃：
 * <b>不再读取</b>，并在读写设置时自动从 NBT 删除。
 * <p><b>机制硬性限制（非缺陷，不要“修”）</b>：补给是「触发即补满」的——
 * 补水/进食会同时抬高主值与对应饱和值（{@code ThirstBridge.add} 一次写口渴+水饱和；
 * 原版 {@code finishUsingItem} 一次把饱食度加到接近/等于上限）。因此只要把
 * {@code bi_quenched_threshold} 或 {@code bi_saturation_threshold} 设为 &gt; 0，
 * 口渴/饱食度就会被反复拉回上限，前两项阈值形同失效。这是预期行为。
 */
public final class FeederThirstSettings
{
    public static final String KEY_THIRST = "bi_thirst_threshold";
    public static final String KEY_QUENCHED = "bi_quenched_threshold";
    public static final String KEY_FOOD = "bi_food_threshold";
    public static final String KEY_SATURATION = "bi_saturation_threshold";

    /** 已废弃：旧枚举档位（自动清除，不读取）。 */
    public static final String LEGACY_KEY_MODE = "bi_thirst_mode";
    /** 已废弃：临时回血开关（自动清除，不读取）。 */
    public static final String LEGACY_KEY_REGEN = "bi_regen_mode";

    /** 默认口渴阈值（对应旧版 NORMAL：口渴 ≤ 10）。 */
    public static final int DEFAULT_THIRST = 10;
    /** 默认水饱和阈值：0 = 不因水饱和补水。 */
    public static final int DEFAULT_QUENCHED = 0;
    /** 默认饱食度阈值（与 BD 原 NORMAL 一致：饱食度 ≤ 10 才吃）。 */
    public static final int DEFAULT_FOOD = 10;
    /** 默认饥饿饱和度阈值：0 = 不因饱和度喂食。 */
    public static final int DEFAULT_SATURATION = 0;

    public static final int MIN = 0;
    public static final int MAX = 20;

    private FeederThirstSettings() {}

    // ─────────────────────────── 读取 ───────────────────────────

    public static int thirstThreshold(ItemStack stack) {
        return read(stack, KEY_THIRST, DEFAULT_THIRST);
    }

    public static int quenchedThreshold(ItemStack stack) {
        return read(stack, KEY_QUENCHED, DEFAULT_QUENCHED);
    }

    public static int foodThreshold(ItemStack stack) {
        return read(stack, KEY_FOOD, DEFAULT_FOOD);
    }

    public static int saturationThreshold(ItemStack stack) {
        return read(stack, KEY_SATURATION, DEFAULT_SATURATION);
    }

    // ─────────────────────────── 写入 ───────────────────────────

    public static void setThirstThreshold(ItemStack stack, int value) {
        write(stack, KEY_THIRST, value);
    }

    public static void setQuenchedThreshold(ItemStack stack, int value) {
        write(stack, KEY_QUENCHED, value);
    }

    public static void setFoodThreshold(ItemStack stack, int value) {
        write(stack, KEY_FOOD, value);
    }

    public static void setSaturationThreshold(ItemStack stack, int value) {
        write(stack, KEY_SATURATION, value);
    }

    // ─────────────────────────── 判定 ───────────────────────────

    /** 口渴是否达到补水时机：口渴 ≤ 阈值，或水饱和 ≤ 阈值。 */
    public static boolean shouldDrink(int thirst, int quenched, int thirstThreshold, int quenchedThreshold) {
        return thirst <= clamp(thirstThreshold) || quenched <= clamp(quenchedThreshold);
    }

    /** 饥饿是否达到喂食时机：饱食度 ≤ 阈值，或饱和度 ≤ 阈值。 */
    public static boolean shouldEat(int foodLevel, float saturation, int foodThreshold, int saturationThreshold) {
        return foodLevel <= clamp(foodThreshold) || saturation <= (float) clamp(saturationThreshold);
    }

    // ─────────────────────────── 内部 ───────────────────────────

    /** 读取一项阈值；顺带清除所有已废弃的键（不在读取时使用它们的值）。 */
    private static int read(ItemStack stack, String key, int fallback) {
        if (stack == null || !stack.hasTag()) return fallback;
        var tag = stack.getTag();
        if (tag == null) return fallback;
        boolean cleaned = false;
        if (tag.contains(LEGACY_KEY_MODE)) {
            tag.remove(LEGACY_KEY_MODE);
            cleaned = true;
        }
        if (tag.contains(LEGACY_KEY_REGEN)) {
            tag.remove(LEGACY_KEY_REGEN);
            cleaned = true;
        }
        if (cleaned && !tag.contains(key)) {
            // 只清理、不写默认值（保持 NBT 精简）
        }
        if (!tag.contains(key)) return fallback;
        return clamp(tag.getInt(key));
    }

    private static void write(ItemStack stack, String key, int value) {
        if (stack == null) return;
        var tag = stack.getOrCreateTag();
        tag.remove(LEGACY_KEY_MODE);
        tag.remove(LEGACY_KEY_REGEN);
        tag.putInt(key, clamp(value));
    }

    private static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
