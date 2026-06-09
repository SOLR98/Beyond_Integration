package com.solr98.beyondintegration.jei;

import com.solr98.beyondintegration.BeyondIntegration;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class BDJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(BeyondIntegration.MODID, "jei_plugin");
    }
}
