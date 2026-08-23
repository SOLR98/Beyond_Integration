package com.solr98.beyondintegration.client.config;

import com.solr98.beyondintegration.CommandConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config 配置界面：将服务端配置按分类展示（语言/分页、附魔分离、
 * 载具充电、物品黑名单、合成参数），修改后直接写入服务端配置。
 */
public class ModConfigScreen {

    /** 创建配置界面（parent 为返回界面），通过 Cloth Config API 构建各分类条目 */
    public static Screen createScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("beyond_integration.config.title"));

        ConfigEntryBuilder eb = builder.entryBuilder();

        // ========== 语言 / 分页 ==========
        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.general"));

        general.addEntry(eb.startEnumSelector(
                Component.translatable("beyond_integration.config.general.language"),
                CommandConfig.Language.class,
                CommandConfig.SERVER.language.get())
                .setDefaultValue(CommandConfig.Language.EN_US)
                .setSaveConsumer(CommandConfig.SERVER.language::set)
                .build());

        general.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.general.max_page"),
                CommandConfig.SERVER.maxNetworksPerPage.get())
                .setDefaultValue(10)
                .setMin(1).setMax(100)
                .setSaveConsumer(CommandConfig.SERVER.maxNetworksPerPage::set)
                .build());

        // ========== 客户端（TACZ 工作台模式） ==========
        ConfigCategory clientCat = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.client"));

        clientCat.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.client.smith_use_network"),
                com.solr98.beyondintegration.ClientConfig.CLIENT.taczSmithUseNetwork.get())
                .setDefaultValue(true)
                .setSaveConsumer(v -> {
                    com.solr98.beyondintegration.ClientConfig.setTaczSmithUseNetwork(v);
                })
                .build());

        clientCat.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.client.smith_output_network"),
                com.solr98.beyondintegration.ClientConfig.CLIENT.taczSmithOutputToNetwork.get())
                .setDefaultValue(false)
                .setSaveConsumer(v -> {
                    com.solr98.beyondintegration.ClientConfig.setTaczSmithOutputToNetwork(v);
                })
                .build());

        // ========== 附魔分离 ==========
        ConfigCategory enchant = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.enchant"));

        enchant.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.separation"),
                CommandConfig.SERVER.ENABLE_ENCHANTMENT_SEPARATION.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.ENABLE_ENCHANTMENT_SEPARATION::set)
                .build());

        enchant.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.enchant.base_cost"),
                CommandConfig.SERVER.ENCHANTMENT_SEPARATION_BASE_COST.get())
                .setDefaultValue(10)
                .setMin(0).setMax(1000)
                .setSaveConsumer(CommandConfig.SERVER.ENCHANTMENT_SEPARATION_BASE_COST::set)
                .build());

        enchant.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.enchant.level_mult"),
                CommandConfig.SERVER.ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER.get())
                .setDefaultValue(5)
                .setMin(0).setMax(100)
                .setSaveConsumer(CommandConfig.SERVER.ENCHANTMENT_SEPARATION_LEVEL_MULTIPLIER::set)
                .build());

        enchant.addEntry(eb.startDoubleField(
                Component.translatable("beyond_integration.config.enchant.default_mult"),
                CommandConfig.SERVER.DEFAULT_ENCHANTMENT_MULTIPLIER.get())
                .setDefaultValue(1.0)
                .setMin(0.1).setMax(10.0)
                .setSaveConsumer(CommandConfig.SERVER.DEFAULT_ENCHANTMENT_MULTIPLIER::set)
                .build());

        enchant.addEntry(eb.startStrField(
                Component.translatable("beyond_integration.config.enchant.formula"),
                CommandConfig.SERVER.COST_FORMULA.get())
                .setDefaultValue("base + (level - 1) * multiplier")
                .setSaveConsumer(CommandConfig.SERVER.COST_FORMULA::set)
                .build());

        enchant.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.use_formula"),
                CommandConfig.SERVER.USE_FORMULA.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.USE_FORMULA::set)
                .build());

        enchant.addEntry(eb.startStrList(
                Component.translatable("beyond_integration.config.enchant.high_cost"),
                new java.util.ArrayList<>(CommandConfig.SERVER.HIGH_COST_ENCHANTMENTS.get()))
                .setDefaultValue(java.util.Arrays.asList(
                        "minecraft:mending:3.0", "minecraft:frost_walker:3.0",
                        "minecraft:sharpness:1.2", "minecraft:protection:1.2"))
                .setSaveConsumer(list -> CommandConfig.SERVER.HIGH_COST_ENCHANTMENTS.set(new java.util.ArrayList<>(list)))
                .build());

        // ========== 载具充电 ==========
        ConfigCategory vehicle = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.vehicle"));

        vehicle.addEntry(eb.startEnumSelector(
                Component.translatable("beyond_integration.config.vehicle.charge_mode"),
                CommandConfig.VehicleChargeMode.class,
                CommandConfig.SERVER.swVehicleChargeMode.get())
                .setDefaultValue(CommandConfig.VehicleChargeMode.RATE)
                .setSaveConsumer(CommandConfig.SERVER.swVehicleChargeMode::set)
                .build());

        vehicle.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.vehicle.charge_rate"),
                CommandConfig.SERVER.swVehicleEnergyChargeRate.get())
                .setDefaultValue(500000)
                .setMin(0).setMax(Integer.MAX_VALUE)
                .setSaveConsumer(CommandConfig.SERVER.swVehicleEnergyChargeRate::set)
                .build());

        vehicle.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.vehicle.charge_interval"),
                CommandConfig.SERVER.swVehicleChargeInterval.get())
                .setDefaultValue(20)
                .setMin(1).setMax(1200)
                .setSaveConsumer(CommandConfig.SERVER.swVehicleChargeInterval::set)
                .build());

        vehicle.addEntry(eb.startDoubleField(
                Component.translatable("beyond_integration.config.vehicle.charge_percentage"),
                CommandConfig.SERVER.swVehicleChargePercentage.get())
                .setDefaultValue(0.0)
                .setMin(0.0).setMax(100.0)
                .setSaveConsumer(CommandConfig.SERVER.swVehicleChargePercentage::set)
                .build());

        // ========== 物品黑名单 ==========
        ConfigCategory blacklist = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.blacklist"));

        blacklist.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.blacklist.enable"),
                CommandConfig.SERVER.ENABLE_ITEM_BLACKLIST.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.ENABLE_ITEM_BLACKLIST::set)
                .build());

        blacklist.addEntry(eb.startStrList(
                Component.translatable("beyond_integration.config.blacklist.items"),
                new java.util.ArrayList<>(CommandConfig.SERVER.ITEM_BLACKLIST.get()))
                .setDefaultValue(java.util.Arrays.asList(
                        "minecraft:barrier", "minecraft:command_block"))
                .setSaveConsumer(list -> CommandConfig.SERVER.ITEM_BLACKLIST.set(new java.util.ArrayList<>(list)))
                .build());

        // ========== 合成 ==========
        ConfigCategory craft = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.craft"));

        craft.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.craft.cooldown"),
                CommandConfig.SERVER.CRAFT_COOLDOWN_MS.get())
                .setDefaultValue(1000)
                .setMin(0).setMax(60000)
                .setSaveConsumer(CommandConfig.SERVER.CRAFT_COOLDOWN_MS::set)
                .build());

        craft.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.craft.max_depth"),
                CommandConfig.SERVER.CRAFT_MAX_DEPTH.get())
                .setDefaultValue(6)
                .setMin(1).setMax(20)
                .setSaveConsumer(CommandConfig.SERVER.CRAFT_MAX_DEPTH::set)
                .build());

        craft.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.craft.block_reader"),
                CommandConfig.SERVER.BLOCK_BD_CONTAINER_READER.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.BLOCK_BD_CONTAINER_READER::set)
                .build());

        // ========== 弹药 ==========
        ConfigCategory ammo = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.ammo"));

        ammo.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.ammo.tacz_poll_enabled"),
                CommandConfig.SERVER.TACZ_AMMO_POLL_ENABLED.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.TACZ_AMMO_POLL_ENABLED::set)
                .build());

        ammo.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.ammo.tacz_poll_interval"),
                CommandConfig.SERVER.TACZ_AMMO_POLL_INTERVAL_TICKS.get())
                .setDefaultValue(10)
                .setMin(1).setMax(200)
                .setSaveConsumer(CommandConfig.SERVER.TACZ_AMMO_POLL_INTERVAL_TICKS::set)
                .build());

        ammo.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.ammo.sw_poll_enabled"),
                CommandConfig.SERVER.SW_AMMO_POLL_ENABLED.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.SW_AMMO_POLL_ENABLED::set)
                .build());

        ammo.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.ammo.sw_poll_interval"),
                CommandConfig.SERVER.SW_AMMO_POLL_INTERVAL_TICKS.get())
                .setDefaultValue(10)
                .setMin(1).setMax(200)
                .setSaveConsumer(CommandConfig.SERVER.SW_AMMO_POLL_INTERVAL_TICKS::set)
                .build());

        ammo.addEntry(eb.startStrList(
                Component.translatable("beyond_integration.config.ammo.extract_mappings"),
                new java.util.ArrayList<>(CommandConfig.SERVER.AMMO_EXTRACT_MAPPINGS.get()))
                .setDefaultValue(java.util.Collections.emptyList())
                .setSaveConsumer(list -> CommandConfig.SERVER.AMMO_EXTRACT_MAPPINGS.set(new java.util.ArrayList<>(list)))
                .build());

        // ========== 自动图腾 ==========
        ConfigCategory totem = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.totem"));

        totem.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.totem.enabled"),
                CommandConfig.SERVER.AUTO_TOTEM_ENABLED.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.AUTO_TOTEM_ENABLED::set)
                .build());

        totem.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.totem.respect_bypasses"),
                CommandConfig.SERVER.AUTO_TOTEM_RESPECT_BYPASSES.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.AUTO_TOTEM_RESPECT_BYPASSES::set)
                .build());

        totem.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.totem.restore_max_health"),
                CommandConfig.SERVER.AUTO_TOTEM_RESTORE_MAX_HEALTH.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.AUTO_TOTEM_RESTORE_MAX_HEALTH::set)
                .build());

        totem.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.totem.heal_to_full"),
                CommandConfig.SERVER.AUTO_TOTEM_HEAL_TO_FULL.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.AUTO_TOTEM_HEAL_TO_FULL::set)
                .build());

        totem.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.totem.cooldown"),
                CommandConfig.SERVER.AUTO_TOTEM_COOLDOWN_SECONDS.get())
                .setDefaultValue(60)
                .setMin(0).setMax(3600)
                .setSaveConsumer(CommandConfig.SERVER.AUTO_TOTEM_COOLDOWN_SECONDS::set)
                .build());

        totem.addEntry(eb.startStrList(
                Component.translatable("beyond_integration.config.totem.damage_blacklist"),
                new java.util.ArrayList<>(CommandConfig.SERVER.AUTO_TOTEM_DAMAGE_BLACKLIST.get()))
                .setDefaultValue(java.util.Arrays.asList("minecraft:out_of_world"))
                .setSaveConsumer(list -> CommandConfig.SERVER.AUTO_TOTEM_DAMAGE_BLACKLIST.set(new java.util.ArrayList<>(list)))
                .build());

        // ========== 铁砧 ==========
        ConfigCategory anvil = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.anvil"));

        anvil.addEntry(eb.startEnumSelector(
                Component.translatable("beyond_integration.config.anvil.cost_mode"),
                CommandConfig.AnvilChargeMode.class,
                CommandConfig.SERVER.anvilCostMode.get())
                .setDefaultValue(CommandConfig.AnvilChargeMode.LEVEL)
                .setSaveConsumer(CommandConfig.SERVER.anvilCostMode::set)
                .build());

        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.level_cap"),
                CommandConfig.SERVER.anvilLevelCap.get())
                .setDefaultValue(30)
                .setMin(0).setMax(1000)
                .setSaveConsumer(CommandConfig.SERVER.anvilLevelCap::set)
                .build());

        anvil.addEntry(eb.startLongField(
                Component.translatable("beyond_integration.config.anvil.points_cap"),
                CommandConfig.SERVER.anvilPointsCap.get())
                .setDefaultValue(5000L)
                .setMin(0).setMax(Long.MAX_VALUE)
                .setSaveConsumer(CommandConfig.SERVER.anvilPointsCap::set)
                .build());

        // ========== 铁砧附魔增强 ==========
        anvil.addEntry(eb.startEnumSelector(
                Component.translatable("beyond_integration.config.anvil.break_level_mode"),
                CommandConfig.BreakLevelMode.class,
                CommandConfig.SERVER.anvilBreakLevelMode.get())
                .setDefaultValue(CommandConfig.BreakLevelMode.OFF)
                .setSaveConsumer(CommandConfig.SERVER.anvilBreakLevelMode::set)
                .build());
        anvil.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.anvil.ignore_conflict"),
                CommandConfig.SERVER.anvilIgnoreConflict.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.anvilIgnoreConflict::set)
                .build());
        anvil.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.anvil.ignore_support"),
                CommandConfig.SERVER.anvilIgnoreSupport.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.anvilIgnoreSupport::set)
                .build());
        anvil.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.anvil.unrestricted"),
                CommandConfig.SERVER.anvilUnrestricted.get())
                .setDefaultValue(false)
                .setSaveConsumer(CommandConfig.SERVER.anvilUnrestricted::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.conflict_penalty"),
                CommandConfig.SERVER.anvilConflictPenalty.get())
                .setDefaultValue(2).setMin(0).setMax(1000000)
                .setSaveConsumer(CommandConfig.SERVER.anvilConflictPenalty::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.support_penalty"),
                CommandConfig.SERVER.anvilSupportPenalty.get())
                .setDefaultValue(5).setMin(0).setMax(1000000)
                .setSaveConsumer(CommandConfig.SERVER.anvilSupportPenalty::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.conflict_percent"),
                CommandConfig.SERVER.anvilConflictPercent.get())
                .setDefaultValue(0).setMin(0).setMax(1000000)
                .setSaveConsumer(CommandConfig.SERVER.anvilConflictPercent::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.support_percent"),
                CommandConfig.SERVER.anvilSupportPercent.get())
                .setDefaultValue(0).setMin(0).setMax(1000000)
                .setSaveConsumer(CommandConfig.SERVER.anvilSupportPercent::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.break_level_percent"),
                CommandConfig.SERVER.anvilBreakLevelPercent.get())
                .setDefaultValue(0).setMin(0).setMax(1000000)
                .setSaveConsumer(CommandConfig.SERVER.anvilBreakLevelPercent::set)
                .build());
        anvil.addEntry(eb.startIntField(
                Component.translatable("beyond_integration.config.anvil.unrestricted_percent"),
                CommandConfig.SERVER.anvilUnrestrictedPercent.get())
                .setDefaultValue(100).setMin(0).setMax(1000000)
                .setSaveConsumer(CommandConfig.SERVER.anvilUnrestrictedPercent::set)
                .build());

        return builder.build();
    }
}

