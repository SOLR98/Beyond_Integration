package com.solr98.beyondintegration.config;

import com.solr98.beyondintegration.CommandConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

public final class VehicleConfig {

    private static Fields fields;

    private VehicleConfig() {}

    public static void build(ModConfigSpec.Builder builder) {
        builder.comment("Superb Warfare 载具配置").push("vehicle");
        fields = new Fields(builder);
        builder.pop();

        builder.comment("Limitless Vehicle 载具配置").push("ywzj_vehicle");
        fields.initYwzj(builder);
        builder.pop();
    }

    public static class Fields {
        public ModConfigSpec.EnumValue<CommandConfig.SWChargeMode> swChargeMode;
        public ModConfigSpec.IntValue swEnergyChargeRate;
        public ModConfigSpec.IntValue swChargeInterval;
        public ModConfigSpec.DoubleValue swChargePercentage;

        public ModConfigSpec.EnumValue<CommandConfig.ChargeMode> ywzjChargeMode;
        public ModConfigSpec.EnumValue<CommandConfig.FuelSource> ywzjFuelSource;
        public ModConfigSpec.IntValue ywzjEnergyChargeRate;
        public ModConfigSpec.IntValue ywzjChargeInterval;
        public ModConfigSpec.DoubleValue ywzjChargePercentage;
        public ModConfigSpec.IntValue ywzjEnergyConversion;
        public ModConfigSpec.ConfigValue<List<? extends String>> ywzjAllowedEnergyTypes;

        Fields(ModConfigSpec.Builder builder) {
            swChargeMode = builder.defineEnum("chargeMode", CommandConfig.SWChargeMode.SUM);
            swEnergyChargeRate = builder.defineInRange("energyChargeRate", 500000, 0, Integer.MAX_VALUE);
            swChargeInterval = builder.defineInRange("chargeInterval", 20, 1, 1200);
            swChargePercentage = builder.defineInRange("chargePercentage", 50.0, 0.0, 100.0);
        }

        void initYwzj(ModConfigSpec.Builder builder) {
            ywzjChargeMode = builder.defineEnum("chargeMode", CommandConfig.ChargeMode.FLAT_RATE);
            ywzjFuelSource = builder.defineEnum("fuelSource", CommandConfig.FuelSource.FE);
            ywzjEnergyChargeRate = builder.defineInRange("energyChargeRate", 500000, 0, Integer.MAX_VALUE);
            ywzjChargeInterval = builder.defineInRange("chargeInterval", 20, 1, 1200);
            ywzjChargePercentage = builder.defineInRange("chargePercentage", 5.0, 0.1, 100.0);
            ywzjEnergyConversion = builder.defineInRange("energyConversion", 1000, 1, Integer.MAX_VALUE);
            ywzjAllowedEnergyTypes = builder.defineList("allowedEnergyTypes",
                    Arrays.asList("fuel", "steam", "electric"), obj -> obj instanceof String);
        }
    }

    public static Fields fields() { return fields; }

    public static CommandConfig.SWChargeMode swChargeMode() { return fields.swChargeMode.get(); }
    public static int swEnergyChargeRate() { return fields.swEnergyChargeRate.get(); }
    public static int swChargeInterval() { return fields.swChargeInterval.get(); }
    public static double swChargePercentage() { return fields.swChargePercentage.get(); }

    public static CommandConfig.ChargeMode ywzjChargeMode() { return fields.ywzjChargeMode.get(); }
    public static CommandConfig.FuelSource ywzjFuelSource() { return fields.ywzjFuelSource.get(); }
    public static int ywzjEnergyChargeRate() { return fields.ywzjEnergyChargeRate.get(); }
    public static int ywzjChargeInterval() { return fields.ywzjChargeInterval.get(); }
    public static double ywzjChargePercentage() { return fields.ywzjChargePercentage.get(); }
    public static int ywzjEnergyConversion() { return fields.ywzjEnergyConversion.get(); }
    public static List<? extends String> ywzjAllowedEnergyTypes() { return fields.ywzjAllowedEnergyTypes.get(); }
}
