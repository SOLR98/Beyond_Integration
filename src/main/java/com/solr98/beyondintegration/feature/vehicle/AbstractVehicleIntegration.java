package com.solr98.beyondintegration.feature.vehicle;

import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Shared vehicle integration utilities used by both SuperbWarfare and YWZJ vehicle mixins.
 * <p>
 * Provides common operations for:
 * <ul>
 *   <li>Binding/unbinding vehicle UUIDs to BD network IDs via {@link VehicleNetStorage}</li>
 *   <li>Querying network energy and ammo counts</li>
 *   <li>Resolving network display names</li>
 * </ul>
 */
public abstract class AbstractVehicleIntegration {

    protected AbstractVehicleIntegration() {
    }

    /**
     * Bind a vehicle entity UUID to the given network ID.
     */
    public static void bind(java.util.UUID vehicleUuid, int netId) {
        VehicleNetStorage.bindVehicle(vehicleUuid, netId);
    }

    /**
     * Unbind a vehicle entity UUID from any network.
     */
    public static void unbind(java.util.UUID vehicleUuid) {
        VehicleNetStorage.unbindVehicle(vehicleUuid);
    }

    /**
     * Get the network ID bound to a vehicle UUID, or -1 if none.
     */
    public static int getBoundNetId(java.util.UUID vehicleUuid) {
        return VehicleNetStorage.getBoundNetId(vehicleUuid);
    }

    /**
     * Get the DimensionsNet for a bound vehicle, or null.
     */
    public static DimensionsNet getNetForVehicle(java.util.UUID vehicleUuid) {
        int netId = getBoundNetId(vehicleUuid);
        return netId >= 0 ? DimensionsNet.getNetFromId(netId) : null;
    }

    /**
     * Get the network name if available.
     */
    public static String getNetworkName(DimensionsNet net) {
        if (net instanceof com.solr98.beyondintegration.handler.NetworkNameProvider nnp) {
            String name = nnp.getCustomName();
            return name != null && !name.isEmpty() ? name : "";
        }
        return "";
    }

    /**
     * Get energy stored in the given network.
     */
    public static long getEnergy(DimensionsNet net) {
        return net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
    }

    /**
     * Count matching ammo items in the network for weapon reload.
     */
    public static long countAmmoInNetwork(DimensionsNet net, List<ItemStack> ammoStacks) {
        long total = 0;
        for (ItemStack stack : ammoStacks) {
            total += net.getUnifiedStorage().getStackByKey(new ItemStackKey(stack)).amount();
        }
        return total;
    }

    /**
     * Convert a fluid type to its resource location key for fuel matching.
     */
    public static ResourceLocation getFluidKey(net.minecraft.world.level.material.Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid);
    }
}
