package com.solr98.beyondintegration.feature.feeder;

import com.solr98.beyondintegration.CommandConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 组合口渴后端（1.21.1）：当同时存在多种口渴系统（如 Thirst 与 Legendary Survival Overhaul）时驱动全部后端。
 * <ul>
 *   <li>触发判定取各后端最低值（任一低于档位阈值即补水）。</li>
 *   <li>{@code feeder_thirst.separate_hydration=true}：一次补水对每个后端各给完整配置量（量分开）；</li>
 *   <li>false（默认）：只给一份量，补到当前最缺的后端。</li>
 * </ul>
 */
public final class CompositeThirstBridge implements ThirstBridge {

    private final ThirstBridge[] backends;

    public CompositeThirstBridge(ThirstBridge... backends) {
        this.backends = backends;
    }

    @Override
    public boolean loaded() {
        return true;
    }

    @Override
    public List<ThirstBridge> backends() {
        return List.of(backends);
    }

    @Override
    public int primary(Player player) {
        int min = Integer.MAX_VALUE;
        for (ThirstBridge b : backends) {
            min = Math.min(min, b.primary(player));
        }
        return min == Integer.MAX_VALUE ? 20 : min;
    }

    @Override
    public int secondary(Player player) {
        int min = Integer.MAX_VALUE;
        for (ThirstBridge b : backends) {
            min = Math.min(min, b.secondary(player));
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    @Override
    public void add(Player player, int primary, int secondary, int purity) {
        if (backends.length == 0) {
            return;
        }
        if (CommandConfig.feederThirstSeparateHydration()) {
            for (ThirstBridge b : backends) {
                b.add(player, primary, secondary, purity);
            }
            return;
        }
        // 不分开：一份量在两套之间平摊，保证两套系统都获得补水
        int n = backends.length;
        int sharePrimary = Math.max(1, Math.round((float) primary / n));
        int shareSecondary = Math.round((float) secondary / n);
        for (ThirstBridge b : backends) {
            b.add(player, sharePrimary, shareSecondary, purity);
        }
    }

    @Override
    public boolean hydratesViaEat() {
        // 所有后端在“食物/使用完成”路径上都会自行补水
        // （Thirst 经 Player.eat，LSO 经 LivingEntityUseItemEvent.Finish），故返回 true，
        // 食物分支不再显式补，避免与后端自身的自动补水重复。
        return true;
    }

    @Override
    public boolean restoresThirst(ItemStack stack) {
        for (ThirstBridge b : backends) {
            if (b.restoresThirst(stack)) return true;
        }
        return false;
    }

    @Override
    public void drinkItem(ItemStack stack, Player player) {
        // 纯饮料分支：仅对“识别该物品且不会在 finishUsingItem 时自行补水”的后端显式补水。
        for (ThirstBridge b : backends) {
            if (b.restoresThirst(stack) && !b.drinksOnFinish(stack.getItem())) {
                b.drinkItem(stack, player);
            }
        }
    }

    @Override
    public boolean drinksOnFinish(Item item) {
        for (ThirstBridge b : backends) {
            if (!b.drinksOnFinish(item)) return false;
        }
        return true;
    }

    @Override
    public void tagPurity(ItemStack stack, int purity) {
        for (ThirstBridge b : backends) {
            b.tagPurity(stack, purity);
        }
    }

    @Override
    public int readPurity(ItemStack stack) {
        for (ThirstBridge b : backends) {
            int p = b.readPurity(stack);
            if (p >= 0) return p;
        }
        return -1;
    }
}
