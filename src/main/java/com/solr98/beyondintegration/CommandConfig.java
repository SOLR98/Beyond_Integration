package com.solr98.beyondintegration;

import com.solr98.beyondintegration.config.BlacklistConfig;
import com.solr98.beyondintegration.config.EnchantConfig;
import com.solr98.beyondintegration.config.GeneralConfig;
import com.solr98.beyondintegration.config.VehicleConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class CommandConfig {

    public static final ModConfigSpec SERVER_SPEC;

    public enum Language { EN_US, ZH_CN }
    public enum ChargeMode { OFF, FLAT_RATE, PERCENTAGE }
    public enum SWChargeMode { PERCENTAGE, ABSOLUTE, SUM }
    public enum FuelSource { FE, FLUID }
    public enum EnchantFilterMode { DISABLED, WHITELIST, BLACKLIST }
    public enum EnchantItemFilterMode { DISABLED, WHITELIST, BLACKLIST }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        GeneralConfig.build(builder);
        EnchantConfig.build(builder);
        VehicleConfig.build(builder);
        BlacklistConfig.build(builder);
        SERVER_SPEC = builder.build();
    }

    public static final ServerConfig SERVER = new ServerConfig();

    public static class ServerConfig {
        // ── General ──
        public final ModConfigSpec.EnumValue<Language> language = GeneralConfig.fields().language;
        public final ModConfigSpec.IntValue maxNetworksPerPage = GeneralConfig.fields().maxNetworksPerPage;

        // ── Enchant ──
        public final ModConfigSpec.BooleanValue enchantSeparation = EnchantConfig.fields().enableSeparation;
        public final ModConfigSpec.BooleanValue enchantItemSeparation = EnchantConfig.fields().enableItemSeparation;
        public final ModConfigSpec.DoubleValue enchantItemMult = EnchantConfig.fields().itemMult;
        public final ModConfigSpec.IntValue enchantBaseCost = EnchantConfig.fields().baseCost;
        public final ModConfigSpec.DoubleValue enchantLevelMult = EnchantConfig.fields().levelMult;
        public final ModConfigSpec.DoubleValue enchantDefaultMult = EnchantConfig.fields().defaultMult;
        public final ModConfigSpec.ConfigValue<String> enchantCostFormula = EnchantConfig.fields().costFormula;
        public final ModConfigSpec.BooleanValue enchantUseFormula = EnchantConfig.fields().useFormula;
        public final ModConfigSpec.ConfigValue<List<? extends String>> enchantHighCostList = EnchantConfig.fields().highCostList;
        public final ModConfigSpec.EnumValue<EnchantFilterMode> enchantFilterMode = EnchantConfig.fields().filterMode;
        public final ModConfigSpec.ConfigValue<List<? extends String>> enchantFilterList = EnchantConfig.fields().filterList;
        public final ModConfigSpec.EnumValue<EnchantItemFilterMode> enchantItemFilterMode = EnchantConfig.fields().itemFilterMode;
        public final ModConfigSpec.ConfigValue<List<? extends String>> enchantItemFilterList = EnchantConfig.fields().itemFilterList;

        // ── SW Vehicle ──
        public final ModConfigSpec.EnumValue<SWChargeMode> swChargeMode = VehicleConfig.fields().swChargeMode;
        public final ModConfigSpec.IntValue swVehicleEnergyChargeRate = VehicleConfig.fields().swEnergyChargeRate;
        public final ModConfigSpec.IntValue swVehicleChargeInterval = VehicleConfig.fields().swChargeInterval;
        public final ModConfigSpec.DoubleValue swVehicleChargePercentage = VehicleConfig.fields().swChargePercentage;

        // ── YWZJ Vehicle ──
        public final ModConfigSpec.EnumValue<ChargeMode> ywzjChargeMode = VehicleConfig.fields().ywzjChargeMode;
        public final ModConfigSpec.EnumValue<FuelSource> ywzjFuelSource = VehicleConfig.fields().ywzjFuelSource;
        public final ModConfigSpec.IntValue ywzjVehicleEnergyChargeRate = VehicleConfig.fields().ywzjEnergyChargeRate;
        public final ModConfigSpec.IntValue ywzjVehicleChargeInterval = VehicleConfig.fields().ywzjChargeInterval;
        public final ModConfigSpec.DoubleValue ywzjVehicleChargePercentage = VehicleConfig.fields().ywzjChargePercentage;
        public final ModConfigSpec.IntValue ywzjVehicleEnergyConversion = VehicleConfig.fields().ywzjEnergyConversion;
        public final ModConfigSpec.ConfigValue<List<? extends String>> ywzjAllowedEnergyTypes = VehicleConfig.fields().ywzjAllowedEnergyTypes;

        // ── Blacklist ──
        public final ModConfigSpec.BooleanValue ENABLE_ITEM_BLACKLIST = BlacklistConfig.fields().enable;
        public final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST = BlacklistConfig.fields().items;
    }

    // ── General ──
    public static Language getCommandLanguage() { return GeneralConfig.language(); }
    public static int maxNetworksPerPage() { return GeneralConfig.maxNetworksPerPage(); }

    // ── Blacklist ──
    public static boolean enableItemBlacklist() { return BlacklistConfig.enable(); }
    public static List<? extends String> itemBlacklist() { return BlacklistConfig.items(); }

    // ── Enchant ──
    public static boolean enableEnchantSeparation() { return EnchantConfig.enableSeparation(); }
    public static boolean enableItemEnchantSeparation() { return EnchantConfig.enableItemSeparation(); }
    public static double itemSeparationMultiplier() { return EnchantConfig.itemMult(); }
    public static int enchantBaseCost() { return EnchantConfig.baseCost(); }
    public static double enchantLevelMult() { return EnchantConfig.levelMult(); }
    public static double enchantDefaultMult() { return EnchantConfig.defaultMult(); }
    public static String costFormula() { return EnchantConfig.costFormula(); }
    public static boolean useFormula() { return EnchantConfig.useFormula(); }
    public static List<? extends String> enchantHighCostList() { return EnchantConfig.highCostList(); }
    public static EnchantFilterMode enchantFilterMode() { return EnchantConfig.filterMode(); }
    public static List<? extends String> enchantFilterList() { return EnchantConfig.filterList(); }
    public static EnchantItemFilterMode enchantItemFilterMode() { return EnchantConfig.itemFilterMode(); }
    public static List<? extends String> enchantItemFilterList() { return EnchantConfig.itemFilterList(); }

    // ── Vehicle (SW) ──
    public static SWChargeMode swChargeMode() { return VehicleConfig.swChargeMode(); }
    public static int swVehicleEnergyChargeRate() { return VehicleConfig.swEnergyChargeRate(); }
    public static int vehicleChargeInterval() { return VehicleConfig.swChargeInterval(); }
    public static double vehicleChargePercentage() { return VehicleConfig.swChargePercentage(); }

    // ── Vehicle (YWZJ) ──
    public static ChargeMode ywzjChargeMode() { return VehicleConfig.ywzjChargeMode(); }
    public static FuelSource ywzjFuelSource() { return VehicleConfig.ywzjFuelSource(); }
    public static int ywzjVehicleEnergyChargeRate() { return VehicleConfig.ywzjEnergyChargeRate(); }
    public static int ywzjVehicleChargeInterval() { return VehicleConfig.ywzjChargeInterval(); }
    public static double ywzjVehicleChargePercentage() { return VehicleConfig.ywzjChargePercentage(); }
    public static int ywzjVehicleEnergyConversion() { return VehicleConfig.ywzjEnergyConversion(); }
    public static List<? extends String> ywzjAllowedEnergyTypes() { return VehicleConfig.ywzjAllowedEnergyTypes(); }
}
