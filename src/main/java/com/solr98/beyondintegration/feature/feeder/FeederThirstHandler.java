package com.solr98.beyondintegration.feature.feeder;

import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.event.EventHooks;

import com.solr98.beyondintegration.handler.MachineSettingsHelper;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.handler.BucketFluidHelper;
import com.solr98.beyondintegration.init.ModFluids;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.item.BaseMachineItem;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络喂食器的完整逻辑（由 Mixin {@code @Overwrite} 直接替换 BD 原 {@code workContent}）。
 * <p>喂食与补水都按<b>四项阈值</b>判定（见 {@link FeederThirstSettings}）：
 * <ul>
 *   <li>喂食：饱食度 ≤ 阈值，或饥饿饱和度 ≤ 阈值；</li>
 *   <li>补水：口渴 ≤ 阈值，或水饱和 ≤ 阈值；优先标记槽内的补水物品，其次网络流体水（尊重流体标记）。</li>
 * </ul>
 * <p>注意：把水饱和/饥饿饱和度阈值调高会让口渴与饱食度长期贴在上限（因为补充会同时抬主值）。
 */
public final class FeederThirstHandler
{
    private FeederThirstHandler() {}

    /** 接管整段喂食逻辑（服务端调用） */
    public static void handle(ItemStack stack, Level level, Entity holder)
    {
        if (!(holder instanceof Player player)) return;

        ThirstBridge bridge = ThirstBridge.get();
        if (!bridge.loaded())
        {
            feedFood(stack, level, player);
            return;
        }

        // 1) 先喂食物（按饱食度 / 饥饿饱和度阈值）
        feedFood(stack, level, player);

        // 2) 再补水：按顺序（Thirst → LSO）逐个后端“检测→补水”
        int thirstThreshold = FeederThirstSettings.thirstThreshold(stack);
        int quenchedThreshold = FeederThirstSettings.quenchedThreshold(stack);
        var net = NetedItem.getNet(stack);
        if (net != null)
        {
            UnifiedStorage storage = net.getUnifiedStorage();
            List<KeyAmount> filterSlots = com.solr98.beyondintegration.core.util.BdItemData.filterSlots(stack, new ArrayList<>());
            List<ThirstBridge> backends = bridge.backends();
            for (ThirstBridge b : backends)
            {
                if (!FeederThirstSettings.shouldDrink(b.primary(player), b.secondary(player),
                        thirstThreshold, quenchedThreshold)) continue;
                KeyAmount candidate = findCandidate(b, storage, filterSlots);
                if (candidate != null) consume(b, storage, candidate, player, level);
                else tryDrinkFluid(storage, filterSlots, player, b, level, backends.size());
            }
        }
    }

    // ─────────────────────────── 喂食逻辑 ───────────────────────────

    /** 按饱食度 / 饥饿饱和度阈值决定是否喂食。 */
    private static void feedFood(ItemStack stack, Level level, Player player)
    {
        List<KeyAmount> filterSlots = com.solr98.beyondintegration.core.util.BdItemData.filterSlots(stack, new ArrayList<>());
        FoodData data = player.getFoodData();
        int foodThreshold = FeederThirstSettings.foodThreshold(stack);
        int saturationThreshold = FeederThirstSettings.saturationThreshold(stack);
        if (!FeederThirstSettings.shouldEat(data.getFoodLevel(), data.getSaturationLevel(),
                foodThreshold, saturationThreshold))
        {
            return;
        }

        var net = NetedItem.getNet(stack);
        if (net == null) return;
        UnifiedStorage storage = net.getUnifiedStorage();

        KeyAmount foodCache = null;
        boolean hungerFull = data.getFoodLevel() >= 20;
        for (KeyAmount filter : filterSlots)
        {
            if (filter == null || filter.isEmpty() || !(filter.key() instanceof ItemStackKey fk)) continue;
            for (KeyAmount stored : storage.getStorage())
            {
                if (!(stored.key() instanceof ItemStackKey sk) || !sk.isSame(fk)) continue;
                ItemStack candidate = sk.getReadOnlyStack();
                if (candidate.get(DataComponents.FOOD) == null) continue;
                // 饱食度已满时，仅接受能补水的物品，避免无谓进食（把机会让给补水/流体）
                if (hungerFull && !ThirstBridge.get().restoresThirst(candidate)) continue;
                foodCache = new KeyAmount(stored.key(), 1L);
                break;
            }
            if (foodCache != null) break;
        }
        if (foodCache == null) return;

        KeyAmount extracted = storage.extract(foodCache.key(), 1L, false, false);
        if (extracted.isEmpty() || !(extracted.key() instanceof ItemStackKey key)) return;

        ItemStack foodStack = key.copyStackWithCount(1L);
        Item foodItem = foodStack.getItem();
        FoodProperties fp = foodStack.get(DataComponents.FOOD);
        if (fp != null && fp.nutrition() > 0)
        {
            ItemStack remaining = EventHooks.onItemUseFinish(player, foodStack.copy(), 0,
                    foodItem.finishUsingItem(foodStack, level, player));
            if (!remaining.isEmpty())
            {
                KeyAmount leftover = storage.insert(new ItemStackKey(remaining), remaining.getCount(), false);
                if (!leftover.isEmpty()) player.drop((ItemStack) leftover.toStack(), false);
            }
            return;
        }
        // 未能食用则插回
        storage.insert(extracted.key(), extracted.amount(), false);
    }

    // ─────────────────────────── 补水逻辑 ───────────────────────────

    /**
     * 在标记槽位允许的范围内，寻找网络中的第一个补水物品。
     * <p>接受两类：后端认定能补水的物品（Thirst 名单 / LSO 消耗品数据），
     * 以及**含可饮用水的容器**（水桶 / 水瓶 / 模组罐）——后者后端通常不认，由本模组按流体处理。
     */
    @Nullable
    private static KeyAmount findCandidate(ThirstBridge bridge, UnifiedStorage storage, List<KeyAmount> filterSlots)
    {
        if (filterSlots == null || filterSlots.isEmpty()) return null;
        for (KeyAmount filter : filterSlots)
        {
            if (filter == null || filter.isEmpty() || !(filter.key() instanceof ItemStackKey fk) || fk.isEmpty())
                continue;
            for (KeyAmount stored : storage.getStorage())
            {
                if (!(stored.key() instanceof ItemStackKey sk) || !sk.isSame(fk)) continue;
                ItemStack candidate = sk.getReadOnlyStack();
                if (bridge.restoresThirst(candidate) || isDrinkableContainer(candidate))
                {
                    return new KeyAmount(stored.key(), 1L);
                }
            }
        }
        return null;
    }

    /** 可饮用的含水容器（水桶 / 水瓶 / 模组罐）：容器内有流体，且流体是本模组可识别的饮用水。 */
    private static boolean isDrinkableContainer(ItemStack stack)
    {
        FluidStack fluid = BucketFluidHelper.getContainedFluid(stack);
        if (fluid.isEmpty()) return false;
        return drinkablePurity(fluid.getFluid()) >= 0;
    }

    /** 饮用水纯度：本模组 4 档水返回 0~3；原版水按配置；其它返回 -1（不可饮）。 */
    private static int drinkablePurity(Fluid fluid)
    {
        int purity = ModFluids.purityOf(fluid);
        if (purity < 0 && fluid == Fluids.WATER) purity = CommandConfig.feederThirstVanillaWaterPurity();
        return purity;
    }

    /**
     * 喝掉一个含水容器：按容器容量 {@code mB / mbPerUse} 份折算补水，随后返还空容器。
     * <p>不走后端判定（后端通常不认水桶），直接复用流体的「每份补多少」配置。
     */
    private static void consumeContainer(ThirstBridge bridge, UnifiedStorage storage,
                                         KeyAmount candidate, Player player)
    {
        KeyAmount extracted = storage.extract(candidate.key(), 1L, false, false);
        if (extracted.isEmpty() || !(extracted.key() instanceof ItemStackKey key)) return;

        ItemStack container = key.copyStackWithCount(1L);
        FluidStack fluid = BucketFluidHelper.getContainedFluid(container);
        int purity = fluid.isEmpty() ? -1 : drinkablePurity(fluid.getFluid());
        if (fluid.isEmpty() || purity < 0)
        {
            storage.insert(key, 1L, false); // 识别失败：放回
            return;
        }

        // 一次喝掉整个容器：按容量折算份数（桶 1000 mB、瓶 250 mB…）
        int mbPerUse = Math.max(1, CommandConfig.feederThirstMbPerUse());
        long doses = Math.max(1L, fluid.getAmount() / mbPerUse);
        int addThirst = (int) Math.min(Integer.MAX_VALUE,
                Math.max(1, CommandConfig.feederThirstThirstPerUse(purity)) * doses);
        int addQuenched = (int) Math.min(Integer.MAX_VALUE,
                Math.max(0, CommandConfig.feederThirstQuenchedPerUse(purity)) * doses);
        bridge.add(player, addThirst, addQuenched, purity);

        // 返还空容器（原版桶回退为空桶；无剩余物则不返还）
        ItemStack empty = BucketFluidHelper.emptyContainerOf(container);
        if (!empty.isEmpty())
        {
            KeyAmount leftover = storage.insert(new ItemStackKey(empty), empty.getCount(), false);
            if (!leftover.isEmpty()) player.drop((ItemStack) leftover.toStack(), false);
        }
    }

    private static void consume(ThirstBridge bridge, UnifiedStorage storage, KeyAmount candidate, Player player, Level level)
    {
        // 含水容器（水桶/水瓶/模组罐）：后端不认，按流体折算补水并返还空容器
        ItemStack probe = candidate.key() instanceof ItemStackKey pk ? pk.getReadOnlyStack() : ItemStack.EMPTY;
        if (!probe.isEmpty() && !bridge.restoresThirst(probe) && isDrinkableContainer(probe))
        {
            consumeContainer(bridge, storage, candidate, player);
            return;
        }

        KeyAmount extracted = storage.extract(candidate.key(), 1L, false, false);
        if (extracted.isEmpty() || !(extracted.key() instanceof ItemStackKey key)) return;

        ItemStack itemStack = key.copyStackWithCount(1L);
        if (itemStack.isEmpty()) return;

        Item item = itemStack.getItem();
        FoodProperties food = itemStack.get(DataComponents.FOOD);
        ItemStack remaining;

        if (food != null)
        {
            // 食物：Thirst 经 Player.eat 混入自动补水；LSO 需显式补
            if (!bridge.hydratesViaEat() && bridge.restoresThirst(itemStack))
            {
                bridge.drinkItem(itemStack.copyWithCount(1), player);
            }
            remaining = EventHooks.onItemUseFinish(player, itemStack.copy(), 0,
                    item.finishUsingItem(itemStack, level, player));
        }
        else
        {
            // 纯饮料：先补水，再应用效果/产出容器
            if (!bridge.drinksOnFinish(item)) bridge.drinkItem(itemStack, player);
            ItemStack result = item.finishUsingItem(itemStack, level, player);
            if (ItemStack.isSameItemSameComponents(result, itemStack) && result.getCount() >= itemStack.getCount())
            {
                remaining = ItemStack.EMPTY; // 未消耗：强制视为已饮用
            }
            else
            {
                remaining = result;
            }
        }

        if (!remaining.isEmpty())
        {
            KeyAmount leftover = storage.insert(new ItemStackKey(remaining), remaining.getCount(), false);
            if (!leftover.isEmpty()) player.drop((ItemStack) leftover.toStack(), false);
        }
    }

    /** 从网络流体取水补水：每 {@code mbPerPoint} mB 恢复 1 点口渴，并按纯度施加效果 */
    private static void tryDrinkFluid(UnifiedStorage storage, List<KeyAmount> filterSlots,
                                      Player player, ThirstBridge bridge, Level level, int backendCount)
    {
        for (KeyAmount stored : storage.getStorage())
        {
            if (!(stored.key() instanceof FluidStackKey fluidKey)) continue;
            // 只使用标记槽中标记过的流体
            if (!isFluidMarked(filterSlots, fluidKey)) continue;

            Fluid fluid = fluidKey.getSource();
            int purity = ModFluids.purityOf(fluid);
            if (purity < 0)
            {
                if (fluid == Fluids.WATER) purity = CommandConfig.feederThirstVanillaWaterPurity();
                else continue;
            }

            // 每 mbPerUse mB 水补一次；每次加 thirstPerUse 口渴、quenchedPerUse 水饱和
            int mbPerUse = Math.max(1, CommandConfig.feederThirstMbPerUse());
            if (stored.amount() < mbPerUse) continue;

            KeyAmount extracted = storage.extract(fluidKey, mbPerUse, false, false);
            if (extracted.isEmpty()) continue;

            int addThirst = Math.max(1, CommandConfig.feederThirstThirstPerUse(purity));
            int addQuenched = CommandConfig.feederThirstQuenchedPerUse(purity);
            // 不分开补充时，一份量在所有后端之间平摊
            if (backendCount > 1 && !CommandConfig.feederThirstSeparateHydration())
            {
                addThirst = Math.max(1, Math.round((float) addThirst / backendCount));
                addQuenched = Math.round((float) addQuenched / backendCount);
            }

            bridge.add(player, addThirst, addQuenched, purity);
            return;
        }
    }

    /** 该流体是否被标记槽中的某个流体标记匹配 */
    private static boolean isFluidMarked(List<KeyAmount> filterSlots, FluidStackKey fluidKey)
    {
        for (KeyAmount f : filterSlots)
        {
            if (f != null && f.key() instanceof FluidStackKey mark && mark.isSame(fluidKey)) return true;
        }
        return false;
    }
}
