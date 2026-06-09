package com.solr98.beyondintegration.jade;

import com.wintercogs.beyonddimensions.common.block.NetedBlock;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class JadeBDPlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BDServerProvider.INSTANCE, NetedBlockEntity.class);

        if (ModList.get().isLoaded("superbwarfare")) {
            try {
                Class<?> raw = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
                @SuppressWarnings("unchecked")
                Class<? extends Entity> vehicleClass = (Class<? extends Entity>) raw;
                registration.registerEntityDataProvider(VehicleServerProvider.INSTANCE, vehicleClass);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BDClientProvider.INSTANCE, NetedBlock.class);

        if (ModList.get().isLoaded("superbwarfare")) {
            try {
                Class<?> raw = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
                @SuppressWarnings("unchecked")
                Class<? extends Entity> vehicleClass = (Class<? extends Entity>) raw;
                registration.registerEntityComponent(VehicleClientProvider.INSTANCE, vehicleClass);
            } catch (Exception ignored) {}
        }
    }
}
