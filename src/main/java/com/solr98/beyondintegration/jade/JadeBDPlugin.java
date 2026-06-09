package com.solr98.beyondintegration.jade;

import com.wintercogs.beyonddimensions.common.block.NetedBlock;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class JadeBDPlugin implements IWailaPlugin {

    private void registerVehicleProviders(IWailaCommonRegistration registration, String className) {
        try {
            Class<? extends Entity> clazz = Class.forName(className).asSubclass(Entity.class);
            registration.registerEntityDataProvider(VehicleServerProvider.INSTANCE, clazz);
        } catch (ClassNotFoundException ignored) {}
    }

    private void registerVehicleProvidersClient(IWailaClientRegistration registration, String className) {
        try {
            Class<? extends Entity> clazz = Class.forName(className).asSubclass(Entity.class);
            registration.registerEntityComponent(VehicleClientProvider.INSTANCE, clazz);
        } catch (ClassNotFoundException ignored) {}
    }

    @Override
    public void register(IWailaCommonRegistration registration) {
        if (ModList.get().isLoaded("superbwarfare")) {
            registerVehicleProviders(registration, "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            try {
                Class<?> containerBeClass = Class.forName("com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity")
                        .asSubclass(net.minecraft.world.level.block.entity.BlockEntity.class);
                registration.registerBlockDataProvider(ContainerServerProvider.INSTANCE, containerBeClass);
            } catch (ClassNotFoundException ignored) {}
        }
        if (ModList.get().isLoaded("ywzj_vehicle")) {
            registerVehicleProviders(registration, "org.ywzj.vehicle.entity.vehicle.AbstractVehicle");
        }

        registration.registerBlockDataProvider(BlockServerProvider.INSTANCE, NetedBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        if (ModList.get().isLoaded("superbwarfare")) {
            registerVehicleProvidersClient(registration, "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            try {
                Class<? extends Block> containerBlockClass = Class.forName("com.atsuishio.superbwarfare.block.ContainerBlock")
                        .asSubclass(Block.class);
                registration.registerBlockComponent(ContainerClientProvider.INSTANCE, containerBlockClass);
            } catch (ClassNotFoundException ignored) {}
        }
        if (ModList.get().isLoaded("ywzj_vehicle")) {
            registerVehicleProvidersClient(registration, "org.ywzj.vehicle.entity.vehicle.AbstractVehicle");
        }

        registration.registerBlockComponent(BlockClientProvider.INSTANCE, NetedBlock.class);
    }
}
