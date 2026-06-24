package com.solr98.beyondintegration.feature.vehicle;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Strategy-based energy charging for vehicles from BD network storage.
 * <p>
 * Two entry points handle the two supported vehicle mods:
 * <ul>
 *   <li>{@link #chargeSWFromNetwork} — SuperbWarfare vehicles, uses {@code SWChargeMode} with FE energy</li>
 *   <li>{@link #chargeYwzjFromNetwork} — YWZJ vehicles, uses {@code ChargeMode} with configurable FE/fluid fuel source</li>
 * </ul>
 * Each method accepts a {@link ChargeContext} for vehicle-specific energy I/O.
 */
public final class EnergyChargeStrategy {

    private static final long FLUID_MB_PER_FUEL_UNIT = 1000;

    private EnergyChargeStrategy() {
        throw new AssertionError("No instances");
    }

    public interface ChargeContext {
        float getMaxEnergy();
        float getCurrentEnergy();
        float addEnergy(float amount);
    }

    public interface FluidValidator {
        boolean isValidFuel(ResourceLocation fluidId);
    }

    /**
     * Charge a SuperbWarfare vehicle from its bound network using SWChargeMode.
     * Always uses FE energy from the network.
     */
    public static float chargeSWFromNetwork(ChargeContext ctx, DimensionsNet net,
                                             CommandConfig.SWChargeMode mode,
                                             double chargePercentage, int energyChargeRate) {
        float maxEnergy = ctx.getMaxEnergy();
        float currentEnergy = ctx.getCurrentEnergy();
        float space = maxEnergy - currentEnergy;
        if (space <= 0) return 0;

        float needed;
        switch (mode) {
            case PERCENTAGE:
                needed = (float) (maxEnergy * chargePercentage / 100.0) - currentEnergy;
                if (needed <= 0) return 0;
                break;
            case ABSOLUTE:
                needed = energyChargeRate;
                break;
            case SUM:
                needed = (float) (maxEnergy * chargePercentage / 100.0
                        - currentEnergy + energyChargeRate);
                if (needed <= 0) return 0;
                needed = Math.min(needed, space);
                break;
            default:
                return 0;
        }
        needed = Math.min(needed, space);
        if (needed <= 0) return 0;

        UnifiedStorage storage = net.getUnifiedStorage();
        if (storage == null) return 0;
        long extracted = storage.extract(
                EnergyStackKey.INSTANCE, (long) needed, false, false).amount();
        if (extracted > 0) {
            return ctx.addEnergy(extracted);
        }
        return 0;
    }

    /**
     * Charge a YWZJ vehicle from its bound network.
     * Supports FE-to-fuel conversion and direct fluid extraction.
     */
    public static float chargeYwzjFromNetwork(ChargeContext ctx, DimensionsNet net,
                                               CommandConfig.ChargeMode mode,
                                               CommandConfig.FuelSource fuelSource,
                                               double chargePercentage, int energyChargeRate,
                                               int energyConversion,
                                               FluidValidator fluidValidator) {
        float maxEnergy = ctx.getMaxEnergy();
        float currentEnergy = ctx.getCurrentEnergy();
        float space = maxEnergy - currentEnergy;
        if (space <= 0) return 0;

        if (mode == CommandConfig.ChargeMode.OFF) return 0;

        float needed;
        switch (mode) {
            case PERCENTAGE:
                needed = (float) (maxEnergy * chargePercentage / 100.0);
                break;
            case FLAT_RATE:
                needed = energyChargeRate;
                break;
            default:
                return 0;
        }
        needed = Math.min(needed, space);
        if (needed <= 0) return 0;

        UnifiedStorage storage = net.getUnifiedStorage();
        if (storage == null) return 0;

        switch (fuelSource) {
            case FE: {
                long feNeeded = (long) (needed * energyConversion);
                long extracted = storage.extract(
                        EnergyStackKey.INSTANCE, feNeeded, false, false).amount();
                if (extracted > 0) {
                    return ctx.addEnergy((float) extracted / energyConversion);
                }
                break;
            }
            case FLUID: {
                var bucketOpt = storage.getBucket(FluidStackKey.ID);
                if (bucketOpt.isEmpty()) break;
                var bucket = bucketOpt.get();
                long mbNeeded = (long) (needed * FLUID_MB_PER_FUEL_UNIT);
                for (int i = 0; i < bucket.size(); i++) {
                    var raw = bucket.get(i);
                    if (!(raw instanceof FluidStackKey fluidKey)) continue;
                    var fluid = fluidKey.getSource();
                    if (!fluid.isSource(fluid.defaultFluidState())) continue;
                    ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid);
                    if (!fluidValidator.isValidFuel(key)) continue;
                    long extracted = storage.extract(
                            new FluidStackKey(new FluidStack(fluid, 1)), mbNeeded, false, false).amount();
                    if (extracted > 0) {
                        return ctx.addEnergy((float) extracted / FLUID_MB_PER_FUEL_UNIT);
                    }
                }
                break;
            }
        }
        return 0;
    }
}
