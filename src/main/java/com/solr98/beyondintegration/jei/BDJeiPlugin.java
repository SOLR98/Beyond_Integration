package com.solr98.beyondintegration.jei;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.crafting.DimensionsCraftMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class BDJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.tryParse(BeyondIntegration.MODID + ":jei_plugin");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new CraftRecipeTransferHandler(), RecipeTypes.CRAFTING);
    }
}
