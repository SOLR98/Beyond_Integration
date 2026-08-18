package com.solr98.beyondintegration.jade;

import com.wintercogs.beyonddimensions.common.block.NetedBlock;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import com.wintercogs.beyonddimensions.common.block.entity.NetPathwayBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 集成插件：注册与 BD 维度网络及 Superb Warfare 载具相关的 Jade 数据/组件提供器。
 * 负责服务端数据提供与客户端组件渲染的注册，载具部分仅在 superbwarfare 模组存在时反射注册。
 */
@WailaPlugin
public class JadeBDPlugin implements IWailaPlugin {

    /** 服务端注册：方块数据、流体存储；条件注册载具实体数据提供器。 */
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BDServerProvider.INSTANCE, NetedBlockEntity.class);
        registration.registerFluidStorage(BDFluidOverrideProvider.INSTANCE, NetPathwayBlockEntity.class);

        if (ModList.get().isLoaded("superbwarfare")) {
            try {
                Class<?> raw = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
                @SuppressWarnings("unchecked")
                Class<? extends Entity> vehicleClass = (Class<? extends Entity>) raw;
                registration.registerEntityDataProvider(VehicleServerProvider.INSTANCE, vehicleClass);
            } catch (Exception ignored) {}
        }
    }

    /** 客户端注册：方块组件、流体存储客户端渲染；条件注册载具实体组件提供器。 */
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BDClientProvider.INSTANCE, NetedBlock.class);
        registration.registerFluidStorageClient(BDFluidOverrideProvider.INSTANCE);

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
