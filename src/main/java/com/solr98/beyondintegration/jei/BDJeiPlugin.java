package com.solr98.beyondintegration.jei;
import com.solr98.beyondintegration.BeyondIntegration;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class BDJeiPlugin implements IModPlugin {
    // TODO: Implement JEI integration for Beyond Integration items/recipes
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.parse(BeyondIntegration.MODID + ":jei_plugin"); }
}
