package com.solr98.beyondintegration;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import com.solr98.beyondintegration.core.config.ConfigCommentLang;
import com.solr98.beyondintegration.core.sync.NetDataType;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模组通用配置（ForgeConfigSpec 服务端配置）。
 * 集中定义全部可配置项：语言、网络列表分页、附魔分离、载具充能、
 * 物品黑名单、合成冷却、弹药提取映射、TACZ/SW 弹药轮询、
 * 铁砧工作台计费与网络图腾等，并提供静态访问入口。
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

    /** 物品装备位网络充电模式：固定值 / 缺失能量百分比 / 百分比 + 固定值 */
    public enum EnergyChargeMode
    {
        RATE, PERCENTAGE, PERCENTAGE_PLUS_RATE
    }

    /**
     * 附魔台（网络化）附魔功率来源：
     * FIXED=固定功率；NETWORK_XP=网络经验流体换算；PLAYER_LEVEL=玩家经验等级换算。
     */
    public enum EnchantPowerMode
    {
        FIXED, NETWORK_XP, PLAYER_LEVEL
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

    /**
     * 经验棒经验给予方式：
     * BATCH  = 分批 giveExperiencePoints（每 tick 一批，合法升级上限 238609312；
     *          再往上升级所需经验超过 int 上限，经验条会异常）；
     * DIRECT = 直设式（等级计算 + 设定等级/进度 + 网络经验补差），不依赖原版升级公式。
     */
    public enum XpGrantMode
    {
        BATCH, DIRECT
    }

    /**
     * 经验棒自动修复档位（自动用网络经验修复玩家装备）：
     * LOW    = 每次最多 1 XP，间隔 20 tick；
     * NORMAL = 每次最多 4 XP，间隔 10 tick；
     * HIGH   = 每次最多 16 XP，间隔 5 tick；
     * MAX    = 每次最多 64 XP，每 tick。
     * 每 1 XP 修复 2 点耐久（与原版经验修补一致）。
     */
    public enum XpRepairTier
    {
        LOW(1, 20), NORMAL(4, 10), HIGH(16, 5), MAX(64, 1);

        private final int xpPerAction;
        private final int intervalTicks;

        XpRepairTier(int xpPerAction, int intervalTicks)
        {
            this.xpPerAction = xpPerAction;
            this.intervalTicks = intervalTicks;
        }

        /** 每次修复可消耗的 XP 上限。 */
        public int xpPerAction() { return xpPerAction; }

        /** 修复间隔（tick）。 */
        public int intervalTicks() { return intervalTicks; }
    }

    /**
     * 经验棒自动修复的“每次修复耐久量”模式（仅作用于带“经验修补”附魔的装备）：
     * PERCENT  = 每次修复最大耐久的百分比（各档位 xp_repair_tier_*_percent）；
     * ABSOLUTE = 每次修复固定耐久值（xp_repair_absolute）。
     * 二者选其一，实际修复量再受剩余损伤与当次 XP 预算限制。
     */
    public enum XpRepairAmountMode
    {
        PERCENT, ABSOLUTE
    }

    /**
     * 主网络同步范围：
     * ITEMS      = 仅物品键（默认，行为同旧版）；
     * STORAGE    = 所有 storage 键（含能量 / 灵魂）；
     * STORAGE_EXT= storage 全键 + NetworkAmmoData 扩展（虚拟弹药 / 开关等）。
     */
    public enum PrimaryNetSyncScope
    {
        ITEMS, STORAGE, STORAGE_EXT;

        /** 服务端过滤：该存储键是否在同步范围内。 */
        public boolean covers(IStackKey<?> key)
        {
            return switch (this)
            {
                case ITEMS -> key instanceof ItemStackKey;
                case STORAGE, STORAGE_EXT -> true;
            };
        }

        /** 客户端能力判断：该范围是否覆盖某数据类型。 */
        public boolean supports(NetDataType type)
        {
            return switch (this)
            {
                case ITEMS -> type == NetDataType.ITEM;
                case STORAGE -> type == NetDataType.ITEM || type == NetDataType.ENERGY || type == NetDataType.SOUL;
                case STORAGE_EXT -> true;
            };
        }

        /** 该范围覆盖的类型位掩码（作为同步包 typeMask：声明本范围的可用类型）。 */
        public int mask()
        {
            return switch (this)
            {
                case ITEMS -> NetDataType.ITEM.bit();
                case STORAGE -> NetDataType.ITEM.bit() | NetDataType.ENERGY.bit() | NetDataType.SOUL.bit();
                case STORAGE_EXT -> NetDataType.allBits();
            };
        }

        /** 是否包含扩展类型（虚拟弹药 / 开关等 NetworkAmmoData 数据）。 */
        public boolean hasExt()
        {
            return this == STORAGE_EXT;
        }
    }

    /** 配置项定义类：在构造器中分区注册全部配置条目 */
    public static class ServerConfig
    {
        public final ForgeConfigSpec.EnumValue<Language> language;
        public final ForgeConfigSpec.IntValue maxNetworksPerPage;


        public final ForgeConfigSpec.BooleanValue ENABLE_ITEM_BLACKLIST;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;
        public final ForgeConfigSpec.IntValue CRAFT_COOLDOWN_MS;
        public final ForgeConfigSpec.IntValue CRAFT_MAX_DEPTH;
        public final ForgeConfigSpec.BooleanValue BLOCK_BD_CONTAINER_READER;

        /** 服务端启用的工作台 ID 列表（含 storage/craft/anvil/cut/grind/smith/enchant） */
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> WORKSTATIONS_ENABLED;

        // 工作台献祭激活（可选平衡项）：开启后 anvil/cut/grind/smith/enchant 需先献祭对应原版工作台激活（网络级）
        public final ForgeConfigSpec.BooleanValue WORKSTATION_ACTIVATION_ENABLED;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> WORKSTATION_ACTIVATION_COSTS;

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

        public final ForgeConfigSpec.BooleanValue ENABLE_ENCHANTMENT_SEPARATION;


        // Enchantment separation debug
        public final ForgeConfigSpec.BooleanValue ENCHANTMENT_SEPARATION_DEBUG;
        /** 附魔分离：单附魔书同类同级自动合并（两本 L 级 → 一本 L+1 级，铁砧费用转点数，默认关闭） */
        public final ForgeConfigSpec.BooleanValue ENCHANTMENT_SEPARATION_MERGE_SAME_LEVEL;
        /** 附魔分离：拦截链最高重入次数（同级合并级联的深度上限，默认 100，最高 255） */
        public final ForgeConfigSpec.IntValue ENCHANTMENT_SEPARATION_MAX_REENTRANCY;
        public final ForgeConfigSpec.IntValue ENCHANTMENT_SEPARATION_BASE_COST;
        public final ForgeConfigSpec.IntValue ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER;
        public final ForgeConfigSpec.DoubleValue DEFAULT_ENCHANTMENT_MULTIPLIER;
        public final ForgeConfigSpec.ConfigValue<String> COST_FORMULA;
        public final ForgeConfigSpec.BooleanValue USE_FORMULA;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> HIGH_COST_ENCHANTMENTS;

        // Enchantment table workstation
        public final ForgeConfigSpec.EnumValue<EnchantPowerMode> enchantPowerMode;
        public final ForgeConfigSpec.IntValue enchantFixedPower;
        public final ForgeConfigSpec.IntValue enchantXpPerPower;
        public final ForgeConfigSpec.IntValue enchantLevelPerPower;
        // 增强 / 解限
        public final ForgeConfigSpec.BooleanValue enchantIgnoreEnchanted;
        public final ForgeConfigSpec.BooleanValue enchantIgnoreConflict;
        public final ForgeConfigSpec.BooleanValue enchantNoLapis;
        public final ForgeConfigSpec.BooleanValue enchantLevelGateIgnore;
        public final ForgeConfigSpec.BooleanValue enchantUncapPower;
        public final ForgeConfigSpec.IntValue enchantCostPercent;
        public final ForgeConfigSpec.BooleanValue enchantAllowTreasure;
        // 预览 / 刷新（服务端开关）
        public final ForgeConfigSpec.BooleanValue enchantPreviewEnabled;
        public final ForgeConfigSpec.BooleanValue enchantRefreshEnabled;
        public final ForgeConfigSpec.IntValue enchantRefreshLapis;

        // 附魔合并（批量附魔工作站）配置
        public final ForgeConfigSpec.BooleanValue enchantMergeEnable;
        public final ForgeConfigSpec.BooleanValue enchantMergeConsumeBook;
        public final ForgeConfigSpec.IntValue enchantMergeSplitXpCost;
        // 附魔合并新行为：冲突判定 / 视为拥有全部附魔 / 返还书 / 费用调整
        public final ForgeConfigSpec.BooleanValue enchantMergeCheckConflict;
        public final ForgeConfigSpec.BooleanValue enchantMergeAssumeAll;
        public final ForgeConfigSpec.IntValue enchantMergeAssumeAllExtraCost;
        public final ForgeConfigSpec.BooleanValue enchantMergeKeepRemovedBooks;
        public final ForgeConfigSpec.IntValue enchantMergeExtraCostPerEnchant;
        public final ForgeConfigSpec.DoubleValue enchantMergeCostMultiplier;
        public final ForgeConfigSpec.IntValue enchantMergeCostPercentBonus;

        // BD modifications (tweaks applied to Beyond Dimensions)
        public final ForgeConfigSpec.BooleanValue xpRodTweaksEnabled;
        /** 熔炉烧网络终端：批量烧炼网络内全部可烧炼物品（默认关闭） */
        public final ForgeConfigSpec.BooleanValue furnaceTerminalSmeltAllEnabled;
        public final ForgeConfigSpec.IntValue xpRodMaxTargetLevel;
        public final ForgeConfigSpec.IntValue xpRodGrantBatchSize;
        public final ForgeConfigSpec.EnumValue<XpGrantMode> xpRodGrantMode;
        /** 经验棒自动修复：允许使用该功能（总开关；实际使用由经验棒界面决定） */
        public final ForgeConfigSpec.BooleanValue xpAutoRepairEnabled;
        /** 允许界面选择“百分比”修复模式 */
        public final ForgeConfigSpec.BooleanValue xpRepairAllowPercent;
        /** 允许界面选择“固定值”修复模式 */
        public final ForgeConfigSpec.BooleanValue xpRepairAllowAbsolute;
        /** 百分比模式：各档位每次修复量（最大耐久的百分比） */
        public final ForgeConfigSpec.IntValue xpRepairTierLowPercent;
        public final ForgeConfigSpec.IntValue xpRepairTierNormalPercent;
        public final ForgeConfigSpec.IntValue xpRepairTierHighPercent;
        public final ForgeConfigSpec.IntValue xpRepairTierMaxPercent;
        /** 固定值模式每次修复量（耐久值，配置决定数值） */
        public final ForgeConfigSpec.IntValue xpAutoRepairAbsolute;
        /** 各档位修复间隔（tick） */
        public final ForgeConfigSpec.IntValue xpRepairTierLowInterval;
        public final ForgeConfigSpec.IntValue xpRepairTierNormalInterval;
        public final ForgeConfigSpec.IntValue xpRepairTierHighInterval;
        public final ForgeConfigSpec.IntValue xpRepairTierMaxInterval;
        /** 允许界面选择的最高修复档位（LOW..该档位） */
        public final ForgeConfigSpec.EnumValue<XpRepairTier> xpRepairMaxTier;
        /** 桶入网自动分离：含流体的容器入网时拆为"流体 + 空容器"（默认开启） */
        public final ForgeConfigSpec.BooleanValue bucketSeparatorEnabled;
        /** 桶入网自动分离：仅处理桶物品（BucketItem，默认开启；关闭则连带拆分水瓶等非桶容器） */
        public final ForgeConfigSpec.BooleanValue bucketSeparatorBucketOnly;
        /** 桶入网自动分离：排除项（物品注册名列表；命中则不拆分、原样入网；支持 namespace:* 通配） */
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> bucketSeparatorExcludedItems;
        /** 插入拦截器（附魔书除外）的拦截链最高重入次数（默认 32，最高 255） */
        public final ForgeConfigSpec.IntValue handlerMaxReentrancy;
        /** 网络熔炉熔炼速度倍率（1.0=原速；配方耗时按倍率缩放，越大越快） */
        public final ForgeConfigSpec.DoubleValue netFurnaceSmeltSpeed;
        /** 服务端：会话级主网络库存/状态同步（供 JEI 角标等多种功能读取；关闭则各自回退独立通道，默认关闭） */
        public final ForgeConfigSpec.BooleanValue primaryNetSync;
        /** 主网络同步范围（默认仅物品键；可扩至全部存储键 / 含扩展） */
        public final ForgeConfigSpec.EnumValue<PrimaryNetSyncScope> primaryNetSyncScope;
        /** 主网络同步调试日志 / 性能探针开关（默认关闭） */
        public final ForgeConfigSpec.BooleanValue primaryNetSyncDebug;
        /** 维度网络通道（net_pathway）标记槽行数（1~6，全局；标记槽数 = 行数*9） */
        public final ForgeConfigSpec.IntValue netPathwayFilterRows;
        /** 维度网络方块被破坏时保留配置 NBT（标记槽 + 方块配置；默认开启） */
        public final ForgeConfigSpec.BooleanValue netedBlockKeepNbt;

        // ===== 网络磁铁（BI 接管吸取范围/间隔，完全自定义档位） =====
        /** 是否使用下方自定义档位列表（false 时用与 BD 等价的默认六档） */
        public final ForgeConfigSpec.BooleanValue magnetCustomTiersEnabled;
        /** 物品吸取档位列表，格式：名称:半径:间隔（半径 -1 = 整区块） */
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> magnetItemRangeTiers;
        /** 流体吸取档位列表，格式：名称:半径:间隔（半径 -1 = 整区块） */
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> magnetFluidRangeTiers;

        // ===== 网络喂食器口渴（Thirst）集成（独立配置区） =====
        /** 每多少 mB 水补充一次 */
        public final ForgeConfigSpec.IntValue feederThirstMbPerUse;
        /** 各纯度档每次补充的口渴值 */
        public final ForgeConfigSpec.IntValue feederThirstDirtyThirst;
        public final ForgeConfigSpec.IntValue feederThirstSlightlyDirtyThirst;
        public final ForgeConfigSpec.IntValue feederThirstAcceptableThirst;
        public final ForgeConfigSpec.IntValue feederThirstPurifiedThirst;
        /** 各纯度档每次补充的水饱和 */
        public final ForgeConfigSpec.IntValue feederThirstDirtyQuenched;
        public final ForgeConfigSpec.IntValue feederThirstSlightlyDirtyQuenched;
        public final ForgeConfigSpec.IntValue feederThirstAcceptableQuenched;
        public final ForgeConfigSpec.IntValue feederThirstPurifiedQuenched;
        /** 原版水(minecraft:water)在喂食器补水时视为的纯度（0脏/1微脏/2可接受/3纯净） */
        public final ForgeConfigSpec.IntValue feederThirstVanillaWaterPurity;
        /** 同时存在多种口渴系统时，是否对每套各给完整补水数值（默认关：只给一份，补最缺的那套） */
        public final ForgeConfigSpec.BooleanValue feederThirstSeparateHydration;
        /** 回血模式维持的饥饿/水饱和下限（默认 6，覆盖 LSO 单次回血消耗） */
        public final ForgeConfigSpec.IntValue feederThirstRegenMinSaturation;

        // Network totem (network only)
        public final ForgeConfigSpec.BooleanValue AUTO_TOTEM_ENABLED;
        public final ForgeConfigSpec.IntValue AUTO_TOTEM_COOLDOWN_SECONDS;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> AUTO_TOTEM_DAMAGE_BLACKLIST;
        public final ForgeConfigSpec.BooleanValue AUTO_TOTEM_RESPECT_BYPASSES;
        public final ForgeConfigSpec.BooleanValue AUTO_TOTEM_RESTORE_MAX_HEALTH;
        public final ForgeConfigSpec.BooleanValue AUTO_TOTEM_HEAL_TO_FULL;

        /** 图腾爆发（网络图腾触发后对周围造成范围伤害） */
        public final ForgeConfigSpec.BooleanValue TOTEM_BURST_ENABLED;
        public final ForgeConfigSpec.DoubleValue TOTEM_BURST_RADIUS;
        public final ForgeConfigSpec.DoubleValue TOTEM_BURST_DAMAGE_PERCENT;
        public final ForgeConfigSpec.BooleanValue TOTEM_BURST_IGNORE_INVULNERABILITY;

        // SetHealth revive (network only, event + mixin implementations)
        // 通用项（冷却 / 伤害黑名单 / 无敌绕过 / 恢复上限 / 回满）复用 auto_totem 分区配置
        public final ForgeConfigSpec.BooleanValue REVIVE_EVENT_ENABLED;
        public final ForgeConfigSpec.BooleanValue REVIVE_MIXIN_ENABLED;
        public final ForgeConfigSpec.BooleanValue REVIVE_EXTRA_TOTEM_ON_SET_HEALTH_DEATH;
        public final ForgeConfigSpec.IntValue REVIVE_EXTRA_TOTEM_COUNT;
        public final ForgeConfigSpec.BooleanValue REVIVE_RESET_DEATH_TIME;

        // Vehicle energy charge
        public final ForgeConfigSpec.EnumValue<VehicleChargeMode> swVehicleChargeMode;
        public final ForgeConfigSpec.IntValue swVehicleEnergyChargeRate;
        public final ForgeConfigSpec.IntValue swVehicleChargeInterval;
        public final ForgeConfigSpec.DoubleValue swVehicleChargePercentage;

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
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> ENERGY_AMMO_CHARGE_WHITELIST;
        public final ForgeConfigSpec.BooleanValue ENERGY_AMMO_CHARGE_CURIOS;
        public final ForgeConfigSpec.BooleanValue ENERGY_AMMO_CHARGE_MAID_BAUBLES;
        public final ForgeConfigSpec.EnumValue<EnergyChargeMode> ENERGY_AMMO_CHARGE_MODE;
        public final ForgeConfigSpec.DoubleValue ENERGY_AMMO_CHARGE_PERCENTAGE;

        // Networked potion charm
        public final ForgeConfigSpec.BooleanValue POTION_CHARM_ENABLED;
        public final ForgeConfigSpec.IntValue POTION_CHARM_INTERVAL;
        public final ForgeConfigSpec.IntValue POTION_CHARM_REFRESH_LEAD;
        public final ForgeConfigSpec.DoubleValue POTION_CHARM_MENDING_XP_COST;

        // Goety network soul source（网络灵魂源）
        public final ForgeConfigSpec.BooleanValue SOUL_ENABLED;
        public final ForgeConfigSpec.BooleanValue SOUL_REQUIRE_ACTIVATION;
        public final ForgeConfigSpec.BooleanValue SOUL_SOURCE_KILL;
        public final ForgeConfigSpec.DoubleValue SOUL_KILL_RATIO;
        public final ForgeConfigSpec.BooleanValue SOUL_SOURCE_MANUAL;
        public final ForgeConfigSpec.BooleanValue SOUL_DIRECT_MAIN_NET;
        public final ForgeConfigSpec.LongValue SOUL_MAX;
        public final ForgeConfigSpec.BooleanValue SOUL_NETWORK_DISCOUNT;
        public final ForgeConfigSpec.BooleanValue SOUL_NETWORK_REVIVE;
        public final ForgeConfigSpec.BooleanValue SOUL_NETWORK_REVIVE_TO_ARCA;
        public final ForgeConfigSpec.BooleanValue SOUL_DEBUG;
        /** 灵魂是否改用 RI 的 beyonddimensions:stack_type/goety_soul 键（启用并在重载时迁移旧数据） */
        public final ForgeConfigSpec.BooleanValue SOUL_USE_RI_KEY;

        // Iron's Spells 'n Spellbooks network mana source（网络魔力源）
        public final ForgeConfigSpec.BooleanValue IRONS_MANA_ENABLED;
        public final ForgeConfigSpec.BooleanValue IRONS_MANA_PAY_CONSUMPTION;
        public final ForgeConfigSpec.DoubleValue IRONS_MANA_SINK_RATIO;
        public final ForgeConfigSpec.BooleanValue IRONS_MANA_SINK_ON_FULL;
        public final ForgeConfigSpec.BooleanValue IRONS_MANA_SINK_ON_INSTANT;
        public final ForgeConfigSpec.BooleanValue IRONS_MANA_FIXED_REGEN;
        public final ForgeConfigSpec.LongValue IRONS_MANA_FIXED_REGEN_AMOUNT;
        public final ForgeConfigSpec.BooleanValue IRONS_MANA_REGEN_WHEN_NOT_FULL;
        public final ForgeConfigSpec.BooleanValue IRONS_MANA_INDEPENDENT_REGEN;
        public final ForgeConfigSpec.LongValue IRONS_MANA_MAX;

        // Goety ritual environment bypass（仪式环境绕过）
        public final ForgeConfigSpec.BooleanValue RITUAL_ENV_BYPASS;
        public final ForgeConfigSpec.LongValue RITUAL_ENV_SOUL_COST;
        public final ForgeConfigSpec.IntValue RITUAL_ENV_PATHWAY_COUNT;
        public final ForgeConfigSpec.IntValue RITUAL_ENV_CONNECT_COUNT;
        public final ForgeConfigSpec.IntValue RITUAL_ENV_RADIUS;
        public final ForgeConfigSpec.BooleanValue RITUAL_PATHWAY_CAGE;

        public ServerConfig(ForgeConfigSpec.Builder builder)
        {
            builder.comment(ConfigCommentLang.comment("language")).push("language");
            language = builder.defineEnum("command_language", Language.EN_US);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("network_list")).push("network_list");
            maxNetworksPerPage = builder.defineInRange("max_networks_per_page", 10, 1, 100);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("blacklist")).push("blacklist");
            ENABLE_ITEM_BLACKLIST = builder.comment(ConfigCommentLang.comment("blacklist.enable")).define("enable", false);
            ITEM_BLACKLIST = builder.comment(ConfigCommentLang.comment("blacklist.items")).defineList("items",
                    Arrays.asList("minecraft:barrier", "minecraft:command_block"), obj -> obj instanceof String);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("craft")).push("craft");
            CRAFT_COOLDOWN_MS = builder.comment(ConfigCommentLang.comment("craft.cooldown_ms")).defineInRange("cooldown_ms", 1000, 0, 60000);
            CRAFT_MAX_DEPTH = builder.comment(ConfigCommentLang.comment("craft.max_depth")).defineInRange("max_depth", 6, 1, 20);
            BLOCK_BD_CONTAINER_READER = builder.comment(ConfigCommentLang.comment("craft.block_bd_reader")).define("block_bd_reader", true);
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
            anvilCostMode = builder
                    .comment(ConfigCommentLang.comment("anvil.costMode"))
                    .defineEnum("costMode", AnvilChargeMode.POINTS);
            anvilLevelCap = builder
                    .comment(ConfigCommentLang.comment("anvil.levelCap"))
                    .defineInRange("levelCap", 40, 1, Integer.MAX_VALUE);
            anvilPointsCap = builder
                    .comment(ConfigCommentLang.comment("anvil.pointsCap"))
                    .defineInRange("pointsCap", Long.MAX_VALUE, 1L, Long.MAX_VALUE);
            anvilBreakLevelMode = builder
                    .comment(ConfigCommentLang.comment("anvil.breakMaxLevelMode"))
                    .defineEnum("breakMaxLevelMode", BreakLevelMode.OFF);
            anvilIgnoreConflict = builder
                    .comment(ConfigCommentLang.comment("anvil.ignoreConflict"))
                    .define("ignoreConflict", false);
            anvilIgnoreSupport = builder
                    .comment(ConfigCommentLang.comment("anvil.ignoreSupport"))
                    .define("ignoreSupport", false);
            anvilUnrestricted = builder
                    .comment(ConfigCommentLang.comment("anvil.unrestricted"))
                    .define("unrestricted", false);
            anvilConflictPenalty = builder
                    .comment(ConfigCommentLang.comment("anvil.conflictPenalty"))
                    .defineInRange("conflictPenalty", 2, 0, 1000000);
            anvilSupportPenalty = builder
                    .comment(ConfigCommentLang.comment("anvil.supportPenalty"))
                    .defineInRange("supportPenalty", 5, 0, 1000000);
            anvilConflictPercent = builder
                    .comment(ConfigCommentLang.comment("anvil.conflictPercent"))
                    .defineInRange("conflictPercent", 0, 0, 1000000);
            anvilSupportPercent = builder
                    .comment(ConfigCommentLang.comment("anvil.supportPercent"))
                    .defineInRange("supportPercent", 0, 0, 1000000);
            anvilBreakLevelPercent = builder
                    .comment(ConfigCommentLang.comment("anvil.breakLevelPercent"))
                    .defineInRange("breakLevelPercent", 0, 0, 1000000);
            anvilUnrestrictedPercent = builder
                    .comment(ConfigCommentLang.comment("anvil.unrestrictedPercent"))
                    .defineInRange("unrestrictedPercent", 100, 0, 1000000);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("enchantment_separation")).push("enchantment_separation");

            ENABLE_ENCHANTMENT_SEPARATION = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.enable"))
                    .define("enable", false);

            ENCHANTMENT_SEPARATION_BASE_COST = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.base_cost"))
                    .defineInRange("base_cost", 10, 0, 1000);

            ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.level_multiplier"))
                    .defineInRange("level_multiplier", 5, 0, 100);

            DEFAULT_ENCHANTMENT_MULTIPLIER = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.default_multiplier"))
                    .defineInRange("default_multiplier", 1.0, 0.1, 10.0);

            COST_FORMULA = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.cost_formula"))
                    .define("cost_formula", "base + (level - 1) * multiplier");

            USE_FORMULA = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.use_formula"))
                    .define("use_formula", false);

            HIGH_COST_ENCHANTMENTS = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.high_cost_list"))
                    .defineList("high_cost_list",
                            Arrays.asList("minecraft:mending:3.0", "minecraft:sharpness:1.2"),
                            obj -> obj instanceof String);

            ENCHANTMENT_SEPARATION_DEBUG = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.debug"))
                    .define("debug", false);

            ENCHANTMENT_SEPARATION_MERGE_SAME_LEVEL = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.merge_same_level"))
                    .define("merge_same_level", false);

            ENCHANTMENT_SEPARATION_MAX_REENTRANCY = builder
                    .comment(ConfigCommentLang.comment("enchantment_separation.max_reentrancy"))
                    .defineInRange("max_reentrancy", 100, 1, 255);

            builder.pop();

            builder.comment(ConfigCommentLang.comment("enchant_table")).push("enchant_table");
            enchantPowerMode = builder.comment(ConfigCommentLang.comment("enchant_table.powerMode"))
                    .defineEnum("powerMode", EnchantPowerMode.FIXED);
            enchantFixedPower = builder.comment(ConfigCommentLang.comment("enchant_table.fixedPower"))
                    .defineInRange("fixedPower", 15, 0, 100);
            enchantXpPerPower = builder.comment(ConfigCommentLang.comment("enchant_table.xpPerPower"))
                    .defineInRange("xpPerPower", 100, 1, Integer.MAX_VALUE);
            enchantLevelPerPower = builder.comment(ConfigCommentLang.comment("enchant_table.levelPerPower"))
                    .defineInRange("levelPerPower", 1, 1, Integer.MAX_VALUE);
            enchantIgnoreEnchanted = builder.comment(ConfigCommentLang.comment("enchant_table.ignoreEnchanted"))
                    .define("ignoreEnchanted", false);
            enchantIgnoreConflict = builder.comment(ConfigCommentLang.comment("enchant_table.ignoreConflict"))
                    .define("ignoreConflict", false);
            enchantNoLapis = builder.comment(ConfigCommentLang.comment("enchant_table.noLapis"))
                    .define("noLapis", false);
            enchantLevelGateIgnore = builder.comment(ConfigCommentLang.comment("enchant_table.levelGateIgnore"))
                    .define("levelGateIgnore", false);
            enchantUncapPower = builder.comment(ConfigCommentLang.comment("enchant_table.uncapPower"))
                    .define("uncapPower", false);
            enchantCostPercent = builder.comment(ConfigCommentLang.comment("enchant_table.costPercent"))
                    .defineInRange("costPercent", 100, 10, 500);
            enchantAllowTreasure = builder.comment(ConfigCommentLang.comment("enchant_table.allowTreasure"))
                    .define("allowTreasure", false);
            enchantPreviewEnabled = builder.comment(ConfigCommentLang.comment("enchant_table.previewEnabled"))
                    .define("previewEnabled", true);
            enchantRefreshEnabled = builder.comment(ConfigCommentLang.comment("enchant_table.refreshEnabled"))
                    .define("refreshEnabled", true);
            enchantRefreshLapis = builder.comment(ConfigCommentLang.comment("enchant_table.refreshLapis"))
                    .defineInRange("refreshLapis", 1, 0, 64);
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
            xpAutoRepairEnabled = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_enabled"))
                    .define("xp_repair_enabled", true);
            xpRepairAllowPercent = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_allow_percent"))
                    .define("xp_repair_allow_percent", true);
            xpRepairAllowAbsolute = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_allow_absolute"))
                    .define("xp_repair_allow_absolute", true);
            xpRepairTierLowPercent = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_tier_low_percent"))
                    .defineInRange("xp_repair_tier_low_percent", 5, 0, 100);
            xpRepairTierNormalPercent = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_tier_normal_percent"))
                    .defineInRange("xp_repair_tier_normal_percent", 10, 0, 100);
            xpRepairTierHighPercent = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_tier_high_percent"))
                    .defineInRange("xp_repair_tier_high_percent", 20, 0, 100);
            xpRepairTierMaxPercent = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_tier_max_percent"))
                    .defineInRange("xp_repair_tier_max_percent", 50, 0, 100);
            xpAutoRepairAbsolute = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_absolute"))
                    .defineInRange("xp_repair_absolute", 100, 0, Integer.MAX_VALUE);
            xpRepairTierLowInterval = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_tier_low_interval"))
                    .defineInRange("xp_repair_tier_low_interval", 20, 1, 200);
            xpRepairTierNormalInterval = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_tier_normal_interval"))
                    .defineInRange("xp_repair_tier_normal_interval", 10, 1, 200);
            xpRepairTierHighInterval = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_tier_high_interval"))
                    .defineInRange("xp_repair_tier_high_interval", 5, 1, 200);
            xpRepairTierMaxInterval = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_tier_max_interval"))
                    .defineInRange("xp_repair_tier_max_interval", 1, 1, 200);
            xpRepairMaxTier = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.xp_repair_max_tier"))
                    .defineEnum("xp_repair_max_tier", XpRepairTier.MAX);
            bucketSeparatorEnabled = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.bucket_separator_enabled"))
                    .define("bucket_separator_enabled", true);
            bucketSeparatorBucketOnly = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.bucket_separator_bucket_only"))
                    .define("bucket_separator_bucket_only", true);
            bucketSeparatorExcludedItems = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.bucket_separator_excluded_items"))
                    .defineList("bucket_separator_excluded_items",
                            Arrays.asList(
                                    "kaleidoscope_tavern:grape_bucket",
                                    "kaleidoscope_tavern:ice_grape_bucket",
                                    "kaleidoscope_tavern:gold_grape_bucket",
                                    "kaleidoscope_tavern:green_grape_bucket",
                                    "kaleidoscope_tavern:sweet_berries_bucket",
                                    "kaleidoscope_tavern:glow_berries_bucket"),
                            obj -> obj instanceof String);
            handlerMaxReentrancy = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.handler_max_reentrancy"))
                    .defineInRange("handler_max_reentrancy", 32, 1, 255);
            netFurnaceSmeltSpeed = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.net_furnace_smelt_speed"))
                    .defineInRange("net_furnace_smelt_speed", 1.0D, 0.1D, 100.0D);
            primaryNetSync = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.primary_net_sync"))
                    .define("primary_net_sync", false);
            primaryNetSyncScope = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.primary_net_sync_scope"))
                    .defineEnum("primary_net_sync_scope", PrimaryNetSyncScope.ITEMS);
            primaryNetSyncDebug = builder
                    .comment(ConfigCommentLang.comment("bd_tweaks.primary_net_sync_debug"))
                    .define("primary_net_sync_debug", false);
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

            builder.comment(ConfigCommentLang.comment("vehicle")).push("vehicle");
            swVehicleChargeMode = builder
                    .comment(ConfigCommentLang.comment("vehicle.charge_mode"))
                    .defineEnum("charge_mode", VehicleChargeMode.RATE);
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

            builder.comment(ConfigCommentLang.comment("ammo_extract")).push("ammo_extract");
            AMMO_EXTRACT_MAPPINGS = builder
                    .comment(ConfigCommentLang.comment("ammo_extract.mappings"))
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

            builder.comment(ConfigCommentLang.comment("potion_charm")).push("potion_charm");
            POTION_CHARM_ENABLED = builder
                    .comment(ConfigCommentLang.comment("potion_charm.enabled"))
                    .define("enabled", true);
            POTION_CHARM_INTERVAL = builder
                    .comment(ConfigCommentLang.comment("potion_charm.interval"))
                    .defineInRange("interval", 20, 1, 1200);
            POTION_CHARM_REFRESH_LEAD = builder
                    .comment(ConfigCommentLang.comment("potion_charm.refresh_lead_ticks"))
                    .defineInRange("refresh_lead_ticks", 10, 1, 1200);
            POTION_CHARM_MENDING_XP_COST = builder
                    .comment(ConfigCommentLang.comment("potion_charm.mending_xp_cost"))
                    .defineInRange("mending_xp_cost", 1.0, 0.0, 1000000.0);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("goety_soul")).push("goety_soul");
            SOUL_ENABLED = builder
                    .comment(ConfigCommentLang.comment("goety_soul.enabled"))
                    .define("enabled", true);
            SOUL_REQUIRE_ACTIVATION = builder
                    .comment(ConfigCommentLang.comment("goety_soul.require_activation"))
                    .define("require_activation", false);
            SOUL_SOURCE_KILL = builder
                    .comment(ConfigCommentLang.comment("goety_soul.source_kill"))
                    .define("source_kill", true);
            SOUL_KILL_RATIO = builder
                    .comment(ConfigCommentLang.comment("goety_soul.kill_ratio"))
                    .defineInRange("kill_ratio", 1.0, 0.0, 1.0);
            SOUL_SOURCE_MANUAL = builder
                    .comment(ConfigCommentLang.comment("goety_soul.source_manual"))
                    .define("source_manual", false);
            SOUL_DIRECT_MAIN_NET = builder
                    .comment(ConfigCommentLang.comment("goety_soul.direct_main_net"))
                    .define("direct_main_net", true);
            SOUL_MAX = builder
                    .comment(ConfigCommentLang.comment("goety_soul.max_souls"))
                    .defineInRange("max_souls", 0L, 0L, Long.MAX_VALUE);
            SOUL_NETWORK_DISCOUNT = builder
                    .comment(ConfigCommentLang.comment("goety_soul.network_discount"))
                    .define("network_discount", false);
            SOUL_NETWORK_REVIVE = builder
                    .comment(ConfigCommentLang.comment("goety_soul.network_revive"))
                    .define("network_revive", true);
            SOUL_NETWORK_REVIVE_TO_ARCA = builder
                    .comment(ConfigCommentLang.comment("goety_soul.network_revive_to_arca"))
                    .define("network_revive_to_arca", true);
            SOUL_DEBUG = builder
                    .comment(ConfigCommentLang.comment("goety_soul.debug"))
                    .define("debug", false);
            SOUL_USE_RI_KEY = builder
                    .comment(ConfigCommentLang.comment("goety_soul.use_ri_key"))
                    .define("use_ri_key", false);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("irons_mana")).push("irons_mana");
            IRONS_MANA_ENABLED = builder
                    .comment(ConfigCommentLang.comment("irons_mana.enabled"))
                    .define("enabled", true);
            IRONS_MANA_PAY_CONSUMPTION = builder
                    .comment(ConfigCommentLang.comment("irons_mana.pay_consumption"))
                    .define("pay_consumption", true);
            IRONS_MANA_SINK_RATIO = builder
                    .comment(ConfigCommentLang.comment("irons_mana.sink_ratio"))
                    .defineInRange("sink_ratio", 1.0, 0.0, 1.0);
            IRONS_MANA_SINK_ON_FULL = builder
                    .comment(ConfigCommentLang.comment("irons_mana.sink_on_full"))
                    .define("sink_on_full", true);
            IRONS_MANA_SINK_ON_INSTANT = builder
                    .comment(ConfigCommentLang.comment("irons_mana.sink_on_instant"))
                    .define("sink_on_instant", true);
            IRONS_MANA_FIXED_REGEN = builder
                    .comment(ConfigCommentLang.comment("irons_mana.fixed_regen"))
                    .define("fixed_regen", false);
            IRONS_MANA_FIXED_REGEN_AMOUNT = builder
                    .comment(ConfigCommentLang.comment("irons_mana.fixed_regen_amount"))
                    .defineInRange("fixed_regen_amount", 1L, 0L, Long.MAX_VALUE);
            IRONS_MANA_REGEN_WHEN_NOT_FULL = builder
                    .comment(ConfigCommentLang.comment("irons_mana.regen_when_not_full"))
                    .define("regen_when_not_full", false);
            IRONS_MANA_INDEPENDENT_REGEN = builder
                    .comment(ConfigCommentLang.comment("irons_mana.independent_regen"))
                    .define("independent_regen", false);
            IRONS_MANA_MAX = builder
                    .comment(ConfigCommentLang.comment("irons_mana.max_mana"))
                    .defineInRange("max_mana", 10000L, 0L, Long.MAX_VALUE);
            builder.pop();

            builder.comment(ConfigCommentLang.comment("goety_ritual")).push("goety_ritual");
            RITUAL_ENV_BYPASS = builder
                    .comment(ConfigCommentLang.comment("goety_ritual.env_bypass"))
                    .define("env_bypass", true);
            RITUAL_ENV_SOUL_COST = builder
                    .comment(ConfigCommentLang.comment("goety_ritual.env_bypass_soul_cost"))
                    .defineInRange("env_bypass_soul_cost", 100L, 0L, Long.MAX_VALUE);
            RITUAL_ENV_PATHWAY_COUNT = builder
                    .comment(ConfigCommentLang.comment("goety_ritual.pathway_count"))
                    .defineInRange("pathway_count", 5, 0, 64);
            RITUAL_ENV_CONNECT_COUNT = builder
                    .comment(ConfigCommentLang.comment("goety_ritual.connect_count"))
                    .defineInRange("connect_count", 16, 0, 64);
            RITUAL_ENV_RADIUS = builder
                    .comment(ConfigCommentLang.comment("goety_ritual.radius"))
                    .defineInRange("radius", 8, 1, 32);
            RITUAL_PATHWAY_CAGE = builder
                    .comment(ConfigCommentLang.comment("goety_ritual.pathway_cage"))
                    .define("pathway_cage", true);
            builder.pop();
        }

    }

    // ─── 配置读取静态入口（供各处代码调用） ───
    /** 获取命令输出语言；配置尚未生成/加载时回退到默认 EN_US */
    public static Language getCommandLanguage() {
        try {
            return SERVER.language.get();
        } catch (IllegalStateException e) {
            return Language.EN_US;
        }
    }
    public static int maxNetworksPerPage() { return SERVER.maxNetworksPerPage.get(); }

    public static boolean enableItemBlacklist() { return SERVER.ENABLE_ITEM_BLACKLIST.get(); }
    public static List<? extends String> itemBlacklist() { return SERVER.ITEM_BLACKLIST.get(); }

    public static int craftCooldownMs() { return SERVER.CRAFT_COOLDOWN_MS.get(); }
    public static int craftMaxDepth() { return SERVER.CRAFT_MAX_DEPTH.get(); }
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

    public static boolean enableEnchantmentSeparation() { return SERVER.ENABLE_ENCHANTMENT_SEPARATION.get(); }
    public static boolean enchantmentSeparationDebug() { return SERVER.ENCHANTMENT_SEPARATION_DEBUG.get(); }
    /** 附魔分离：单附魔书同类同级自动合并（默认关闭） */
    public static boolean enchantmentSeparationMergeSameLevel() { return SERVER.ENCHANTMENT_SEPARATION_MERGE_SAME_LEVEL.get(); }
    /** 附魔分离：拦截链最高重入次数（同级合并级联深度上限，默认 100，最高 255）。 */
    public static int enchantmentSeparationMaxReentrancy() { return SERVER.ENCHANTMENT_SEPARATION_MAX_REENTRANCY.get(); }
    public static int enchantmentSeparationBaseCost() { return SERVER.ENCHANTMENT_SEPARATION_BASE_COST.get(); }
    public static int enchantmentSeparationLevelMultiplier() { return SERVER.ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER.get(); }
    public static double defaultEnchantmentMultiplier() { return SERVER.DEFAULT_ENCHANTMENT_MULTIPLIER.get(); }
    public static String costFormula() { return SERVER.COST_FORMULA.get(); }
    public static boolean useFormula() { return SERVER.USE_FORMULA.get(); }
    public static List<? extends String> highCostEnchantments() { return SERVER.HIGH_COST_ENCHANTMENTS.get(); }

    public static EnchantPowerMode enchantPowerMode() { return SERVER.enchantPowerMode.get(); }
    public static int enchantFixedPower() { return SERVER.enchantFixedPower.get(); }
    public static int enchantXpPerPower() { return SERVER.enchantXpPerPower.get(); }
    public static int enchantLevelPerPower() { return SERVER.enchantLevelPerPower.get(); }
    public static boolean enchantIgnoreEnchanted() { return SERVER.enchantIgnoreEnchanted.get(); }
    public static boolean enchantIgnoreConflict() { return SERVER.enchantIgnoreConflict.get(); }
    public static boolean enchantNoLapis() { return SERVER.enchantNoLapis.get(); }
    public static boolean enchantLevelGateIgnore() { return SERVER.enchantLevelGateIgnore.get(); }
    public static boolean enchantUncapPower() { return SERVER.enchantUncapPower.get(); }
    public static int enchantCostPercent() { return SERVER.enchantCostPercent.get(); }
    public static boolean enchantAllowTreasure() { return SERVER.enchantAllowTreasure.get(); }
    public static boolean enchantPreviewEnabled() { return SERVER.enchantPreviewEnabled.get(); }
    public static boolean enchantRefreshEnabled() { return SERVER.enchantRefreshEnabled.get(); }
    public static int enchantRefreshLapis() { return SERVER.enchantRefreshLapis.get(); }

    /** 附魔合并工作站总开关 */
    public static boolean enchantMergeEnable() { return SERVER.enchantMergeEnable.get(); }
    /** 附魔合并：拆分时是否连原附魔书载体一起消耗（false = 保留原载体，每步拆分仅需 1 本普通书；true = 原载体也消耗，每步 2 本） */
    public static boolean enchantMergeConsumeBook() { return SERVER.enchantMergeConsumeBook.get(); }
    /** 附魔合并：每次拆分高等级附魔书消耗的经验点（0 = 不消耗，默认） */
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

    /** BD 修改：经验棒修改总开关（等级上限/分批/直设，关闭 = BD 原行为） */
    public static boolean xpRodTweaksEnabled() { return SERVER.xpRodTweaksEnabled.get(); }

    /** BD 修改：桶入网自动分离（含流体的容器拆为流体 + 空容器；默认开启） */
    public static boolean bucketSeparatorEnabled() { return SERVER.bucketSeparatorEnabled.get(); }
    /** BD 修改：桶入网自动分离是否仅处理桶物品（BucketItem；默认开启）。 */
    public static boolean bucketSeparatorBucketOnly() { return SERVER.bucketSeparatorBucketOnly.get(); }
    /** BD 修改：桶入网自动分离排除项（物品注册名列表；命中则不拆分、原样入网）。 */
    public static List<? extends String> bucketSeparatorExcludedItems() { return SERVER.bucketSeparatorExcludedItems.get(); }
    /** 插入拦截器（附魔书除外）的拦截链最高重入次数（默认 32，最高 255）。 */
    public static int handlerMaxReentrancy() { return SERVER.handlerMaxReentrancy.get(); }

    /** BD 修改：熔炉烧网络终端触发批量烧炼（默认关闭；终端不消耗，产物即终端，取出时触发） */
    public static boolean furnaceTerminalSmeltAllEnabled() { return SERVER.furnaceTerminalSmeltAllEnabled.get(); }

    /** 网络熔炉熔炼速度倍率（1.0=原速） */
    public static double netFurnaceSmeltSpeed() { return SERVER.netFurnaceSmeltSpeed.get(); }

    /** 服务端：会话级主网络库存/状态同步（供 JEI 等多种功能读取；默认关闭 = 各自回退独立通道） */
    public static boolean primaryNetSync() { return SERVER.primaryNetSync.get(); }

    /** 主网络同步范围（ITEMS / STORAGE / STORAGE_EXT） */
    public static PrimaryNetSyncScope primaryNetSyncScope() { return SERVER.primaryNetSyncScope.get(); }

    /** 主网络同步调试日志 / 性能探针开关（默认关闭） */
    public static boolean primaryNetSyncDebug() { return SERVER.primaryNetSyncDebug.get(); }

    /** 维度网络通道（net_pathway）标记槽行数（1~6，全局） */
    public static int netPathwayFilterRows() { return SERVER.netPathwayFilterRows.get(); }

    /** 维度网络方块被破坏时保留配置 NBT（标记槽 + 方块配置） */
    public static boolean netedBlockKeepNbt() { return SERVER.netedBlockKeepNbt.get(); }

    /** 经验棒可设定的目标等级上限（默认 21863，受经验总量 int 边界约束） */
    public static int xpRodMaxTargetLevel() { return SERVER.xpRodMaxTargetLevel.get(); }

    /** 经验棒每 tick 发放批次上限（0 = 不分批，一次性发放安全量） */
    public static int xpRodGrantBatchSize() { return SERVER.xpRodGrantBatchSize.get(); }

    /** 经验棒经验给予方式（BATCH 分批 / DIRECT 直设式） */
    public static XpGrantMode xpRodGrantMode() { return SERVER.xpRodGrantMode.get(); }

    /** 经验棒自动修复开关（用绑定网络的经验修复玩家装备） */
    public static boolean xpAutoRepairEnabled() { return SERVER.xpAutoRepairEnabled.get(); }

    /** 允许界面选择“百分比”修复模式 */
    public static boolean xpRepairAllowPercent() { return SERVER.xpRepairAllowPercent.get(); }

    /** 允许界面选择“固定值”修复模式 */
    public static boolean xpRepairAllowAbsolute() { return SERVER.xpRepairAllowAbsolute.get(); }

    /** 经验棒自动修复：档位对应的每次修复百分比（PERCENT 模式） */
    public static int xpRepairTierPercent(XpRepairTier tier) {
        return switch (tier) {
            case LOW -> SERVER.xpRepairTierLowPercent.get();
            case NORMAL -> SERVER.xpRepairTierNormalPercent.get();
            case HIGH -> SERVER.xpRepairTierHighPercent.get();
            case MAX -> SERVER.xpRepairTierMaxPercent.get();
        };
    }

    /** 经验棒自动修复：档位对应的修复间隔（tick） */
    public static int xpRepairTierInterval(XpRepairTier tier) {
        return switch (tier) {
            case LOW -> SERVER.xpRepairTierLowInterval.get();
            case NORMAL -> SERVER.xpRepairTierNormalInterval.get();
            case HIGH -> SERVER.xpRepairTierHighInterval.get();
            case MAX -> SERVER.xpRepairTierMaxInterval.get();
        };
    }

    /** 经验棒自动修复每次修复量（固定耐久值，ABSOLUTE 模式） */
    public static int xpAutoRepairAbsolute() { return SERVER.xpAutoRepairAbsolute.get(); }

    /** 允许界面选择的最高修复档位（LOW..该档位） */
    public static XpRepairTier xpRepairMaxTier() { return SERVER.xpRepairMaxTier.get(); }

    /** 网络磁铁是否使用自定义档位列表 */
    public static boolean magnetCustomTiersEnabled() { return SERVER.magnetCustomTiersEnabled.get(); }

    /** 网络磁铁物品吸取档位列表（名称:半径:间隔；半径 -1 = 整区块） */
    public static List<? extends String> magnetItemRangeTiers() { return SERVER.magnetItemRangeTiers.get(); }

    /** 网络磁铁流体吸取档位列表（名称:半径:间隔；半径 -1 = 整区块） */
    public static List<? extends String> magnetFluidRangeTiers() { return SERVER.magnetFluidRangeTiers.get(); }

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

    public static boolean autoTotemEnabled() { return SERVER.AUTO_TOTEM_ENABLED.get(); }
    public static int autoTotemCooldownSeconds() { return SERVER.AUTO_TOTEM_COOLDOWN_SECONDS.get(); }
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

    public static VehicleChargeMode vehicleChargeMode() { return SERVER.swVehicleChargeMode.get(); }
    public static int vehicleChargeRate() { return SERVER.swVehicleEnergyChargeRate.get(); }
    public static int vehicleChargeInterval() { return SERVER.swVehicleChargeInterval.get(); }
    public static double vehicleChargePercentage() { return SERVER.swVehicleChargePercentage.get(); }

    public static List<? extends String> ammoExtractMappings() { return SERVER.AMMO_EXTRACT_MAPPINGS.get(); }

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

    public static boolean potionCharmEnabled() { return SERVER.POTION_CHARM_ENABLED.get(); }
    public static int potionCharmInterval() { return SERVER.POTION_CHARM_INTERVAL.get(); }
    public static int potionCharmRefreshLeadTicks() { return SERVER.POTION_CHARM_REFRESH_LEAD.get(); }
    public static double potionCharmMendingXpCost() { return SERVER.POTION_CHARM_MENDING_XP_COST.get(); }

    // ── 网络灵魂源（Goety 联动）──
    public static boolean soulEnabled() { return SERVER.SOUL_ENABLED.get(); }
    /** 是否启用"仪式激活门槛"；关闭时网络默认已激活灵魂存储。 */
    public static boolean soulRequireActivation() { return SERVER.SOUL_REQUIRE_ACTIVATION.get(); }
    public static boolean soulSourceKill() { return SERVER.SOUL_SOURCE_KILL.get(); }
    public static double soulKillRatio() { return SERVER.SOUL_KILL_RATIO.get(); }
    public static boolean soulSourceManual() { return SERVER.SOUL_SOURCE_MANUAL.get(); }
    public static boolean soulDirectMainNet() { return SERVER.SOUL_DIRECT_MAIN_NET.get(); }
    public static long soulMax() { return SERVER.SOUL_MAX.get(); }
    /** 网络魂是否参与 {@code soulDiscount}（折扣/减免装备）。默认关：网络部分按原价扣。 */
    public static boolean soulNetworkDiscount() { return SERVER.SOUL_NETWORK_DISCOUNT.get(); }
    /** 是否允许网络灵魂参与复活判定（死亡时扣网络魂复活）。 */
    public static boolean soulNetworkRevive() { return SERVER.SOUL_NETWORK_REVIVE.get(); }
    /** 网络复活时，若玩家有绑定方舟则传送到方舟位置，否则原地复活。 */
    public static boolean soulNetworkReviveToArca() { return SERVER.SOUL_NETWORK_REVIVE_TO_ARCA.get(); }
    public static boolean soulDebug() { return SERVER.SOUL_DEBUG.get(); }
    /** 灵魂是否改用 RI 键（beyonddimensions:stack_type/goety_soul）；启用且重载配置时迁移旧数据。 */
    public static boolean soulUseRiKey() { return SERVER.SOUL_USE_RI_KEY.get(); }

    /**
     * 采集全部服务端配置项的快照（路径 → 字符串值），用于整包下发客户端。
     * <p>直接使用 Forge 提供的配置项列表 {@code SERVER_SPEC.getValues().valueMap()}；
     * 跳过取值为 null 的项。列表型值按 {@link String#valueOf(Object)} 形式输出。
     */
    public static Map<String, String> snapshotAll() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Object raw : SERVER_SPEC.getValues().valueMap().values()) {
            if (!(raw instanceof ForgeConfigSpec.ConfigValue<?> value) || value.get() == null) continue;
            out.put(String.join(".", value.getPath()), String.valueOf(value.get()));
        }
        return out;
    }

    // ── 网络魔力源（Iron's Spells 'n Spellbooks 联动）──
    public static boolean ironsManaEnabled() { return SERVER.IRONS_MANA_ENABLED.get(); }
    /** 魔力消耗时是否由网络支付。 */
    public static boolean ironsManaPayConsumption() { return SERVER.IRONS_MANA_PAY_CONSUMPTION.get(); }
    /** 魔力回复时沉淀入网的比例（0~1）。 */
    public static double ironsManaSinkRatio() { return SERVER.IRONS_MANA_SINK_RATIO.get(); }
    /** 自然回蓝满蓝（溢出）时是否沉淀入网。 */
    public static boolean ironsManaSinkOnFull() { return SERVER.IRONS_MANA_SINK_ON_FULL.get(); }
    /** 瞬间回复（如法力药水）满蓝时溢出部分是否沉淀入网。 */
    public static boolean ironsManaSinkOnInstant() { return SERVER.IRONS_MANA_SINK_ON_INSTANT.get(); }
    /** 固定自然回复网络蓝（固定量而非溢出）。 */
    public static boolean ironsManaFixedRegen() { return SERVER.IRONS_MANA_FIXED_REGEN.get(); }
    /** 固定回蓝的每次注入量。 */
    public static long ironsManaFixedRegenAmount() { return SERVER.IRONS_MANA_FIXED_REGEN_AMOUNT.get(); }
    /** 固定回蓝时，玩家未满蓝也注入。 */
    public static boolean ironsManaRegenWhenNotFull() { return SERVER.IRONS_MANA_REGEN_WHEN_NOT_FULL.get(); }
    /** 网络独立回蓝（需至少一个成员在线）。 */
    public static boolean ironsManaIndependentRegen() { return SERVER.IRONS_MANA_INDEPENDENT_REGEN.get(); }
    /** 网络魔力上限（0=不限）。 */
    public static long ironsManaMax() { return SERVER.IRONS_MANA_MAX.get(); }

    // ── 仪式环境绕过（Goety 原版仪式）──
    public static boolean ritualEnvBypass() { return SERVER.RITUAL_ENV_BYPASS.get(); }
    /** 环境绕过生效时按次额外消耗的魂量（0 = 关闭）。 */
    public static long ritualEnvBypassSoulCost() { return SERVER.RITUAL_ENV_SOUL_COST.get(); }
    public static int ritualEnvPathwayCount() { return SERVER.RITUAL_ENV_PATHWAY_COUNT.get(); }
    public static int ritualEnvConnectCount() { return SERVER.RITUAL_ENV_CONNECT_COUNT.get(); }
    public static int ritualEnvRadius() { return SERVER.RITUAL_ENV_RADIUS.get(); }
    /** 允许祭坛正下方的诅咒之笼被维度网络通道（net_pathway）替代，取施法者主网络作魂源。 */
    public static boolean ritualPathwayCage() { return SERVER.RITUAL_PATHWAY_CAGE.get(); }
}
