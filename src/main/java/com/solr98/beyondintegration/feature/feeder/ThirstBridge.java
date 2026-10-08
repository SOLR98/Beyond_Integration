package com.solr98.beyondintegration.feature.feeder;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * 口渴后端统一桥：屏蔽新版「Thirst was Reclaimed」（cn.mlus）、旧版「Thirst was Taken」（dev.ghen）
 * 与 Legendary Survival Overhaul（sfiomn）差异。
 * <p>运行时选择：新版 Thirst → 旧版 Thirst → LSO → {@link #NOOP}。
 * 新旧两版 modId 均为 {@code thirst}，类名/方法签名一致、仅根包不同，故按“类是否存在”区分。
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
        ThirstBridge thirst = null;
        if (present("cn.mlus.thirst.api.ThirstHelper")) thirst = ThirstCompat.INSTANCE;
        else if (present("dev.ghen.thirst.api.ThirstHelper")) thirst = ThirstCompatLegacy.INSTANCE;

        boolean lso = ModList.get().isLoaded("legendarysurvivaloverhaul");
        // 同时存在多种口渴系统：组合后端同时驱动（是否分开给量由配置决定）
        if (thirst != null && lso) return new CompositeThirstBridge(thirst, LsoThirstBackend.INSTANCE);
        if (thirst != null) return thirst;
        if (lso) return LsoThirstBackend.INSTANCE;
        return NOOP;
    }

    /** 判断某类是否存在（仅加载、不初始化），用于区分同 modId 的新旧版 Thirst。 */
    static boolean present(String className)
    {
        try
        {
            Class.forName(className, false, ThirstBridge.class.getClassLoader());
            return true;
        }
        catch (Throwable t)
        {
            return false;
        }
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
