package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.Entity;

public interface IVehicleEnergySystem {

    float getMaxEnergy();

    float getCurrentEnergy();

    float addEnergy(float amount);

    Entity getVehicleEntity();

    int getBoundNetId();

    default DimensionsNet getBoundNetwork() {
        int id = getBoundNetId();
        return id >= 0 ? DimensionsNet.getNetFromId(id) : null;
    }
}
