package com.solr98.beyondintegration.jei;

import com.solr98.beyondintegration.feature.crafting.DimensionsCraftMenu;
import com.solr98.beyondintegration.init.ModMenus;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RecipeFillPacket;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 合成配方转移处理器（JEI 点击一键填充）：
 * 客户端聚合可用物品（合成格已有 + 网络存储 + 玩家背包）逐槽匹配 JEI 输入，
 * 生成 RecipeFillPacket 发送服务端填格；材料不足返回 COSMETIC 错误提示。
 */
public class CraftRecipeTransferHandler implements IRecipeTransferHandler<DimensionsCraftMenu, CraftingRecipe> {

    @Override
    public Class<? extends DimensionsCraftMenu> getContainerClass() {
        return DimensionsCraftMenu.class;
    }

    @Override
    public Optional<MenuType<DimensionsCraftMenu>> getMenuType() {
        return Optional.of(ModMenus.CRAFT.get());
    }

    @Override
    public RecipeType<CraftingRecipe> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    // 构建物品池并逐槽匹配：doTransfer 时发送填充包；任一输入缺失则返回 COSMETIC 提示
    @Override
    public @Nullable IRecipeTransferError transferRecipe(DimensionsCraftMenu menu, CraftingRecipe recipe,
                                                         IRecipeSlotsView slotsView, Player player,
                                                         boolean maxTransfer, boolean doTransfer) {
        // 构建可用物品池: 合成格已有物品 + 网络存储 + 玩家背包
        Map<Item, List<Avail>> pool = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            Slot s = menu.getSlot(i);
            if (s.hasItem()) {
                ItemStack stack = s.getItem();
                addPool(pool, new ItemStackKey(stack), stack.getCount());
            }
        }
        if (menu.clientNetStorage != null) {
            for (KeyAmount ka : menu.clientNetStorage.getStorage()) {
                if (ka == null || ka.isEmpty()) continue;
                if (ka.key() instanceof ItemStackKey isk)
                    addPool(pool, isk, ka.amount());
            }
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) addPool(pool, new ItemStackKey(stack), stack.getCount());
        }

        // 匹配每个 JEI 输入槽
        List<IStackKey<?>> outKeys = new ArrayList<>();
        List<Long> outAmts = new ArrayList<>();
        boolean missing = false;

        for (IRecipeSlotView slotView : slotsView.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<ItemStack> candidates = slotView.getIngredients(VanillaTypes.ITEM_STACK)
                    .filter(Objects::nonNull).filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            if (candidates.isEmpty()) {
                outKeys.add(EmptyStackKey.INSTANCE);
                outAmts.add(0L);
                continue;
            }
            long need = 1;
            boolean filled = false;
            for (ItemStack alt : candidates) {
                List<Avail> list = pool.get(alt.getItem());
                if (list == null || list.isEmpty()) continue;
                for (Avail a : list) {
                    if (a.remain <= 0) continue;
                    long take = Math.min(need, a.remain);
                    a.remain -= take;
                    outKeys.add(a.key);
                    outAmts.add(take);
                    filled = true;
                    break;
                }
                if (filled) break;
            }
            if (!filled) {
                missing = true;
                outKeys.add(EmptyStackKey.INSTANCE);
                outAmts.add(0L);
            }
        }

        if (doTransfer) {
            PacketHandler.sendToServer(new RecipeFillPacket(outKeys, outAmts));
        }

        if (missing) {
            return new IRecipeTransferError() {
                @Override public Type getType() { return Type.COSMETIC; }
            };
        }
        return null;
    }

    // 按物品（Item）归入可用池
    private static void addPool(Map<Item, List<Avail>> pool, ItemStackKey key, long count) {
        if (count <= 0) return;
        pool.computeIfAbsent(key.getSource(), k -> new ArrayList<>()).add(new Avail(key, count));
    }

    // 可用物品条目：网络/背包的 key + 剩余可用量（匹配时递减防重复占用）
    private static class Avail {
        final ItemStackKey key;
        long remain;
        Avail(ItemStackKey key, long remain) { this.key = key; this.remain = remain; }
    }
}
