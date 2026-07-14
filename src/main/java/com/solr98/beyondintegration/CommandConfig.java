package com.solr98.beyondintegration;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

public class CommandConfig
{
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ServerConfig SERVER;

    static
    {
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public enum Language
    {
        EN_US("en_us"), ZH_CN("zh_cn");

        private final String code;
        Language(String code) { this.code = code; }
        public String getCode() { return code; }
    }

    public enum VehicleChargeMode
    {
        RATE, PERCENTAGE
    }

    public static class ServerConfig
    {
        public final ForgeConfigSpec.EnumValue<Language> language;
        public final ForgeConfigSpec.IntValue maxNetworksPerPage;

        public final ForgeConfigSpec.BooleanValue ENABLE_ENCHANTMENT_SEPARATION;
        public final ForgeConfigSpec.BooleanValue ENABLE_ITEM_ENCHANTMENT_SEPARATION;
        public final ForgeConfigSpec.DoubleValue ITEM_SEPARATION_MULTIPLIER;
        public final ForgeConfigSpec.IntValue ENCHANTMENT_SEPARATION_BASE_COST;
        public final ForgeConfigSpec.IntValue ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER;
        public final ForgeConfigSpec.DoubleValue DEFAULT_ENCHANTMENT_MULTIPLIER;
        public final ForgeConfigSpec.ConfigValue<String> COST_FORMULA;
        public final ForgeConfigSpec.BooleanValue USE_FORMULA;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> HIGH_COST_ENCHANTMENTS;

        // Vehicle energy charge
        public final ForgeConfigSpec.EnumValue<VehicleChargeMode> swVehicleChargeMode;
        public final ForgeConfigSpec.IntValue swVehicleEnergyChargeRate;
        public final ForgeConfigSpec.IntValue swVehicleChargeInterval;
        public final ForgeConfigSpec.DoubleValue swVehicleChargePercentage;


        public final ForgeConfigSpec.BooleanValue ENABLE_ITEM_BLACKLIST;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;
        public final ForgeConfigSpec.IntValue CRAFT_COOLDOWN_MS;
        public final ForgeConfigSpec.IntValue CRAFT_MAX_DEPTH;
        public final ForgeConfigSpec.BooleanValue BLOCK_BD_CONTAINER_READER;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> AMMO_EXTRACT_MAPPINGS;
        public final ForgeConfigSpec.BooleanValue ENABLE_TOKEN_SYSTEM;
        public final ForgeConfigSpec.BooleanValue ENABLE_AUDIT_LOG;
        public final ForgeConfigSpec.ConfigValue<String> AUDIT_STORAGE;
        public final ForgeConfigSpec.ConfigValue<String> AUDIT_SQLITE_PATH;
        public final ForgeConfigSpec.ConfigValue<String> AUDIT_MYSQL_URL;
        public final ForgeConfigSpec.ConfigValue<String> AUDIT_MYSQL_USER;
        public final ForgeConfigSpec.ConfigValue<String> AUDIT_MYSQL_PASSWORD;
        public final ForgeConfigSpec.BooleanValue ALLOW_LEGACY_BINDINGS;

        public ServerConfig(ForgeConfigSpec.Builder builder)
        {
            builder.comment("Command language settings").push("language");
            language = builder.defineEnum("command_language", Language.EN_US);
            builder.pop();

            builder.comment("Network list settings").push("network_list");
            maxNetworksPerPage = builder.defineInRange("max_networks_per_page", 10, 1, 100);
            builder.pop();

            builder.comment("Enchantment separation settings").push("enchantment_separation");

            ENABLE_ENCHANTMENT_SEPARATION = builder
                    .comment("Enable enchantment separation when items pass through NetPump")
                    .define("enable", true);

            ENABLE_ITEM_ENCHANTMENT_SEPARATION = builder
                    .comment("Enable extracting enchantments from tools/weapons/armor")
                    .define("enable_item_extraction", false);

            ITEM_SEPARATION_MULTIPLIER = builder
                    .comment("Extra XP cost multiplier for extracting enchantments from items")
                    .defineInRange("item_multiplier", 2.0, 1.0, 100.0);

            ENCHANTMENT_SEPARATION_BASE_COST = builder
                    .comment("Base experience cost for separating one enchantment")
                    .defineInRange("base_cost", 10, 0, 1000);

            ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER = builder
                    .comment("Extra XP cost per enchantment level")
                    .defineInRange("level_multiplier", 5, 0, 100);

            DEFAULT_ENCHANTMENT_MULTIPLIER = builder
                    .comment("Default cost multiplier for enchantments not in the high-cost list")
                    .defineInRange("default_multiplier", 1.0, 0.1, 10.0);

            COST_FORMULA = builder
                    .comment("Cost formula. Variables: base, level, multiplier, books")
                    .define("cost_formula", "base + (level - 1) * multiplier");

            USE_FORMULA = builder
                    .comment("Use custom formula for cost calculation")
                    .define("use_formula", false);

            HIGH_COST_ENCHANTMENTS = builder
                    .comment("Enchantments with custom cost multiplier (format: 'modid:id:multiplier')")
                    .defineList("high_cost_list",
                            Arrays.asList("minecraft:mending:3.0", "minecraft:sharpness:1.2"),
                            obj -> obj instanceof String);

            builder.pop();

            builder.comment("Vehicle settings").push("vehicle");
            swVehicleChargeMode = builder
                    .comment("Charge mode: RATE = fixed FE/tick, PERCENTAGE = percentage of missing energy")
                    .defineEnum("charge_mode", VehicleChargeMode.RATE);
            swVehicleEnergyChargeRate = builder.defineInRange("energyChargeRate", 500000, 0, Integer.MAX_VALUE);
            swVehicleChargeInterval = builder.defineInRange("chargeInterval", 20, 1, 1200);
            swVehicleChargePercentage = builder.defineInRange("chargePercentage", 0.0, 0.0, 100.0);
            builder.pop();

            builder.comment("Item blacklist").push("blacklist");
            ENABLE_ITEM_BLACKLIST = builder.comment("Enable").define("enable", false);
            ITEM_BLACKLIST = builder.comment("Blocked items").defineList("items",
                    Arrays.asList("minecraft:barrier", "minecraft:command_block"), obj -> obj instanceof String);
            builder.pop();

            builder.comment("Craft settings").push("craft");
            CRAFT_COOLDOWN_MS = builder.comment("Cooldown (ms)").defineInRange("cooldown_ms", 1000, 0, 60000);
            CRAFT_MAX_DEPTH = builder.comment("Max recipe depth").defineInRange("max_depth", 6, 1, 20);
            BLOCK_BD_CONTAINER_READER = builder.comment("Block TACZ addon reader").define("block_bd_reader", true);
            builder.pop();

            builder.comment("Ammo extract mappings for NetInterface").push("ammo_extract");
            AMMO_EXTRACT_MAPPINGS = builder
                    .comment("Ammo extract mappings. Format: 'modid:item_path:ammo_type'")
                    .defineList("mappings",
                            Arrays.asList(
                                    "superbwarfare:handgun_ammo:HandgunAmmo",
                                    "superbwarfare:handgun_ammo_box:HandgunAmmo",
                                    "superbwarfare:rifle_ammo:RifleAmmo",
                                    "superbwarfare:rifle_ammo_box:RifleAmmo",
                                    "superbwarfare:shotgun_ammo:ShotgunAmmo",
                                    "superbwarfare:shotgun_ammo_box:ShotgunAmmo",
                                    "superbwarfare:sniper_ammo:SniperAmmo",
                                    "superbwarfare:sniper_ammo_box:SniperAmmo",
                                    "superbwarfare:heavy_ammo:HeavyAmmo"
                            ),
                            obj -> obj instanceof String);
            builder.pop();

            builder.comment("Token system (network security)").push("token");
            ENABLE_TOKEN_SYSTEM = builder
                    .comment("Enable network binding token system. Disabling this removes all token-based security.")
                    .define("enable", true);
            ALLOW_LEGACY_BINDINGS = builder
                    .comment("Allow legacy bindings (items/blocks bound before token system)")
                    .define("allow_legacy_bindings", true);
            builder.pop();

            builder.comment("Audit log settings").push("audit");
            ENABLE_AUDIT_LOG = builder
                    .comment("Enable audit log. Fully disable to eliminate all logging.")
                    .define("enable", true);
            AUDIT_STORAGE = builder
                    .comment("Storage backend: JSONL (file), SQLITE (embedded), MYSQL (remote)")
                    .define("storage", "SQLITE");
            AUDIT_SQLITE_PATH = builder
                    .comment("SQLite database file path (relative to world directory)")
                    .define("sqlite_path", "beyond_audit.db");
            AUDIT_MYSQL_URL = builder
                    .comment("MySQL JDBC URL (e.g. jdbc:mysql://host:3306/dbname)")
                    .define("mysql_url", "");
            AUDIT_MYSQL_USER = builder
                    .comment("MySQL username")
                    .define("mysql_user", "");
            AUDIT_MYSQL_PASSWORD = builder
                    .comment("MySQL password")
                    .define("mysql_password", "");
            builder.pop();
        }

    }

    public static Language getCommandLanguage() { return SERVER.language.get(); }
    public static int maxNetworksPerPage() { return SERVER.maxNetworksPerPage.get(); }

    public static boolean enableEnchantmentSeparation() { return SERVER.ENABLE_ENCHANTMENT_SEPARATION.get(); }
    public static boolean enableItemEnchantmentSeparation() { return SERVER.ENABLE_ITEM_ENCHANTMENT_SEPARATION.get(); }
    public static double itemSeparationMultiplier() { return SERVER.ITEM_SEPARATION_MULTIPLIER.get(); }
    public static int enchantmentSeparationBaseCost() { return SERVER.ENCHANTMENT_SEPARATION_BASE_COST.get(); }
    public static int enchantmentSeparationLevelMultiplier() { return SERVER.ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER.get(); }
    public static double defaultEnchantmentMultiplier() { return SERVER.DEFAULT_ENCHANTMENT_MULTIPLIER.get(); }
    public static String costFormula() { return SERVER.COST_FORMULA.get(); }
    public static boolean useFormula() { return SERVER.USE_FORMULA.get(); }
    public static List<? extends String> highCostEnchantments() { return SERVER.HIGH_COST_ENCHANTMENTS.get(); }

    public static boolean enableItemBlacklist() { return SERVER.ENABLE_ITEM_BLACKLIST.get(); }
    public static List<? extends String> itemBlacklist() { return SERVER.ITEM_BLACKLIST.get(); }
    public static VehicleChargeMode vehicleChargeMode() { return SERVER.swVehicleChargeMode.get(); }
    public static int vehicleChargeRate() { return SERVER.swVehicleEnergyChargeRate.get(); }
    public static int vehicleChargeInterval() { return SERVER.swVehicleChargeInterval.get(); }
    public static double vehicleChargePercentage() { return SERVER.swVehicleChargePercentage.get(); }

    public static int craftCooldownMs() { return SERVER.CRAFT_COOLDOWN_MS.get(); }
    public static int craftMaxDepth() { return SERVER.CRAFT_MAX_DEPTH.get(); }
    public static boolean blockBdContainerReader() { return SERVER.BLOCK_BD_CONTAINER_READER.get(); }

    public static List<? extends String> ammoExtractMappings() { return SERVER.AMMO_EXTRACT_MAPPINGS.get(); }
    public static boolean enableTokenSystem() { return SERVER.ENABLE_TOKEN_SYSTEM.get(); }
    public static boolean enableAuditLog() { return SERVER.ENABLE_AUDIT_LOG.get(); }
    public static String auditStorage() { return SERVER.AUDIT_STORAGE.get(); }
    public static String auditSqlitePath() { return SERVER.AUDIT_SQLITE_PATH.get(); }
    public static String auditMysqlUrl() { return SERVER.AUDIT_MYSQL_URL.get(); }
    public static String auditMysqlUser() { return SERVER.AUDIT_MYSQL_USER.get(); }
    public static String auditMysqlPassword() { return SERVER.AUDIT_MYSQL_PASSWORD.get(); }
    public static boolean allowLegacyBindings() { return SERVER.ALLOW_LEGACY_BINDINGS.get(); }
}
