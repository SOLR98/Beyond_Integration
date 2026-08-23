package com.solr98.beyondintegration;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

/**
 * 模组通用配置（ForgeConfigSpec 服务端配置）。
 * 集中定义全部可配置项：语言、网络列表分页、附魔分离、载具充能、
 * 物品黑名单、合成冷却、弹药提取映射、TACZ/SW 弹药轮询、
 * 铁砧工作台计费与自动图腾等，并提供静态访问入口。
 */
public class CommandConfig
{
    /** 服务端配置规格（同步到客户端） */
    public static final ForgeConfigSpec SERVER_SPEC;
    /** 服务端配置对象实例 */
    public static final ServerConfig SERVER;

    /** 构建配置规格与配置实例 */
    static
    {
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    /** 命令输出语言选项 */
    public enum Language
    {
        EN_US("en_us"), ZH_CN("zh_cn");

        private final String code;
        Language(String code) { this.code = code; }
        public String getCode() { return code; }
    }

    /** 载具充能模式：按固定速率（FE/tick）或按缺失能量的百分比 */
    public enum VehicleChargeMode
    {
        RATE, PERCENTAGE
    }

    /** 铁砧工作台计费模式：按等级或按经验点数 */
    public enum AnvilChargeMode
    {
        LEVEL, POINTS
    }

    /**
     * 铁砧附魔等级上限模式：
     * OFF      = 原版：钳制附魔最高等级（5 级书 + 5 级书 → 6，但封顶 5）；
     * PLUS     = 叠级突破：保留原版合成规则（同附魔 5+5=6、异附魔取 max），仅解除封顶；
     * ADDITIVE = 相加突破：仅同类附魔等级直接相加（5+5=10），不同附魔仍取 max。
     */
    public enum BreakLevelMode
    {
        OFF, PLUS, ADDITIVE
    }

    /** 配置项定义类：在构造器中分区注册全部配置条目 */
    public static class ServerConfig
    {
        public final ForgeConfigSpec.EnumValue<Language> language;
        public final ForgeConfigSpec.IntValue maxNetworksPerPage;

        public final ForgeConfigSpec.BooleanValue ENABLE_ENCHANTMENT_SEPARATION;


        // Enchantment separation debug
        public final ForgeConfigSpec.BooleanValue ENCHANTMENT_SEPARATION_DEBUG;
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

        // TACZ ammo polling
        public final ForgeConfigSpec.BooleanValue TACZ_AMMO_POLL_ENABLED;
        public final ForgeConfigSpec.IntValue TACZ_AMMO_POLL_INTERVAL_TICKS;

        // SW ammo polling
        public final ForgeConfigSpec.BooleanValue SW_AMMO_POLL_ENABLED;
        public final ForgeConfigSpec.IntValue SW_AMMO_POLL_INTERVAL_TICKS;

        // SW energy ammo network charging
        public final ForgeConfigSpec.BooleanValue ENERGY_AMMO_CHARGE_ENABLED;
        public final ForgeConfigSpec.IntValue ENERGY_AMMO_CHARGE_INTERVAL;
        public final ForgeConfigSpec.IntValue ENERGY_AMMO_CHARGE_RATE;

        // Anvil workstation
        public final ForgeConfigSpec.EnumValue<AnvilChargeMode> anvilCostMode;
        public final ForgeConfigSpec.IntValue anvilLevelCap;
        public final ForgeConfigSpec.LongValue anvilPointsCap;
        public final ForgeConfigSpec.EnumValue<BreakLevelMode> anvilBreakLevelMode;
        public final ForgeConfigSpec.BooleanValue anvilIgnoreConflict;
        public final ForgeConfigSpec.BooleanValue anvilIgnoreSupport;
        public final ForgeConfigSpec.BooleanValue anvilUnrestricted;
        public final ForgeConfigSpec.IntValue anvilConflictPenalty;
        public final ForgeConfigSpec.IntValue anvilSupportPenalty;
        public final ForgeConfigSpec.IntValue anvilConflictPercent;
        public final ForgeConfigSpec.IntValue anvilSupportPercent;
        public final ForgeConfigSpec.IntValue anvilBreakLevelPercent;
        public final ForgeConfigSpec.IntValue anvilUnrestrictedPercent;

        // Auto totem (network only)
        public final ForgeConfigSpec.BooleanValue AUTO_TOTEM_ENABLED;
        public final ForgeConfigSpec.IntValue AUTO_TOTEM_COOLDOWN_SECONDS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> AUTO_TOTEM_DAMAGE_BLACKLIST;
        public final ForgeConfigSpec.BooleanValue AUTO_TOTEM_RESPECT_BYPASSES;
        public final ForgeConfigSpec.BooleanValue AUTO_TOTEM_RESTORE_MAX_HEALTH;
        public final ForgeConfigSpec.BooleanValue AUTO_TOTEM_HEAL_TO_FULL;

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

            ENCHANTMENT_SEPARATION_BASE_COST = builder
                    .comment("Base XP cost per enchantment")
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

            ENCHANTMENT_SEPARATION_DEBUG = builder
                    .comment("附魔分离调试日志：开启后输出各分支判定详情（物品/开关/放行原因/分离结果），用于排查分离不生效",
                            "  示例: enchantmentSeparationDebug=true → 控制台输出 [enchant-sep] 前缀的详细日志")
                    .define("debug", false);

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

            builder.comment("TACZ network ammo polling settings").push("tacz_ammo");
            TACZ_AMMO_POLL_ENABLED = builder
                    .comment("Enable server-side polling of tacz:ammo items per network (full rescan + push to clients)")
                    .define("ammo_poll_enabled", true);
            TACZ_AMMO_POLL_INTERVAL_TICKS = builder
                    .comment("Ticks between full ammo rescans and pushes to clients")
                    .defineInRange("ammo_poll_interval_ticks", 10, 1, 1200);
            builder.pop();

            builder.comment("SW network ammo polling settings").push("sw_ammo");
            SW_AMMO_POLL_ENABLED = builder
                    .comment("Enable server-side polling of SW virtual ammo + FE per network (diff-based delta push)")
                    .define("ammo_poll_enabled", true);
            SW_AMMO_POLL_INTERVAL_TICKS = builder
                    .comment("Ticks between SW ammo/FE diff checks and pushes to clients")
                    .defineInRange("ammo_poll_interval_ticks", 10, 1, 1200);
            builder.pop();

            builder.comment("SW energy ammo network charging settings").push("energy_ammo");
            ENERGY_AMMO_CHARGE_ENABLED = builder
                    .comment("Enable auto-charging handheld SW energy weapons (ENERGY ammo type) from the player's primary network FE storage",
                            "  示例: energyAmmoChargeEnabled=true → 手持能量武器时每隔 interval 从网络充电")
                    .define("charge_enabled", true);
            ENERGY_AMMO_CHARGE_INTERVAL = builder
                    .comment("Ticks between each network charge attempt for handheld energy weapons")
                    .defineInRange("charge_interval", 20, 1, 1200);
            ENERGY_AMMO_CHARGE_RATE = builder
                    .comment("FE extracted from network per charge attempt (capped by weapon's missing energy)")
                    .defineInRange("charge_rate", 10000, 1, Integer.MAX_VALUE);
            builder.pop();

            builder.comment("Anvil workstation settings").push("anvil");
            anvilCostMode = builder
                    .comment("Anvil XP charge mode: LEVEL = vanilla levels (network XP tops up player levels first), POINTS = fixed XP points (network XP pays first)")
                    .defineEnum("costMode", AnvilChargeMode.POINTS);
            anvilLevelCap = builder
                    .comment("LEVEL mode 'too expensive' level cap (40 = vanilla default, 2147483647 = no cap)")
                    .defineInRange("levelCap", 40, 1, Integer.MAX_VALUE);
            anvilPointsCap = builder
                    .comment("POINTS mode 'too expensive' XP point cap (9223372036854775807 = no cap)")
                    .defineInRange("pointsCap", Long.MAX_VALUE, 1L, Long.MAX_VALUE);
            anvilBreakLevelMode = builder
                    .comment("附魔等级上限模式 (OFF=原版钳制封顶，PLUS=叠级突破 5+5=6 仅解除封顶，ADDITIVE=同类直接相加 5+5=10)",
                            "  示例: anvilBreakLevelMode=ADDITIVE  → 两本锋利V合成锋利X")
                    .defineEnum("breakMaxLevelMode", BreakLevelMode.OFF);
            anvilIgnoreConflict = builder
                    .comment("无视附魔冲突：冲突附魔不再被剔除，可同时打上（每个冲突附魔计 conflictPenalty 罚金）",
                            "  示例: ignoreConflict=true 时 锋利V + 亡灵杀手V 可共存于同一物品")
                    .define("ignoreConflict", false);
            anvilIgnoreSupport = builder
                    .comment("无视附魔适用性：本不该能打上的附魔也能打，等同创造模式行为（每个违例附魔计 supportPenalty 罚金）",
                            "  示例: ignoreSupport=true 时 锄头也能打锋利V")
                    .define("ignoreSupport", false);
            anvilUnrestricted = builder
                    .comment("完全解禁：一键同时开启 breakMaxLevelMode=PLUS、ignoreConflict、ignoreSupport（百分位倍率叠加，见各 Percent 项）",
                            "  示例: unrestricted=true 时所有限制解除，费用按倍率公式 (100 + Σ启用项百分位)/100 叠加")
                    .define("unrestricted", false);
            anvilConflictPenalty = builder
                    .comment("每个冲突附魔的固定罚金（基础值，先计入费用，再应用百分位倍率）",
                            "  示例: conflictPenalty=2 时打 2 个冲突附魔，费用额外 +4")
                    .defineInRange("conflictPenalty", 2, 0, 1000000);
            anvilSupportPenalty = builder
                    .comment("每个违例（不适用）附魔的固定罚金（基础值）",
                            "  示例: supportPenalty=5 时打 1 个违例附魔，费用额外 +5")
                    .defineInRange("supportPenalty", 5, 0, 1000000);
            anvilConflictPercent = builder
                    .comment("无视冲突的百分位费用加成（倍率 = (100 + Σ启用项百分位)/100，各项百分位相加，100=额外×1）",
                            "  示例: conflictPercent=50 且开启 ignoreConflict → 附魔费用 ×1.5")
                    .defineInRange("conflictPercent", 0, 0, 1000000);
            anvilSupportPercent = builder
                    .comment("无视适用性的百分位费用加成（倍率同上，各项百分位相加）",
                            "  示例: supportPercent=50 且开启 ignoreSupport → 附魔费用 ×1.5")
                    .defineInRange("supportPercent", 0, 0, 1000000);
            anvilBreakLevelPercent = builder
                    .comment("等级上限突破（PLUS/ADDITIVE）的百分位费用加成（倍率同上，各项百分位相加）",
                            "  示例: breakLevelPercent=30 → 附魔费用 ×1.3")
                    .defineInRange("breakLevelPercent", 0, 0, 1000000);
            anvilUnrestrictedPercent = builder
                    .comment("完全解禁模式的百分位费用加成（倍率 = (100 + Σ启用项百分位)/100）",
                            "  示例: unrestrictedPercent=100 且开启 unrestricted → 费用 ×2")
                    .defineInRange("unrestrictedPercent", 100, 0, 1000000);
            builder.pop();

            builder.comment("Auto totem settings (uses totems from your network only)").push("auto_totem");
            AUTO_TOTEM_ENABLED = builder
                    .comment("Automatically use a totem from your network when dying")
                    .define("enabled", true);
            AUTO_TOTEM_COOLDOWN_SECONDS = builder
                    .comment("Cooldown between auto-totem uses (seconds, 0 = no cooldown)")
                    .defineInRange("cooldown_seconds", 10, 0, 3600);
            AUTO_TOTEM_DAMAGE_BLACKLIST = builder
                    .comment("Damage types that will NOT trigger auto-totem (msgId or minecraft:msgId)")
                    .defineList("damage_blacklist",
                            Arrays.asList("outOfWorld", "fellOutOfWorld", "genericKill", "command"),
                            obj -> obj instanceof String);
            AUTO_TOTEM_RESPECT_BYPASSES = builder
                    .comment("Whether auto-totem respects the vanilla BYPASSES_INVULNERABILITY rule ",
                             "(damage like void/out_of_world and /kill never triggers totem). ",
                             "true = keep vanilla behavior; false = allow triggering on such damage (damage_blacklist still applies)")
                    .define("respect_bypasses_invulnerability", false);
            AUTO_TOTEM_RESTORE_MAX_HEALTH = builder
                    .comment("Restore the player's reduced maximum health when auto-totem triggers ",
                             "(removes all negative max_health attribute modifiers, e.g. from mods/effects). ",
                             "true = restore the reduced max-health cap")
                    .define("restore_max_health", true);
            AUTO_TOTEM_HEAL_TO_FULL = builder
                    .comment("Heal the player to full health when auto-totem triggers. ",
                             "false = keep vanilla totem behavior (1 HP + regeneration buff)")
                    .define("heal_to_full", false);
            builder.pop();
        }

    }

    // ─── 配置读取静态入口（供各处代码调用） ───
    public static Language getCommandLanguage() { return SERVER.language.get(); }
    public static int maxNetworksPerPage() { return SERVER.maxNetworksPerPage.get(); }

    public static boolean enableEnchantmentSeparation() { return SERVER.ENABLE_ENCHANTMENT_SEPARATION.get(); }
    public static boolean enchantmentSeparationDebug() { return SERVER.ENCHANTMENT_SEPARATION_DEBUG.get(); }
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

    public static boolean taczAmmoPollEnabled() { return SERVER.TACZ_AMMO_POLL_ENABLED.get(); }
    public static int taczAmmoPollIntervalTicks() { return SERVER.TACZ_AMMO_POLL_INTERVAL_TICKS.get(); }

    public static boolean swAmmoPollEnabled() { return SERVER.SW_AMMO_POLL_ENABLED.get(); }
    public static int swAmmoPollIntervalTicks() { return SERVER.SW_AMMO_POLL_INTERVAL_TICKS.get(); }
    public static boolean energyAmmoChargeEnabled() { return SERVER.ENERGY_AMMO_CHARGE_ENABLED.get(); }
    public static int energyAmmoChargeInterval() { return SERVER.ENERGY_AMMO_CHARGE_INTERVAL.get(); }
    public static int energyAmmoChargeRate() { return SERVER.ENERGY_AMMO_CHARGE_RATE.get(); }

    public static AnvilChargeMode anvilCostMode() { return SERVER.anvilCostMode.get(); }
    public static int anvilLevelCap() { return SERVER.anvilLevelCap.get(); }
    public static long anvilPointsCap() { return SERVER.anvilPointsCap.get(); }
    public static BreakLevelMode anvilBreakLevelMode() { return SERVER.anvilBreakLevelMode.get(); }
    public static boolean anvilIgnoreConflict() { return SERVER.anvilIgnoreConflict.get(); }
    public static boolean anvilIgnoreSupport() { return SERVER.anvilIgnoreSupport.get(); }
    public static boolean anvilUnrestricted() { return SERVER.anvilUnrestricted.get(); }
    public static int anvilConflictPenalty() { return SERVER.anvilConflictPenalty.get(); }
    public static int anvilSupportPenalty() { return SERVER.anvilSupportPenalty.get(); }
    public static int anvilConflictPercent() { return SERVER.anvilConflictPercent.get(); }
    public static int anvilSupportPercent() { return SERVER.anvilSupportPercent.get(); }
    public static int anvilBreakLevelPercent() { return SERVER.anvilBreakLevelPercent.get(); }
    public static int anvilUnrestrictedPercent() { return SERVER.anvilUnrestrictedPercent.get(); }

    public static boolean autoTotemEnabled() { return SERVER.AUTO_TOTEM_ENABLED.get(); }
    public static int autoTotemCooldownSeconds() { return SERVER.AUTO_TOTEM_COOLDOWN_SECONDS.get(); }
    public static List<? extends String> autoTotemDamageBlacklist() { return SERVER.AUTO_TOTEM_DAMAGE_BLACKLIST.get(); }
    public static boolean autoTotemRespectBypassesInvulnerability() { return SERVER.AUTO_TOTEM_RESPECT_BYPASSES.get(); }
    public static boolean autoTotemRestoreMaxHealth() { return SERVER.AUTO_TOTEM_RESTORE_MAX_HEALTH.get(); }
    public static boolean autoTotemHealToFull() { return SERVER.AUTO_TOTEM_HEAL_TO_FULL.get(); }
}
