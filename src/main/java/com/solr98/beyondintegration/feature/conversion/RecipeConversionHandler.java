package com.solr98.beyondintegration.feature.conversion;

import com.solr98.beyondintegration.feature.conversion.model.ConversionRecipe;
import com.solr98.beyondintegration.feature.extract.IExtractHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class RecipeConversionHandler implements IExtractHandler {

    @Override
    @Nullable
    public KeyAmount handleExtract(DimensionsNet net, UnifiedStorage storage, IStackKey<?> key, long amount, boolean simulate, boolean fuzzy) {
        if (!(key instanceof ItemStackKey itemKey)) return null;

        ResourceLocation requestId = BuiltInRegistries.ITEM.getKey(itemKey.getSource());
        if (requestId == null) return null;

        List<ConversionRecipe> recipes = ConversionLoader.getRecipesFor(requestId);
        if (recipes.isEmpty()) return null;

        for (ConversionRecipe recipe : recipes) {
            KeyAmount result = tryExecute(recipe, storage, amount, simulate, fuzzy);
            if (result != null) {
                if (net != null) net.setDirty();
                return result;
            }
        }

        return null;
    }

    @Nullable
    private KeyAmount tryExecute(ConversionRecipe recipe, UnifiedStorage storage, long amount, boolean simulate, boolean fuzzy) {
        int outputPerUnit = recipe.getOutput().getCount();
        long units = amount / outputPerUnit;
        if (units <= 0) return null;

        List<MatchResult> matches = new ArrayList<>();

        for (ConversionRecipe.Ingredient ing : recipe.getInput()) {
            long cost = ing.getCost();
            MatchResult match = findMatch(storage, ing, units * cost, fuzzy);
            if (match == null || match.available <= 0) return null;

            long possible = match.available / cost;
            if (possible < units) units = possible;
            if (units <= 0) return null;

            matches.add(match);
        }

        long totalProduced = units * outputPerUnit;
        if (totalProduced <= 0) return null;

        if (simulate) {
            return buildResult(recipe, totalProduced);
        }

        for (int i = 0; i < recipe.getInput().size(); i++) {
            ConversionRecipe.Ingredient ing = recipe.getInput().get(i);
            MatchResult match = matches.get(i);
            long needed = units * ing.getCost();
            storage.extract(match.key, needed, false, fuzzy);
        }

        for (ConversionRecipe.NetworkOutput netOut : recipe.getOutputNetwork()) {
            if (netOut.isItem()) {
                Item item = ForgeRegistries.ITEMS.getValue(netOut.getId());
                if (item != null) {
                    storage.insert(new ItemStackKey(new ItemStack(item, 1)), units * netOut.getCount(), false);
                }
            } else {
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(netOut.getId());
                if (fluid != null && fluid != Fluids.EMPTY) {
                    storage.insert(new FluidStackKey(new FluidStack(fluid, 1)), units * netOut.getAmount(), false);
                }
            }
        }

        return buildResult(recipe, totalProduced);
    }

    @Nullable
    private MatchResult findMatch(UnifiedStorage storage, ConversionRecipe.Ingredient ing, long needed, boolean fuzzy) {
        if (ing.isItem()) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(ing.getItem()));
            if (item == null) return null;
            ItemStackKey key = new ItemStackKey(new ItemStack(item, 1));
            KeyAmount result = storage.extract(key, needed, true, fuzzy);
            if (result.isEmpty() || result.amount() <= 0) return null;
            return new MatchResult(key, result.amount());
        }

        if (ing.isTag()) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.tryParse(ing.getTag()));
            var tagOpt = BuiltInRegistries.ITEM.getTag(tagKey);
            if (tagOpt.isEmpty()) return null;
            for (var holder : tagOpt.get()) {
                ItemStackKey key = new ItemStackKey(new ItemStack(holder.get(), 1));
                KeyAmount result = storage.extract(key, needed, true, fuzzy);
                if (!result.isEmpty() && result.amount() > 0) {
                    return new MatchResult(key, result.amount());
                }
            }
            return null;
        }

        if (ing.isFluid()) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(ResourceLocation.tryParse(ing.getFluid()));
            if (fluid == null || fluid == Fluids.EMPTY) return null;
            FluidStackKey key = new FluidStackKey(new FluidStack(fluid, 1));
            KeyAmount result = storage.extract(key, needed, true, false);
            if (result.isEmpty() || result.amount() <= 0) return null;
            return new MatchResult(key, result.amount());
        }

        return null;
    }

    private static KeyAmount buildResult(ConversionRecipe recipe, long totalProduced) {
        Item item = ForgeRegistries.ITEMS.getValue(recipe.getOutput().getId());
        if (item == null) return new KeyAmount(EmptyStackKey.INSTANCE, 0);
        return new KeyAmount(
                new ItemStackKey(new ItemStack(item, 1)),
                totalProduced
        );
    }

    private record MatchResult(IStackKey<?> key, long available) {}
}
