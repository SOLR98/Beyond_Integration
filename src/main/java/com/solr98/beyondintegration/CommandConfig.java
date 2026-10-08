package com.solr98.beyondintegration;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import com.solr98.beyondintegration.core.config.ConfigCommentLang;

import java.util.Arrays;
import java.util.List;

/**
 * 模组服务端配置文件定义：集中管理所有 COMMON 类型配置项，
 * 涵盖命令语言、附魔分离、载具充电、物品黑名单、铁砧工作站、网络图腾与弹药轮询等。
 */
public class CommandConfig {
    /** 构建完成的配置规格对象，供 NeoForge 注册使用。 */
    public static final ModConfigSpec SERVER_SPEC;
    /** 配置值访问入口，通过它读取各配置项的当前值。 */
    public static final ServerConfig SERVER;
    static {
        // 构建配置规格并将配置值与规格对象解耦存放
        final Pair<ServerConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    /** 命令输出语言选项。 */
    public enum Language { EN_US("en_us"), ZH_CN("zh_cn");
        private final String code;
        Language(String code) { this.code = code; }
        public String getCode() { return code; }
    }

    /** 载具充电模式：关闭 / 固定速率 / 按缺失能量百分比。 */
    public enum ChargeMode { OFF, FLAT_RATE, PERCENTAGE }
    /** 载具燃料来源：FE（网络能量）或 FLUID（流体）。 */
    public enum FuelSource { FE, FLUID }
    /** 铁砧经验收费模式：等级或经验点数。 */
    public enum AnvilChargeMode { LEVEL, POINTS }
    /** 物品装备位网络充电模式：固定值 / 缺失能量百分比 / 百分比 + 固定值。 */
    public enum EnergyChargeMode { RATE, PERCENTAGE, PERCENTAGE_PLUS_RATE }
    /**
     * 附魔台（网络化）附魔功率来源：
     * FIXED       = 固定功率（enchantFixedPower）；
     * NETWORK_XP  = 网络经验流体存量换算（每 enchantXpPerPower 点 = 1 功率）；
     * PLAYER_LEVEL= 玩家经验等级换算（每 enchantLevelPerPower 级 = 1 功率）。
     */
    public enum EnchantPowerMode { FIXED, NETWORK_XP, PLAYER_LEVEL }
    /**
     * 铁砧附魔等级上限模式：
     * OFF      = 原版：钳制附魔最高等级（5 级书 + 5 级书 → 6，但封顶 5）；
     * PLUS     = 叠级突破：保留原版合成规则（同附魔 5+5=6、异附魔取 max），仅解除封顶；
     * ADDITIVE = 相加突破：仅同类附魔等级直接相加（5+5=10），不同附魔仍取 max。
     */
    public enum BreakLevelMode { OFF, PLUS, ADDITIVE }

    /**
     * 经验棒经验给予方式：
     * BATCH  = 分批 giveExperiencePoints（每 tick 一批，合法升级上限 238609312；
     *          再往上升级所需经验超过 int 上限，经验条会异常；totalExperience 从 21863 级起饱和）；
     * DIRECT = 直设式（等级计算 + 设定等级/进度 + 网络经验补差），不依赖原版升级公式。
     */
    public enum XpGrantMode { BATCH, DIRECT }

    /**
     * 服务端配置类：定义所有配置项的默认值与范围，
     * 通过 builder 按分区（push/pop）组织配置界面。
     */
    public static class ServerConfig {
        public final ModConfigSpec.EnumValue<Language> language;
        public final ModConfigSpec.IntValue maxNetworksPerPage;

        // Enchantment separation
        public final ModConfigSpec.BooleanValue enchantSeparation;
        public final ModConfigSpec.IntValue enchantBaseCost;
        public final ModConfigSpec.DoubleValue enchantLevelMult;
        public final ModConfigSpec.DoubleValue enchantDefaultMult;
        public final ModConfigSpec.ConfigValue<List<? extends String>> enchantHighCostList;
        public final ModConfigSpec.BooleanValue enchantDebug;
        /** 附魔分离：单附魔书同类同级自动合并（两本 L 级 → 一本 L+1 级，铁砧费用转点数，默认关闭） */
        public final ModConfigSpec.BooleanValue enchantMergeSameLevel;

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

// Item blacklist
        public final ModConfigSpec.BooleanValue ENABLE_ITEM_BLACKLIST;
        public final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;

        /** 阻止 taczaddon 将 BD 网络方块识别为合成材料容器源 */
        public final ModConfigSpec.BooleanValue BLOCK_BD_CONTAINER_READER;

        // Workstation availability (server-side)
        public final ModConfigSpec.ConfigValue<List<? extends String>> WORKSTATIONS_ENABLED;

        // 工作台献祭激活（可选平衡项）：开启后 anvil/cut/grind/smith/enchant 需先献祭对应原版工作台激活（网络级）
        public final ModConfigSpec.BooleanValue WORKSTATION_ACTIVATION_ENABLED;
        public final ModConfigSpec.ConfigValue<List<? extends String>> WORKSTATION_ACTIVATION_COSTS;

        // TACZ ammo polling
        public final ModConfigSpec.BooleanValue TACZ_AMMO_POLL_ENABLED;
        public final ModConfigSpec.IntValue TACZ_AMMO_POLL_INTERVAL_TICKS;

        public final ModConfigSpec.BooleanValue SW_AMMO_POLL_ENABLED;
        public final ModConfigSpec.IntValue SW_AMMO_POLL_INTERVAL_TICKS;

        // SW energy ammo network charging
        public final ModConfigSpec.BooleanValue ENERGY_AMMO_CHARGE_ENABLED;
        public final ModConfigSpec.IntValue ENERGY_AMMO_CHARGE_INTERVAL;
        public final ModConfigSpec.IntValue ENERGY_AMMO_CHARGE_RATE;
        public final ModConfigSpec.ConfigValue<List<? extends String>> ENERGY_AMMO_CHARGE_WHITELIST;
        public final ModConfigSpec.BooleanValue ENERGY_AMMO_CHARGE_CURIOS;
        public final ModConfigSpec.BooleanValue ENERGY_AMMO_CHARGE_MAID_BAUBLES;
        public final ModConfigSpec.EnumValue<EnergyChargeMode> ENERGY_AMMO_CHARGE_MODE;
        public final ModConfigSpec.DoubleValue ENERGY_AMMO_CHARGE_PERCENTAGE;

        // Anvil workstation
        public final ModConfigSpec.EnumValue<AnvilChargeMode> anvilCostMode;
        public final ModConfigSpec.IntValue anvilLevelCap;
        public final ModConfigSpec.LongValue anvilPointsCap;
        public final ModConfigSpec.EnumValue<BreakLevelMode> anvilBreakLevelMode;
        public final ModConfigSpec.BooleanValue anvilIgnoreConflict;
        public final ModConfigSpec.BooleanValue anvilIgnoreSupport;
        public final ModConfigSpec.BooleanValue anvilUnrestricted;
        public final ModConfigSpec.IntValue anvilConflictPenalty;
        public final ModConfigSpec.IntValue anvilSupportPenalty;
        public final ModConfigSpec.IntValue anvilConflictPercent;
        public final ModConfigSpec.IntValue anvilSupportPercent;
        public final ModConfigSpec.IntValue anvilBreakLevelPercent;
        public final ModConfigSpec.IntValue anvilUnrestrictedPercent;

        // Enchantment table workstation
        public final ModConfigSpec.EnumValue<EnchantPowerMode> enchantPowerMode;
        public final ModConfigSpec.IntValue enchantFixedPower;
        public final ModConfigSpec.IntValue enchantXpPerPower;
        public final ModConfigSpec.IntValue enchantLevelPerPower;
        public final ModConfigSpec.IntValue enchantApothQuanta;
        public final ModConfigSpec.IntValue enchantApothArcana;
        // Enchantment table workstation boosts/uncaps
        public final ModConfigSpec.BooleanValue enchantIgnoreEnchanted;
        public final ModConfigSpec.BooleanValue enchantIgnoreConflict;
        public final ModConfigSpec.BooleanValue enchantNoLapis;
        public final ModConfigSpec.BooleanValue enchantLevelGateIgnore;
        public final ModConfigSpec.BooleanValue enchantUncapPower;
        public final ModConfigSpec.IntValue enchantCostPercent;
        public final ModConfigSpec.IntValue enchantRefreshLapis;
        public final ModConfigSpec.BooleanValue enchantPreviewEnabled;
        public final ModConfigSpec.BooleanValue enchantRefreshEnabled;
        public final ModConfigSpec.BooleanValue enchantAllowTreasure;
        // 附魔合并（批量附魔工作站）配置
        public final ModConfigSpec.BooleanValue enchantMergeEnable;
        public final ModConfigSpec.BooleanValue enchantMergeConsumeBook;
        public final ModConfigSpec.IntValue enchantMergeSplitXpCost;
        // 附魔合并新行为：冲突判定 / 视为拥有全部附魔 / 返还书 / 费用调整
        public final ModConfigSpec.BooleanValue enchantMergeCheckConflict;
        public final ModConfigSpec.BooleanValue enchantMergeAssumeAll;
        public final ModConfigSpec.IntValue enchantMergeAssumeAllExtraCost;
        public final ModConfigSpec.BooleanValue enchantMergeKeepRemovedBooks;
        public final ModConfigSpec.IntValue enchantMergeExtraCostPerEnchant;
        public final ModConfigSpec.DoubleValue enchantMergeCostMultiplier;
        public final ModConfigSpec.IntValue enchantMergeCostPercentBonus;

        // Network totem (network only)
        // BD modifications (tweaks applied to Beyond Dimensions)
        public final ModConfigSpec.BooleanValue xpRodTweaksEnabled;
        /** 熔炉烧网络终端：批量烧炼网络内全部可烧炼物品（默认关闭） */
        public final ModConfigSpec.BooleanValue furnaceTerminalSmeltAllEnabled;
        public final ModConfigSpec.IntValue xpRodMaxTargetLevel;
        public final ModConfigSpec.IntValue xpRodGrantBatchSize;
        public final ModConfigSpec.EnumValue<XpGrantMode> xpRodGrantMode;
        /** 桶入网自动分离：含流体的容器入网时拆为"流体 + 空容器"（默认开启） */
        public final ModConfigSpec.BooleanValue bucketSeparatorEnabled;
        /** 网络熔炉熔炼速度倍率（1.0=原速；配方耗时按倍率缩放，越大越快） */
        public final ModConfigSpec.DoubleValue netFurnaceSmeltSpeed;
        /** 服务端：会话级主网络物品计数同步（JEI 任意界面显示/取物；关闭则仅 BD 终端界面生效，默认关闭） */
        public final ModConfigSpec.BooleanValue primaryNetJeiSync;
        /** 维度网络通道（net_pathway）标记槽行数（1~6，全局；标记槽数 = 行数*9） */
        public final ModConfigSpec.IntValue netPathwayFilterRows;
        /** 维度网络方块被破坏时保留配置 NBT（标记槽 + 方块配置；默认开启） */
        public final ModConfigSpec.BooleanValue netedBlockKeepNbt;

        // ===== 网络磁铁（BI 接管吸取范围/间隔，完全自定义档位） =====
        /** 是否使用下方自定义档位列表（false 时用与 BD 等价的默认六档） */
        public final ModConfigSpec.BooleanValue magnetCustomTiersEnabled;
        /** 物品吸取档位列表，格式：名称:半径:间隔（半径 -1 = 整区块） */
        public final ModConfigSpec.ConfigValue<List<? extends String>> magnetItemRangeTiers;
        /** 流体吸取档位列表，格式：名称:半径:间隔（半径 -1 = 整区块） */
        public final ModConfigSpec.ConfigValue<List<? extends String>> magnetFluidRangeTiers;

        // ===== 网络喂食器口渴（Thirst）集成（独立配置区） =====
        /** 每多少 mB 水补充一次 */
        public final ModConfigSpec.IntValue feederThirstMbPerUse;
        /** 各纯度档每次补充的口渴值 */
        public final ModConfigSpec.IntValue feederThirstDirtyThirst;
        public final ModConfigSpec.IntValue feederThirstSlightlyDirtyThirst;
        public final ModConfigSpec.IntValue feederThirstAcceptableThirst;
        public final ModConfigSpec.IntValue feederThirstPurifiedThirst;
        /** 各纯度档每次补充的水饱和 */
        public final ModConfigSpec.IntValue feederThirstDirtyQuenched;
        public final ModConfigSpec.IntValue feederThirstSlightlyDirtyQuenched;
        public final ModConfigSpec.IntValue feederThirstAcceptableQuenched;
        public final ModConfigSpec.IntValue feederThirstPurifiedQuenched;
        /** 原版水(minecraft:water)在喂食器补水时视为的纯度（0脏/1微脏/2可接受/3纯净） */
        public final ModConfigSpec.IntValue feederThirstVanillaWaterPurity;
        /** 同时存在多种口渴系统时，是否对每套各给完整补水数值（默认关：只给一份，补最缺的那套） */
        public final ModConfigSpec.BooleanValue feederThirstSeparateHydration;
        /** 回血模式维持的饥饿/水饱和下限（默认 6，覆盖 LSO 单次回血消耗） */
        public final ModConfigSpec.IntValue feederThirstRegenMinSaturation;

        public final ModConfigSpec.BooleanValue AUTO_TOTEM_ENABLED;
        public final ModConfigSpec.IntValue AUTO_TOTEM_COOLDOWN_SECONDS;
        public final ModConfigSpec.ConfigValue<List<? extends String>> AUTO_TOTEM_DAMAGE_BLACKLIST;
        public final ModConfigSpec.BooleanValue AUTO_TOTEM_RESPECT_BYPASSES;
        public final ModConfigSpec.BooleanValue AUTO_TOTEM_RESTORE_MAX_HEALTH;
        public final ModConfigSpec.BooleanValue AUTO_TOTEM_HEAL_TO_FULL;

        /** 图腾爆发（网络图腾触发后对周围造成范围伤害） */
        public final ModConfigSpec.BooleanValue TOTEM_BURST_ENABLED;
        public final ModConfigSpec.DoubleValue TOTEM_BURST_RADIUS;
        public final ModConfigSpec.DoubleValue TOTEM_BURST_DAMAGE_PERCENT;
        public final ModConfigSpec.BooleanValue TOTEM_BURST_IGNORE_INVULNERABILITY;

        // SetHealth revive (network only, event + mixin implementations)
        // 通用项（冷却 / 伤害黑名单 / 无敌绕过 / 恢复上限 / 回满）复用 auto_totem 分区配置
        public final ModConfigSpec.BooleanValue REVIVE_EVENT_ENABLED;
        public final ModConfigSpec.BooleanValue REVIVE_MIXIN_ENABLED;
        public final ModConfigSpec.BooleanValue REVIVE_EXTRA_TOTEM_ON_SET_HEALTH_DEATH;
        public final ModConfigSpec.IntValue REVIVE_EXTRA_TOTEM_COUNT;
        public final ModConfigSpec.BooleanValue REVIVE_RESET_DEATH_TIME;

        /**
         * ServerConfig 构造函数：按功能分区定义全部配置项及其默认值、校验范围。
         */
        public ServerConfig(ModConfigSpec.Builder builder) {
            builder.comment(ConfigCommentLang.comment("general")).push("general");
            // 命令输出语言
            language = builder
                    .comment(ConfigCommentLang.comment("general.command_language"))
                    .defineEnum("command_language", Language.EN_US);
            // 网络列表每页最大显示数量
            maxNetworksPerPage = builder
                    .comment(ConfigCommentLang.comment("general.max_networks_per_page"))
                    .defineInRange("max_networks_per_page", 10, 1, 100);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("enchant")).push("enchant");
            enchantSeparation = builder
                    .comment(ConfigCommentLang.comment("enchant.separation"))
                    .define("separation", false);
            enchantBaseCost = builder
                    .comment(ConfigCommentLang.comment("enchant.base_cost"))
                    .defineInRange("base_cost", 5, 0, 100);
            enchantLevelMult = builder
                    .comment(ConfigCommentLang.comment("enchant.level_mult"))
                    .defineInRange("level_mult", 1.0, 0.0, 100.0);
            enchantDefaultMult = builder
                    .comment(ConfigCommentLang.comment("enchant.default_mult"))
                    .defineInRange("default_mult", 1.0, 0.0, 100.0);
            enchantHighCostList = builder
                    .comment(ConfigCommentLang.comment("enchant.high_cost"))
                    .defineList("high_cost",
                            Arrays.asList("minecraft:mending:3.0", "minecraft:frost_walker:3.0",
                                    "minecraft:sharpness:1.2", "minecraft:protection:1.2"),
                            obj -> obj instanceof String);
            enchantDebug = builder
                    .comment(ConfigCommentLang.comment("enchant.debug"))
                    .define("debug", false);
            enchantMergeSameLevel = builder
                    .comment(ConfigCommentLang.comment("enchant.merge_same_level"))
                    .define("merge_same_level", false);
            builder.pop();


            builder.comment(ConfigCommentLang.comment("vehicle")).push("vehicle");
            swVehicleEnergyChargeRate = builder
                    .comment(ConfigCommentLang.comment("vehicle.energyChargeRate"))
                    .defineInRange("energyChargeRate", 500000, 0, Integer.MAX_VALUE);
            swVehicleChargeInterval = builder
                    .comment(ConfigCommentLang.comment("vehicle.chargeInterval"))
                    .defineInRange("chargeInterval", 20, 1, 1200);
            swVehicleChargePercentage = builder
                    .comment(ConfigCommentLang.comment("vehicle.chargePercentage"))
                    .defineInRange("chargePercentage", 0.0, 0.0, 100.0);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("ywzj_vehicle")).push("ywzj_vehicle");
            ywzjChargeMode = builder.comment(ConfigCommentLang.comment("ywzj_vehicle.chargeMode")).defineEnum("chargeMode", ChargeMode.FLAT_RATE);
            ywzjFuelSource = builder.comment(ConfigCommentLang.comment("ywzj_vehicle.fuelSource")).defineEnum("fuelSource", FuelSource.FE);
            ywzjVehicleEnergyChargeRate = builder.comment(ConfigCommentLang.comment("ywzj_vehicle.energyChargeRate")).defineInRange("energyChargeRate", 500000, 0, Integer.MAX_VALUE);
            ywzjVehicleChargeInterval = builder.comment(ConfigCommentLang.comment("ywzj_vehicle.chargeInterval")).defineInRange("chargeInterval", 20, 1, 1200);
            ywzjVehicleChargePercentage = builder.comment(ConfigCommentLang.comment("ywzj_vehicle.chargePercentage")).defineInRange("chargePercentage", 5.0, 0.1, 100.0);
            ywzjVehicleEnergyConversion = builder.comment(ConfigCommentLang.comment("ywzj_vehicle.energyConversion")).defineInRange("energyConversion", 1000, 1, Integer.MAX_VALUE);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("blacklist")).push("blacklist");
            ENABLE_ITEM_BLACKLIST = builder.comment(ConfigCommentLang.comment("blacklist.enable")).define("enable", false);
            ITEM_BLACKLIST = builder.comment(ConfigCommentLang.comment("blacklist.items")).defineList("items",
                    Arrays.asList("minecraft:barrier", "minecraft:command_block"), obj -> obj instanceof String);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("craft")).push("craft");
            BLOCK_BD_CONTAINER_READER = builder
                    .comment(ConfigCommentLang.comment("craft.block_bd_reader"))
                    .define("block_bd_reader", true);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("workstations")).push("workstations");
            // 注意：storage 实为 BD 终端界面（网络存储/合成终端），不属于工作台，不受本列表控制
            WORKSTATIONS_ENABLED = builder
                    .comment(ConfigCommentLang.comment("workstations.enabled"))
                    .defineList("enabled",
                            Arrays.asList("craft", "anvil", "cut", "grind", "smith", "enchant", "enchant_merge"),
                            obj -> obj instanceof String);

            // 献祭激活（可选平衡）：开启后除合成台外的工作台需先献祭对应原版工作台激活（网络级）
            builder.comment(ConfigCommentLang.comment("workstations.activation")).push("activation");
            WORKSTATION_ACTIVATION_ENABLED = builder
                    .comment(ConfigCommentLang.comment("workstations.activation.enable"))
                    .define("enable", false);
            WORKSTATION_ACTIVATION_COSTS = builder
                    .comment(ConfigCommentLang.comment("workstations.activation.costs"))
                    .defineList("costs",
                            Arrays.asList(
                                    "anvil:minecraft:anvil:1",
                                    "cut:minecraft:stonecutter:1",
                                    "grind:minecraft:grindstone:1",
                                    "smith:minecraft:smithing_table:1",
                                    "enchant:minecraft:enchanting_table:1",
                                    "enchant_merge:minecraft:enchanting_table:1"),
                            obj -> obj instanceof String);
            builder.pop();
            builder.pop();

            builder.comment(ConfigCommentLang.comment("anvil")).push("anvil");
            anvilCostMode = builder.comment(ConfigCommentLang.comment("anvil.costMode"))
                    .defineEnum("costMode", AnvilChargeMode.POINTS);
            anvilLevelCap = builder.comment(ConfigCommentLang.comment("anvil.levelCap"))
                    .defineInRange("levelCap", 40, 1, Integer.MAX_VALUE);
            anvilPointsCap = builder.comment(ConfigCommentLang.comment("anvil.pointsCap"))
                    .defineInRange("pointsCap", Long.MAX_VALUE, 1, Long.MAX_VALUE);
            anvilBreakLevelMode = builder.comment(ConfigCommentLang.comment("anvil.breakMaxLevelMode"))
                    .defineEnum("breakMaxLevelMode", BreakLevelMode.OFF);
            anvilIgnoreConflict = builder.comment(ConfigCommentLang.comment("anvil.ignoreConflict"))
                    .define("ignoreConflict", false);
            anvilIgnoreSupport = builder.comment(ConfigCommentLang.comment("anvil.ignoreSupport"))
                    .define("ignoreSupport", false);
            anvilUnrestricted = builder.comment(ConfigCommentLang.comment("anvil.unrestricted"))
                    .define("unrestricted", false);
            anvilConflictPenalty = builder.comment(ConfigCommentLang.comment("anvil.conflictPenalty"))
                    .defineInRange("conflictPenalty", 2, 0, 1000000);
            anvilSupportPenalty = builder.comment(ConfigCommentLang.comment("anvil.supportPenalty"))
                    .defineInRange("supportPenalty", 5, 0, 1000000);
            anvilConflictPercent = builder.comment(ConfigCommentLang.comment("anvil.conflictPercent"))
                    .defineInRange("conflictPercent", 0, 0, 1000000);
            anvilSupportPercent = builder.comment(ConfigCommentLang.comment("anvil.supportPercent"))
                    .defineInRange("supportPercent", 0, 0, 1000000);
            anvilBreakLevelPercent = builder.comment(ConfigCommentLang.comment("anvil.breakLevelPercent"))
                    .defineInRange("breakLevelPercent", 0, 0, 1000000);
            anvilUnrestrictedPercent = builder.comment(ConfigCommentLang.comment("anvil.unrestrictedPercent"))
                    .defineInRange("unrestrictedPercent", 100, 0, 1000000);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("enchant")).push("enchant");
            enchantPowerMode = builder.comment(ConfigCommentLang.comment("enchant.powerMode"))
                    .defineEnum("powerMode", EnchantPowerMode.FIXED);
            enchantFixedPower = builder.comment(ConfigCommentLang.comment("enchant.fixedPower"))
                    .defineInRange("fixedPower", 15, 0, 100);
            enchantXpPerPower = builder.comment(ConfigCommentLang.comment("enchant.xpPerPower"))
                    .defineInRange("xpPerPower", 100, 1, Integer.MAX_VALUE);
            enchantLevelPerPower = builder.comment(ConfigCommentLang.comment("enchant.levelPerPower"))
                    .defineInRange("levelPerPower", 1, 1, Integer.MAX_VALUE);
            enchantApothQuanta = builder.comment(ConfigCommentLang.comment("enchant.apothQuanta"))
                    .defineInRange("apothQuanta", 15, 0, 100);
            enchantApothArcana = builder.comment(ConfigCommentLang.comment("enchant.apothArcana"))
                    .defineInRange("apothArcana", 0, 0, 100);
            enchantIgnoreEnchanted = builder.comment(ConfigCommentLang.comment("enchant.ignoreEnchanted"))
                    .define("ignoreEnchanted", false);
            enchantIgnoreConflict = builder.comment(ConfigCommentLang.comment("enchant.ignoreConflict"))
                    .define("ignoreConflict", false);
            enchantNoLapis = builder.comment(ConfigCommentLang.comment("enchant.noLapis"))
                    .define("noLapis", false);
            enchantLevelGateIgnore = builder.comment(ConfigCommentLang.comment("enchant.levelGateIgnore"))
                    .define("levelGateIgnore", false);
            enchantUncapPower = builder.comment(ConfigCommentLang.comment("enchant.uncapPower"))
                    .define("uncapPower", false);
            enchantCostPercent = builder.comment(ConfigCommentLang.comment("enchant.costPercent"))
                    .defineInRange("costPercent", 100, 10, 500);
            enchantRefreshLapis = builder.comment(ConfigCommentLang.comment("enchant.refreshLapis"))
                    .defineInRange("refreshLapis", 1, 0, 64);
            enchantPreviewEnabled = builder.comment(ConfigCommentLang.comment("enchant.previewEnabled"))
                    .define("previewEnabled", true);
            enchantRefreshEnabled = builder.comment(ConfigCommentLang.comment("enchant.refreshEnabled"))
                    .define("refreshEnabled", true);
            enchantAllowTreasure = builder.comment(ConfigCommentLang.comment("enchant.allowTreasure"))
                    .define("allowTreasure", false);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("enchant_merge")).push("enchant_merge");
            enchantMergeEnable = builder.comment(ConfigCommentLang.comment("enchant_merge.enable"))
                    .define("enable", true);
            enchantMergeConsumeBook = builder.comment(ConfigCommentLang.comment("enchant_merge.consume_original_book"))
                    .define("consume_original_book", false);
            enchantMergeSplitXpCost = builder.comment(ConfigCommentLang.comment("enchant_merge.split_xp_cost"))
                    .defineInRange("split_xp_cost", 0, 0, Integer.MAX_VALUE);
            enchantMergeCheckConflict = builder.comment(ConfigCommentLang.comment("enchant_merge.check_conflict"))
                    .define("check_conflict", true);
            enchantMergeAssumeAll = builder.comment(ConfigCommentLang.comment("enchant_merge.assume_all_enchantments"))
                    .define("assume_all_enchantments", false);
            enchantMergeAssumeAllExtraCost = builder.comment(ConfigCommentLang.comment("enchant_merge.all_enchant_extra_cost"))
                    .defineInRange("all_enchant_extra_cost", 3000, 0, Integer.MAX_VALUE);
            enchantMergeKeepRemovedBooks = builder.comment(ConfigCommentLang.comment("enchant_merge.keep_removed_books"))
                    .define("keep_removed_books", false);
            enchantMergeExtraCostPerEnchant = builder.comment(ConfigCommentLang.comment("enchant_merge.extra_cost_per_enchant"))
                    .defineInRange("extra_cost_per_enchant", 0, 0, Integer.MAX_VALUE);
            enchantMergeCostMultiplier = builder.comment(ConfigCommentLang.comment("enchant_merge.cost_multiplier"))
                    .defineInRange("cost_multiplier", 1.0D, 0.0D, 1000.0D);
            enchantMergeCostPercentBonus = builder.comment(ConfigCommentLang.comment("enchant_merge.cost_percent_bonus"))
                    .defineInRange("cost_percent_bonus", 0, 0, 1000);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("bd_tweaks")).push("bd_tweaks");
            xpRodTweaksEnabled = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_rod_enabled"))
                    .define("xp_rod_enabled", true);
            furnaceTerminalSmeltAllEnabled = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.furnace_terminal_smelt_all"))
                    .define("furnace_terminal_smelt_all", false);
            xpRodMaxTargetLevel = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.max_target_level"))
                    .defineInRange("max_target_level", 238609312, 0, Integer.MAX_VALUE);
            xpRodGrantBatchSize = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.grant_batch_size"))
                    .defineInRange("grant_batch_size", Integer.MAX_VALUE - 1, 0, Integer.MAX_VALUE);
            xpRodGrantMode = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.grant_mode"))
                    .defineEnum("grant_mode", XpGrantMode.BATCH);
            bucketSeparatorEnabled = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.bucket_separator_enabled"))
                    .define("bucket_separator_enabled", true);
            netFurnaceSmeltSpeed = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.net_furnace_smelt_speed"))
                    .defineInRange("net_furnace_smelt_speed", 1.0D, 0.1D, 100.0D);
            primaryNetJeiSync = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.primary_net_jei_sync"))
                    .define("primary_net_jei_sync", false);
            netPathwayFilterRows = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.net_pathway_filter_rows"))
                    .defineInRange("net_pathway_filter_rows", 3, 1, 6);
            netedBlockKeepNbt = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.neted_block_keep_nbt"))
                    .define("neted_block_keep_nbt", true);
            builder.pop();

            // ===== 网络磁铁（BI 接管：完全自定义档位吸取范围） =====
            builder.comment(ConfigCommentLang.comment("magnet")).push("magnet");
            magnetCustomTiersEnabled = builder
                    .comment(ConfigCommentLang.comment("magnet.custom_tiers_enabled"))
                    .define("custom_tiers_enabled", true);
            magnetItemRangeTiers = builder
                    .comment(ConfigCommentLang.comment("magnet.item_range_tiers"))
                    .defineList("item_range_tiers",
                            Arrays.asList(
                                    "lowest:2:0",
                                    "low:3:0",
                                    "mid:5:2",
                                    "high:7:5",
                                    "highest:10:10",
                                    "chunk:-1:1200"),
                            obj -> obj instanceof String);
            magnetFluidRangeTiers = builder
                    .comment(ConfigCommentLang.comment("magnet.fluid_range_tiers"))
                    .defineList("fluid_range_tiers",
                            Arrays.asList(
                                    "lowest:2:0",
                                    "low:3:0",
                                    "mid:5:10",
                                    "high:7:20",
                                    "highest:10:50",
                                    "chunk:-1:1200"),
                            obj -> obj instanceof String);
            builder.pop();

            // ===== 网络喂食器口渴（Thirst）集成 =====
            builder.comment(ConfigCommentLang.comment("feeder_thirst")).push("feeder_thirst");
            feederThirstMbPerUse = builder
                    .comment(ConfigCommentLang.comment("feeder_thirst.mb_per_use"))
                    .defineInRange("mb_per_use", 50, 1, 1000000);
            feederThirstDirtyThirst = builder
                    .comment(ConfigCommentLang.comment("feeder_thirst.dirty_thirst"))
                    .defineInRange("dirty_thirst", 1, 0, 20);
            feederThirstDirtyQuenched = builder
                    .comment(ConfigCommentLang.comment("feeder_thirst.dirty_quenched"))
                    .defineInRange("dirty_quenched", 0, 0, 20);
            feederThirstSlightlyDirtyThirst = builder
                    .comment(ConfigCommentLang.comment("feeder_thirst.slightly_dirty_thirst"))
                    .defineInRange("slightly_dirty_thirst", 1, 0, 20);
            feederThirstSlightlyDirtyQuenched = builder
                    .comment(ConfigCommentLang.comment("feeder_thirst.slightly_dirty_quenched"))
                    .defineInRange("slightly_dirty_quenched", 1, 0, 20);
            feederThirstAcceptableThirst = builder
                    .comment(ConfigCommentLang.comment("feeder_thirst.acceptable_thirst"))
                    .defineInRange("acceptable_thirst", 2, 0, 20);
            feederThirstAcceptableQuenched = builder
                    .comment(ConfigCommentLang.comment("feeder_thirst.acceptable_quenched"))
                    .defineInRange("acceptable_quenched", 2, 0, 20);
            feederThirstPurifiedThirst = builder
                    .comment(ConfigCommentLang.comment("feeder_thirst.purified_thirst"))
                    .defineInRange("purified_thirst", 3, 0, 20);
            feederThirstPurifiedQuenched = builder
                    .comment(ConfigCommentLang.comment("feeder_thirst.purified_quenched"))
                    .defineInRange("purified_quenched", 3, 0, 20);
            feederThirstVanillaWaterPurity = builder
                    .comment(ConfigCommentLang.comment("feeder_thirst.vanilla_water_purity"))
                    .defineInRange("vanilla_water_purity", 1, 0, 3);
            feederThirstSeparateHydration = builder
                    .comment(ConfigCommentLang.comment("feeder_thirst.separate_hydration"))
                    .define("separate_hydration", false);
            feederThirstRegenMinSaturation = builder
                    .comment(ConfigCommentLang.comment("feeder_thirst.regen_min_saturation"))
                    .defineInRange("regen_min_saturation", 6, 0, 20);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("auto_totem")).push("auto_totem");
            AUTO_TOTEM_ENABLED = builder
                    .comment(ConfigCommentLang.comment("auto_totem.enabled"))
                    .define("enabled", true);
            AUTO_TOTEM_COOLDOWN_SECONDS = builder
                    .comment(ConfigCommentLang.comment("auto_totem.cooldown_seconds"))
                    .defineInRange("cooldown_seconds", 10, 0, 3600);
            AUTO_TOTEM_DAMAGE_BLACKLIST = builder
                    .comment(ConfigCommentLang.comment("auto_totem.damage_blacklist"))
                    .defineList("damage_blacklist",
                            Arrays.asList("outOfWorld", "fellOutOfWorld", "genericKill", "command"),
                            obj -> obj instanceof String);
            AUTO_TOTEM_RESPECT_BYPASSES = builder
                    .comment(ConfigCommentLang.comment("auto_totem.respect_bypasses_invulnerability"))
                    .define("respect_bypasses_invulnerability", false);
            AUTO_TOTEM_RESTORE_MAX_HEALTH = builder
                    .comment(ConfigCommentLang.comment("auto_totem.restore_max_health"))
                    .define("restore_max_health", true);
            AUTO_TOTEM_HEAL_TO_FULL = builder
                    .comment(ConfigCommentLang.comment("auto_totem.heal_to_full"))
                    .define("heal_to_full", false);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("totem_burst")).push("totem_burst");
            TOTEM_BURST_ENABLED = builder
                    .comment(ConfigCommentLang.comment("totem_burst.enabled"))
                    .define("enabled", false);
            TOTEM_BURST_RADIUS = builder
                    .comment(ConfigCommentLang.comment("totem_burst.radius"))
                    .defineInRange("radius", 6.0D, 0.5D, 64.0D);
            TOTEM_BURST_DAMAGE_PERCENT = builder
                    .comment(ConfigCommentLang.comment("totem_burst.damage_percent"))
                    .defineInRange("damage_percent", 50.0D, 0.0D, 1000.0D);
            TOTEM_BURST_IGNORE_INVULNERABILITY = builder
                    .comment(ConfigCommentLang.comment("totem_burst.ignore_invulnerability"))
                    .define("ignore_invulnerability", true);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("revive")).push("revive");
            REVIVE_EVENT_ENABLED = builder
                    .comment(ConfigCommentLang.comment("revive.event_enabled"))
                    .define("event_enabled", true);
            REVIVE_MIXIN_ENABLED = builder
                    .comment(ConfigCommentLang.comment("revive.mixin_enabled"))
                    .define("mixin_enabled", true);
            REVIVE_EXTRA_TOTEM_ON_SET_HEALTH_DEATH = builder
                    .comment(ConfigCommentLang.comment("revive.extra_totem_on_set_health_death"))
                    .define("extra_totem_on_set_health_death", true);
            REVIVE_EXTRA_TOTEM_COUNT = builder
                    .comment(ConfigCommentLang.comment("revive.extra_totem_count"))
                    .defineInRange("extra_totem_count", 1, 0, 64);
            REVIVE_RESET_DEATH_TIME = builder
                    .comment(ConfigCommentLang.comment("revive.reset_death_time"))
                    .define("reset_death_time", true);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("tacz_ammo")).push("tacz_ammo");
            TACZ_AMMO_POLL_ENABLED = builder
                    .comment(ConfigCommentLang.comment("tacz_ammo.ammo_poll_enabled"))
                    .define("ammo_poll_enabled", true);
            TACZ_AMMO_POLL_INTERVAL_TICKS = builder
                    .comment(ConfigCommentLang.comment("tacz_ammo.ammo_poll_interval_ticks"))
                    .defineInRange("ammo_poll_interval_ticks", 10, 1, 1200);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("sw_ammo")).push("sw_ammo");
            SW_AMMO_POLL_ENABLED = builder
                    .comment(ConfigCommentLang.comment("sw_ammo.ammo_poll_enabled"))
                    .define("ammo_poll_enabled", true);
            SW_AMMO_POLL_INTERVAL_TICKS = builder
                    .comment(ConfigCommentLang.comment("sw_ammo.ammo_poll_interval_ticks"))
                    .defineInRange("ammo_poll_interval_ticks", 10, 1, 1200);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("energy_ammo")).push("energy_ammo");
            ENERGY_AMMO_CHARGE_ENABLED = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_enabled"))
                    .define("charge_enabled", true);
            ENERGY_AMMO_CHARGE_INTERVAL = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_interval"))
                    .defineInRange("charge_interval", 20, 1, 1200);
            ENERGY_AMMO_CHARGE_RATE = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_rate"))
                    .defineInRange("charge_rate", 10000, 1, Integer.MAX_VALUE);
            ENERGY_AMMO_CHARGE_MODE = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_mode"))
                    .defineEnum("charge_mode", EnergyChargeMode.RATE);
            ENERGY_AMMO_CHARGE_PERCENTAGE = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_percentage"))
                    .defineInRange("charge_percentage", 10.0, 0.0, 100.0);
            ENERGY_AMMO_CHARGE_WHITELIST = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_whitelist"))
                    .defineList("charge_whitelist", Arrays.asList(
                            "ae2:wireless_terminal",
                            "ae2:wireless_crafting_terminal",
                            "ae2wtlib:wireless_crafting_terminal",
                            "ae2wtlib:wireless_pattern_access_terminal",
                            "ae2wtlib:wireless_pattern_encoding_terminal",
                            "ae2wtlib:wireless_universal_terminal",
                            "wcwt:wireless_comprehensive_work_terminal",
                            "refinedstorage:wireless_grid",
                            "refinedstorage:wireless_fluid_grid",
                            "refinedstorage:wireless_crafting_monitor"
                    ), obj -> obj instanceof String);
            ENERGY_AMMO_CHARGE_CURIOS = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_curios"))
                    .define("charge_curios", true);
            ENERGY_AMMO_CHARGE_MAID_BAUBLES = builder
                    .comment(ConfigCommentLang.comment("energy_ammo.charge_maid_baubles"))
                    .define("charge_maid_baubles", true);
            builder.pop();
        }
    }

    public static Language getCommandLanguage() {
        try {
            return SERVER.language.get();
        } catch (IllegalStateException e) {
            return Language.EN_US;
        }
    }
    /** 返回命令列表每页显示的网络数量上限。 */
    public static int maxNetworksPerPage() { return SERVER.maxNetworksPerPage.get(); }
    public static boolean enableItemBlacklist() { return SERVER.ENABLE_ITEM_BLACKLIST.get(); }
    public static List<? extends String> itemBlacklist() { return SERVER.ITEM_BLACKLIST.get(); }

    /** 是否阻止 taczaddon 将 BD 网络方块识别为合成材料容器源（默认开启） */
    public static boolean blockBdContainerReader() { return SERVER.BLOCK_BD_CONTAINER_READER.get(); }

    /** 工作台是否在服务端启用列表中（ID 忽略大小写精确匹配；null/空列表视为全部禁用） */
    public static boolean isWorkstationEnabled(String id) {
        if (id == null || id.isEmpty()) return false;
        for (String s : SERVER.WORKSTATIONS_ENABLED.get()) {
            if (s != null && s.equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    /** 服务端启用的工作站 ID 列表（原始配置值，供同步包下发给客户端） */
    public static List<String> workstationsEnabledList() {
        List<String> out = new java.util.ArrayList<>();
        for (String s : SERVER.WORKSTATIONS_ENABLED.get()) {
            if (s != null && !s.isEmpty()) out.add(s);
        }
        return out;
    }

    /** 是否启用工作台献祭激活（可选平衡项；默认关闭） */
    public static boolean isWorkstationActivationEnabled() { return SERVER.WORKSTATION_ACTIVATION_ENABLED.get(); }

    /** 工作台献祭成本列表（格式 "<工作台ID>:<物品ID>:<数量>"） */
    public static List<? extends String> workstationActivationCosts() { return SERVER.WORKSTATION_ACTIVATION_COSTS.get(); }

    /**
     * 解析某工作台的献祭成本物品（格式 {@code <工作台ID>:<命名空间>:<路径>:<数量>}）。
     * 未配置/物品无效时返回 {@link net.minecraft.world.item.ItemStack#EMPTY}（视为无需献祭，直接激活）。
     */
    public static net.minecraft.world.item.ItemStack getWorkstationActivationCost(String id) {
        if (id == null || id.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        for (String raw : workstationActivationCosts()) {
            if (raw == null) continue;
            String[] parts = raw.split(":", 4);
            if (parts.length != 4 || !parts[0].trim().equalsIgnoreCase(id)) continue;
            var rl = net.minecraft.resources.ResourceLocation.tryParse(parts[1].trim() + ":" + parts[2].trim());
            if (rl == null) continue;
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
            int count;
            try { count = Math.max(1, Integer.parseInt(parts[3].trim())); } catch (NumberFormatException e) { count = 1; }
            return new net.minecraft.world.item.ItemStack(item, count);
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
    public static int vehicleChargeInterval() { return SERVER.swVehicleChargeInterval.get(); }
    public static double vehicleChargePercentage() { return SERVER.swVehicleChargePercentage.get(); }

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
    public static EnchantPowerMode enchantPowerMode() { return SERVER.enchantPowerMode.get(); }
    public static int enchantFixedPower() { return SERVER.enchantFixedPower.get(); }
    public static int enchantXpPerPower() { return SERVER.enchantXpPerPower.get(); }
    public static int enchantLevelPerPower() { return SERVER.enchantLevelPerPower.get(); }
    public static int enchantApothQuanta() { return SERVER.enchantApothQuanta.get(); }
    public static int enchantApothArcana() { return SERVER.enchantApothArcana.get(); }
    public static boolean enchantIgnoreEnchanted() { return SERVER.enchantIgnoreEnchanted.get(); }
    public static boolean enchantIgnoreConflict() { return SERVER.enchantIgnoreConflict.get(); }
    public static boolean enchantNoLapis() { return SERVER.enchantNoLapis.get(); }
    public static boolean enchantLevelGateIgnore() { return SERVER.enchantLevelGateIgnore.get(); }
    public static boolean enchantUncapPower() { return SERVER.enchantUncapPower.get(); }
    public static int enchantCostPercent() { return SERVER.enchantCostPercent.get(); }
    public static int enchantRefreshLapis() { return SERVER.enchantRefreshLapis.get(); }

    /** 附魔合并工作站总开关 */
    public static boolean enchantMergeEnable() { return SERVER.enchantMergeEnable.get(); }
    /** 附魔合并：拆分时是否连原附魔书载体一起消耗（false = 保留原载体，每步 1 本普通书；true = 每步 2 本） */
    public static boolean enchantMergeConsumeBook() { return SERVER.enchantMergeConsumeBook.get(); }
    /** 附魔合并：每步拆分消耗的经验点（0 = 不消耗） */
    public static int enchantMergeSplitXpCost() { return SERVER.enchantMergeSplitXpCost.get(); }
    /** 附魔合并：是否启用冲突判定（默认开；关=允许互斥附魔共存） */
    public static boolean enchantMergeCheckConflict() { return SERVER.enchantMergeCheckConflict.get(); }
    /** 附魔合并：是否视网络拥有装备可用的全部附魔（无书时按高额经验计费） */
    public static boolean enchantMergeAssumeAll() { return SERVER.enchantMergeAssumeAll.get(); }
    /** 附魔合并：视为拥有时为“无书”附魔每项收取的额外经验点 */
    public static int enchantMergeAssumeAllExtraCost() { return SERVER.enchantMergeAssumeAllExtraCost.get(); }
    /** 附魔合并：清除/降级时是否把移除的附魔组装成附魔书放入网络 */
    public static boolean enchantMergeKeepRemovedBooks() { return SERVER.enchantMergeKeepRemovedBooks.get(); }
    /** 附魔合并：每成功合并一个附魔附加的固定经验点 */
    public static int enchantMergeExtraCostPerEnchant() { return SERVER.enchantMergeExtraCostPerEnchant.get(); }
    /** 附魔合并：总费用倍率 */
    public static double enchantMergeCostMultiplier() { return SERVER.enchantMergeCostMultiplier.get(); }
    /** 附魔合并：总费用百分比加成（0-1000） */
    public static int enchantMergeCostPercentBonus() { return SERVER.enchantMergeCostPercentBonus.get(); }
    public static boolean enchantPreviewEnabled() { return SERVER.enchantPreviewEnabled.get(); }
    public static boolean enchantRefreshEnabled() { return SERVER.enchantRefreshEnabled.get(); }
    public static boolean enchantAllowTreasure() { return SERVER.enchantAllowTreasure.get(); }
    public static boolean enchantDebug() { return SERVER.enchantDebug.get(); }
    /** 附魔分离：单附魔书同类同级自动合并（默认关闭） */
    public static boolean enchantMergeSameLevel() { return SERVER.enchantMergeSameLevel.get(); }

    /** BD 修改：经验棒修改总开关（等级上限/分批/直设，关闭 = BD 原行为） */
    public static boolean xpRodTweaksEnabled() { return SERVER.xpRodTweaksEnabled.get(); }

    /** BD 修改：桶入网自动分离（含流体的容器拆为流体 + 空容器；默认开启） */
    public static boolean bucketSeparatorEnabled() { return SERVER.bucketSeparatorEnabled.get(); }

    // ===== 网络喂食器口渴（Thirst）集成 =====

    /** 每多少 mB 水补充一次 */
    public static int feederThirstMbPerUse() { return SERVER.feederThirstMbPerUse.get(); }

    /** 指定纯度档每次补充的口渴值 */
    public static int feederThirstThirstPerUse(int purity) {
        return switch (purity) {
            case 0 -> SERVER.feederThirstDirtyThirst.get();
            case 1 -> SERVER.feederThirstSlightlyDirtyThirst.get();
            case 2 -> SERVER.feederThirstAcceptableThirst.get();
            case 3 -> SERVER.feederThirstPurifiedThirst.get();
            default -> SERVER.feederThirstDirtyThirst.get();
        };
    }

    /** 指定纯度档每次补充的水饱和 */
    public static int feederThirstQuenchedPerUse(int purity) {
        return switch (purity) {
            case 0 -> SERVER.feederThirstDirtyQuenched.get();
            case 1 -> SERVER.feederThirstSlightlyDirtyQuenched.get();
            case 2 -> SERVER.feederThirstAcceptableQuenched.get();
            case 3 -> SERVER.feederThirstPurifiedQuenched.get();
            default -> SERVER.feederThirstDirtyQuenched.get();
        };
    }

    /** 原版水在喂食器补水时视为的纯度 */
    public static int feederThirstVanillaWaterPurity() { return SERVER.feederThirstVanillaWaterPurity.get(); }

    /** 同时存在多种口渴系统时，是否对每套系统各给完整补水数值 */
    public static boolean feederThirstSeparateHydration() { return SERVER.feederThirstSeparateHydration.get(); }

    /** 回血模式维持的饥饿/水饱和下限 */
    public static int feederThirstRegenMinSaturation() { return SERVER.feederThirstRegenMinSaturation.get(); }

    /** BD 修改：熔炉烧网络终端触发批量烧炼（默认关闭；终端不消耗，产物即终端，取出时触发） */
    public static boolean furnaceTerminalSmeltAllEnabled() { return SERVER.furnaceTerminalSmeltAllEnabled.get(); }

    /** 网络熔炉熔炼速度倍率（1.0=原速） */
    public static double netFurnaceSmeltSpeed() { return SERVER.netFurnaceSmeltSpeed.get(); }

    /** 服务端：会话级主网络物品计数同步（JEI 任意界面生效；默认关闭 = 仅 BD 终端界面） */
    public static boolean primaryNetJeiSync() { return SERVER.primaryNetJeiSync.get(); }

    /** 维度网络通道（net_pathway）标记槽行数（1~6，全局） */
    public static int netPathwayFilterRows() { return SERVER.netPathwayFilterRows.get(); }

    /** 维度网络方块被破坏时保留配置 NBT（标记槽 + 方块配置） */
    public static boolean netedBlockKeepNbt() { return SERVER.netedBlockKeepNbt.get(); }

    /** 网络磁铁是否使用自定义档位列表 */
    public static boolean magnetCustomTiersEnabled() { return SERVER.magnetCustomTiersEnabled.get(); }

    /** 网络磁铁物品吸取档位列表（名称:半径:间隔；半径 -1 = 整区块） */
    public static List<? extends String> magnetItemRangeTiers() { return SERVER.magnetItemRangeTiers.get(); }

    /** 网络磁铁流体吸取档位列表（名称:半径:间隔；半径 -1 = 整区块） */
    public static List<? extends String> magnetFluidRangeTiers() { return SERVER.magnetFluidRangeTiers.get(); }

    /** 经验棒可设定的目标等级上限（默认 21863，受经验总量 int 边界约束） */
    public static int xpRodMaxTargetLevel() { return SERVER.xpRodMaxTargetLevel.get(); }

    /** 经验棒每 tick 发放批次上限（0 = 不分批，一次性发放安全量） */
    public static int xpRodGrantBatchSize() { return SERVER.xpRodGrantBatchSize.get(); }

    /** 经验棒经验给予方式（BATCH 分批 / DIRECT 直设式） */
    public static XpGrantMode xpRodGrantMode() { return SERVER.xpRodGrantMode.get(); }

    public static boolean autoTotemEnabled() { return SERVER.AUTO_TOTEM_ENABLED.get(); }
    public static int autoTotemCooldownSeconds() { return SERVER.AUTO_TOTEM_COOLDOWN_SECONDS.get(); }
    /** 返回网络图腾不触发的伤害类型黑名单。 */
    public static List<? extends String> autoTotemDamageBlacklist() { return SERVER.AUTO_TOTEM_DAMAGE_BLACKLIST.get(); }
    public static boolean autoTotemRespectBypassesInvulnerability() { return SERVER.AUTO_TOTEM_RESPECT_BYPASSES.get(); }
    public static boolean autoTotemRestoreMaxHealth() { return SERVER.AUTO_TOTEM_RESTORE_MAX_HEALTH.get(); }
    public static boolean autoTotemHealToFull() { return SERVER.AUTO_TOTEM_HEAL_TO_FULL.get(); }

    /** 图腾爆发开关（网络图腾触发后范围伤害） */
    public static boolean totemBurstEnabled() { return SERVER.TOTEM_BURST_ENABLED.get(); }
    /** 图腾爆发半径（格） */
    public static double totemBurstRadius() { return SERVER.TOTEM_BURST_RADIUS.get(); }
    /** 图腾爆发伤害占触发者最大生命的百分比 */
    public static double totemBurstDamagePercent() { return SERVER.TOTEM_BURST_DAMAGE_PERCENT.get(); }
    /** 图腾爆发是否无视无敌帧 */
    public static boolean totemBurstIgnoreInvulnerability() { return SERVER.TOTEM_BURST_IGNORE_INVULNERABILITY.get(); }

    /** 事件版复活实现开关（配置 false 时不注册处理器，类不加载） */
    public static boolean reviveEventEnabled() { return SERVER.REVIVE_EVENT_ENABLED.get(); }
    /** Mixin 版复活实现开关（注入 checkTotemDeathProtection，配置 false 时方法体内直接返回） */
    public static boolean reviveMixinEnabled() { return SERVER.REVIVE_MIXIN_ENABLED.get(); }
    public static boolean reviveExtraTotemOnSetHealthDeath() { return SERVER.REVIVE_EXTRA_TOTEM_ON_SET_HEALTH_DEATH.get(); }
    public static int reviveExtraTotemCount() { return SERVER.REVIVE_EXTRA_TOTEM_COUNT.get(); }
    public static boolean reviveResetDeathTime() { return SERVER.REVIVE_RESET_DEATH_TIME.get(); }

    public static ChargeMode ywzjChargeMode() { return SERVER.ywzjChargeMode.get(); }
    public static FuelSource ywzjFuelSource() { return SERVER.ywzjFuelSource.get(); }
    public static int ywzjVehicleEnergyChargeRate() { return SERVER.ywzjVehicleEnergyChargeRate.get(); }
    public static int ywzjVehicleChargeInterval() { return SERVER.ywzjVehicleChargeInterval.get(); }
    public static double ywzjVehicleChargePercentage() { return SERVER.ywzjVehicleChargePercentage.get(); }
    public static int ywzjVehicleEnergyConversion() { return SERVER.ywzjVehicleEnergyConversion.get(); }

    public static boolean taczAmmoPollEnabled() { return SERVER.TACZ_AMMO_POLL_ENABLED.get(); }
    public static int taczAmmoPollIntervalTicks() { return SERVER.TACZ_AMMO_POLL_INTERVAL_TICKS.get(); }

    public static boolean swAmmoPollEnabled() { return SERVER.SW_AMMO_POLL_ENABLED.get(); }
    public static int swAmmoPollIntervalTicks() { return SERVER.SW_AMMO_POLL_INTERVAL_TICKS.get(); }
    public static boolean energyAmmoChargeEnabled() { return SERVER.ENERGY_AMMO_CHARGE_ENABLED.get(); }
    public static int energyAmmoChargeInterval() { return SERVER.ENERGY_AMMO_CHARGE_INTERVAL.get(); }
    public static int energyAmmoChargeRate() { return SERVER.ENERGY_AMMO_CHARGE_RATE.get(); }
    public static List<? extends String> energyAmmoChargeWhitelist() { return SERVER.ENERGY_AMMO_CHARGE_WHITELIST.get(); }
    public static boolean energyAmmoChargeCuriosEnabled() { return SERVER.ENERGY_AMMO_CHARGE_CURIOS.get(); }
    public static boolean energyAmmoChargeMaidBaublesEnabled() { return SERVER.ENERGY_AMMO_CHARGE_MAID_BAUBLES.get(); }
    public static EnergyChargeMode energyAmmoChargeMode() { return SERVER.ENERGY_AMMO_CHARGE_MODE.get(); }
    public static double energyAmmoChargePercentage() { return SERVER.ENERGY_AMMO_CHARGE_PERCENTAGE.get(); }
}

