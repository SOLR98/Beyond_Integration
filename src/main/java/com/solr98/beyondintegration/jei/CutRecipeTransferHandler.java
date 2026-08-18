package com.solr98.beyondintegration.jei;

import com.solr98.beyondintegration.feature.crafting.DimensionsCutMenu;
import com.solr98.beyondintegration.init.ModMenus;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

// 切石转移：JEI 点击 → 选中对应配方（StonecutterMenu 风格）
/**
 * 切石配方转移处理器：JEI 点击后通过 clickMenuButton 选中对应配方（对齐原版切石机交互）。
 */
public class CutRecipeTransferHandler implements IRecipeTransferHandler<DimensionsCutMenu, StonecutterRecipe> {

    @Override
    public Class<? extends DimensionsCutMenu> getContainerClass() {
        return DimensionsCutMenu.class;
    }

    @Override
    public Optional<MenuType<DimensionsCutMenu>> getMenuType() {
        return Optional.of(ModMenus.CUT.get());
    }

    @Override
    public RecipeType<StonecutterRecipe> getRecipeType() {
        return RecipeTypes.STONECUTTING;
    }

    // doTransfer 时在配方列表中找到目标配方并调用菜单按钮选中
    @Override
    public @Nullable IRecipeTransferError transferRecipe(DimensionsCutMenu menu, StonecutterRecipe recipe,
                                                         IRecipeSlotsView slotsView, Player player,
                                                         boolean maxTransfer, boolean doTransfer) {
        if (doTransfer) {
            List<StonecutterRecipe> recipes = menu.getRecipes();
            int idx = recipes.indexOf(recipe);
            if (idx >= 0) menu.clickMenuButton(player, idx);
        }
        return null;
    }
}
