package com.solr98.beyondintegration;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import java.util.Arrays;
import java.util.List;

public class CommandConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ServerConfig SERVER;
    static {
        final Pair<ServerConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public enum Language { EN_US("en_us"), ZH_CN("zh_cn");
        private final String code;
        Language(String code) { this.code = code; }
        public String getCode() { return code; }
    }

    public enum ChargeMode { OFF, FLAT_RATE, PERCENTAGE }
    public enum FuelSource { FE, FLUID }

    public static class ServerConfig {
        public final ModConfigSpec.EnumValue<Language> language;
        public final ModConfigSpec.IntValue maxNetworksPerPage;

        // Enchantment separation
        public final ModConfigSpec.BooleanValue enchantSeparation;
        public final ModConfigSpec.BooleanValue enchantItemSeparation;
        public final ModConfigSpec.DoubleValue enchantItemMult;
        public final ModConfigSpec.IntValue enchantBaseCost;
        public final ModConfigSpec.DoubleValue enchantLevelMult;
        public final ModConfigSpec.DoubleValue enchantDefaultMult;
        public final ModConfigSpec.ConfigValue<List<? extends String>> enchantHighCostList;

        // Vehicle energy charge
        public final ModConfigSpec.IntValue swVehicleEnergyChargeRate;
        public final ModConfigSpec.IntValue swVehicleChargeInterval;
        public final ModConfigSpec.DoubleValue swVehicleChargePercentage;

        // Ywzj vehicle energy charge
        public final ModConfigSpec.EnumValue<ChargeMode> ywzjChargeMode;
        public final ModConfigSpec.EnumValue<FuelSource> ywzjFuelSource;
        public final ModConfigSpec.IntValue ywzjVehicleEnergyChargeRate;
        public final ModConfigSpec.IntValue ywzjVehicleChargeInterval;
        public final ModConfigSpec.DoubleValue ywzjVehicleChargePercentage;
        public final ModConfigSpec.IntValue ywzjVehicleEnergyConversion;
        public final ModConfigSpec.ConfigValue<List<? extends String>> ywzjAllowedEnergyTypes;

        // Item blacklist
        public final ModConfigSpec.BooleanValue ENABLE_ITEM_BLACKLIST;
        public final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;

        public ServerConfig(ModConfigSpec.Builder builder) {
            builder.comment("General settings").push("general");
            language = builder.defineEnum("command_language", Language.EN_US);
            maxNetworksPerPage = builder.defineInRange("max_networks_per_page", 10, 1, 100);
            builder.pop();

            builder.comment("Enchantment separation settings").push("enchant");
            enchantSeparation = builder.define("separation", true);
            enchantItemSeparation = builder.define("itemSeparation", true);
            enchantItemMult = builder.defineInRange("itemMult", 2.0, 0.1, 100.0);
            enchantBaseCost = builder.defineInRange("base_cost", 5, 0, 100);
            enchantLevelMult = builder.defineInRange("level_mult", 1.0, 0.0, 100.0);
            enchantDefaultMult = builder.defineInRange("default_mult", 1.0, 0.0, 100.0);
            enchantHighCostList = builder.defineList("high_cost",
                    Arrays.asList("minecraft:mending:3.0", "minecraft:frost_walker:3.0",
                            "minecraft:sharpness:1.2", "minecraft:protection:1.2"),
                    obj -> obj instanceof String);
            builder.pop();

            builder.comment("Vehicle settings").push("vehicle");
            swVehicleEnergyChargeRate = builder.defineInRange("energyChargeRate", 500000, 0, Integer.MAX_VALUE);
            swVehicleChargeInterval = builder.defineInRange("chargeInterval", 20, 1, 1200);
            swVehicleChargePercentage = builder.defineInRange("chargePercentage", 0.0, 0.0, 100.0);
            builder.comment("When chargePercentage > 0, charge that percentage of missing energy per interval (overrides energyChargeRate). Set to 0 to use flat FE rate mode.");
            builder.pop();

            builder.push("ywzj_vehicle");
            ywzjChargeMode = builder.comment("Charge mode: OFF (disabled), FLAT_RATE (fixed FE/interval), PERCENTAGE (% of missing energy)").defineEnum("chargeMode", ChargeMode.FLAT_RATE);
            ywzjFuelSource = builder.comment("Fuel source: FE (convert FE from network to fuel), FLUID (extract fluids matching fuelNameWhiteList from network)").defineEnum("fuelSource", FuelSource.FE);
            ywzjVehicleEnergyChargeRate = builder.comment("FE per charge interval (used in FLAT_RATE mode)").defineInRange("energyChargeRate", 500000, 0, Integer.MAX_VALUE);
            ywzjVehicleChargeInterval = builder.comment("Ticks between charges").defineInRange("chargeInterval", 20, 1, 1200);
            ywzjVehicleChargePercentage = builder.comment("Percentage of missing energy per interval (used in PERCENTAGE mode)").defineInRange("chargePercentage", 5.0, 0.1, 100.0);
            ywzjVehicleEnergyConversion = builder.comment("FE to fuel conversion divisor (default 1000, i.e. 1000 FE = 1 fuel). Lower = faster charge.").defineInRange("energyConversion", 1000, 1, Integer.MAX_VALUE);
            ywzjAllowedEnergyTypes = builder.comment("Vehicle energy types allowed to charge from network (matches energyInfo.energyType)").defineList("allowedEnergyTypes", Arrays.asList("fuel", "steam", "electric"), obj -> obj instanceof String);
            builder.pop();

            builder.comment("Item blacklist").push("blacklist");
            ENABLE_ITEM_BLACKLIST = builder.define("enable", false);
            ITEM_BLACKLIST = builder.defineList("items",
                    Arrays.asList("minecraft:barrier", "minecraft:command_block"), obj -> obj instanceof String);
            builder.pop();
        }
    }

    public static Language getCommandLanguage() { return SERVER.language.get(); }
    public static int maxNetworksPerPage() { return SERVER.maxNetworksPerPage.get(); }
    public static boolean enableItemBlacklist() { return SERVER.ENABLE_ITEM_BLACKLIST.get(); }
    public static List<? extends String> itemBlacklist() { return SERVER.ITEM_BLACKLIST.get(); }
    public static int vehicleChargeInterval() { return SERVER.swVehicleChargeInterval.get(); }
    public static double vehicleChargePercentage() { return SERVER.swVehicleChargePercentage.get(); }

    public static ChargeMode ywzjChargeMode() { return SERVER.ywzjChargeMode.get(); }
    public static FuelSource ywzjFuelSource() { return SERVER.ywzjFuelSource.get(); }
    public static int ywzjVehicleEnergyChargeRate() { return SERVER.ywzjVehicleEnergyChargeRate.get(); }
    public static int ywzjVehicleChargeInterval() { return SERVER.ywzjVehicleChargeInterval.get(); }
    public static double ywzjVehicleChargePercentage() { return SERVER.ywzjVehicleChargePercentage.get(); }
    public static int ywzjVehicleEnergyConversion() { return SERVER.ywzjVehicleEnergyConversion.get(); }
}
