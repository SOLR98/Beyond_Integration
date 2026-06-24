package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.Entity;

/**
 * Abstraction for vehicle energy systems that can receive energy from a BD network.
 * <p>
 * Separates vehicle-specific energy I/O from the charging strategy logic.
 * Implement this for each vehicle type:
 * <ul>
 *   <li>SuperbWarfare {@code VehicleEntity} — energy is stored as an integer FE value</li>
 *   <li>YWZJ {@code AbstractVehicle} — energy is a float fuel value with fluid-based refueling</li>
 * </ul>
 */
public interface IVehicleEnergySystem {

    /** Maximum energy capacity of this vehicle. */
    float getMaxEnergy();

    /** Current energy stored in this vehicle. */
    float getCurrentEnergy();

    /** Add energy to the vehicle. Returns the amount actually added (0 to capacity limit). */
    float addEnergy(float amount);

    /** Get the entity representation of this vehicle. */
    Entity getVehicleEntity();

    /** Get the network ID this vehicle is bound to, or -1 if unbound. */
    int getBoundNetId();

    /** Get the DimensionsNet bound to this vehicle, or null. */
    default DimensionsNet getBoundNetwork() {
        int id = getBoundNetId();
        return id >= 0 ? DimensionsNet.getNetFromId(id) : null;
    }
}
