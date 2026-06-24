package com.solr98.beyondintegration.mixin.ywzj_vehicle;

import com.solr98.beyondintegration.handler.MenuNetIdHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.ywzj.vehicle.network.message.ClientMachineMaxAction;
import org.ywzj.vehicle.recipe.VehiclePrintingIngredient;
import org.ywzj.vehicle.recipe.VehiclePrintingRecipe;

import java.util.List;

@Mixin(ClientMachineMaxAction.class)
public class MachineMaxCraftMixin {

    @Inject(method = "hasIngredients", at = @At("HEAD"), cancellable = true)
    private static void beyond$checkNetworkForCraft(ServerPlayer player, VehiclePrintingRecipe recipe, CallbackInfoReturnable<Boolean> cir) {
        if (hasEnoughInInventory(player, recipe)) return;

        DimensionsNet net = findNetwork(player);
        if (net == null) return;

        for (VehiclePrintingIngredient input : recipe.getInputs()) {
            Ingredient ingredient = input.ingredient();
            int needed = input.count();
            for (ItemStack stack : ingredient.getItems()) {
                long available = net.getUnifiedStorage().getStackByKey(new ItemStackKey(stack)).amount();
                needed -= (int) Math.min(available, needed);
                if (needed <= 0) break;
            }
            if (needed > 0) return;
        }

        // Network has all materials, pull them into player inventory
        for (VehiclePrintingIngredient input : recipe.getInputs()) {
            Ingredient ingredient = input.ingredient();
            int needed = input.count();
            for (ItemStack stack : ingredient.getItems()) {
                if (needed <= 0) break;
                var key = new ItemStackKey(stack);
                long available = net.getUnifiedStorage().getStackByKey(key).amount();
                long extract = Math.min(available, needed);
                if (extract <= 0) continue;

                ItemStack pulled = stack.copyWithCount((int) extract);
                int beforeCount = pulled.getCount();
                player.getInventory().add(pulled);
                int consumed = beforeCount - pulled.getCount();
                if (consumed > 0) {
                    net.getUnifiedStorage().extract(key, consumed, false, false);
                    needed -= consumed;
                }
            }
        }

        net.setDirty();
        cir.setReturnValue(true);
    }

    private static boolean hasEnoughInInventory(ServerPlayer player, VehiclePrintingRecipe recipe) {
        List<ItemStack> inventoryCopy = player.getInventory().items.stream()
                .filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();

        for (VehiclePrintingIngredient input : recipe.getInputs()) {
            int needed = input.count();
            Ingredient ingredient = input.ingredient();
            for (ItemStack stack : inventoryCopy) {
                if (ingredient.test(stack)) {
                    int take = Math.min(stack.getCount(), needed);
                    stack.shrink(take);
                    needed -= take;
                }
                if (needed <= 0) break;
            }
            if (needed > 0) return false;
        }
        return true;
    }

    private static DimensionsNet findNetwork(ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net != null) return net;
        return MenuNetIdHelper.getNetFromMenu(player);
    }
}
