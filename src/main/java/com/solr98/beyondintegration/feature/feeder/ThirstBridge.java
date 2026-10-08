package com.solr98.beyondintegration.feature.feeder;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * 口渴后端统一桥：屏蔽 Thirst was Reclaimed（cn.mlus）与 Legendary Survival Overhaul（sfiomn）差异。
 * <p>运行时按加载选择：优先 {@code thirst}，其次 {@code legendarysurvivaloverhaul}，都无则 {@link #NOOP}。
 * <ul>
 *   <li>主值：Thirst=thirst / LSO=hydration（0–20）</li>
 *   <li>副值（水饱和）：Thirst=quenched / LSO=round(saturation)</li>
 * </ul>
 */
public interface ThirstBridge
{
    boolean loaded();

    /** 主值（口渴/水分） */
    int primary(Player player);

    /** 副值（水饱和） */
    int secondary(Player player);

    /** 补水：主值 + 副值；{@code purity}(0–3, -1 表示未知) 用于 Thirst 的水纯度效果（LSO 忽略） */
    void add(Player player, int primary, int secondary, int purity);

    /** 可食用食物是否在"吃"时自动补水（Thirst 经 Player.eat 混入；LSO 需显式调用） */
    boolean hydratesViaEat();

    /** 该物品是否能补水（Thirst 名单 / LSO 消耗品数据） */
    boolean restoresThirst(ItemStack stack);

    /** 按物品自身配置执行一次补水 */
    void drinkItem(ItemStack stack, Player player);

    /** {@code finishUsingItem} 内部是否会自行补水（Thirst 的 DrinkableItem） */
    boolean drinksOnFinish(Item item);

    /** 给水容器打上该后端的水纯度/类型标签 */
    void tagPurity(ItemStack stack, int purity);

    /** 读取水容器纯度（0–3）；无则 -1 */
    int readPurity(ItemStack stack);

    /** 展开为按“补水顺序”排列的后端列表（组合后端返回其子后端；默认仅自身）。 */
    default List<ThirstBridge> backends() { return List.of(this); }

    static ThirstBridge get()
    {
        boolean thirst = ModList.get().isLoaded("thirst");
        boolean lso = ModList.get().isLoaded("legendarysurvivaloverhaul");
        // 同时存在多种口渴系统：组合后端同时驱动（是否分开给量由配置决定）
        if (thirst && lso) return new CompositeThirstBridge(ThirstCompat.INSTANCE, LsoThirstBackend.INSTANCE);
        if (thirst) return ThirstCompat.INSTANCE;
        if (lso) return LsoThirstBackend.INSTANCE;
        return NOOP;
    }

    ThirstBridge NOOP = new ThirstBridge()
    {
        @Override public boolean loaded() { return false; }
        @Override public int primary(Player player) { return 20; }
        @Override public int secondary(Player player) { return 0; }
        @Override public void add(Player player, int primary, int secondary, int purity) {}
        @Override public boolean hydratesViaEat() { return false; }
        @Override public boolean restoresThirst(ItemStack stack) { return false; }
        @Override public void drinkItem(ItemStack stack, Player player) {}
        @Override public boolean drinksOnFinish(Item item) { return false; }
        @Override public void tagPurity(ItemStack stack, int purity) {}
        @Override public int readPurity(ItemStack stack) { return -1; }
    };
}
