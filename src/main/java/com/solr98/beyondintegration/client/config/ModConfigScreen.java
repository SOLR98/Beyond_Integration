package com.solr98.beyondintegration.client.config;

import com.solr98.beyondintegration.CommandConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModConfigScreen {

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

        // ========== 附魔分离 ==========
        ConfigCategory enchant = builder.getOrCreateCategory(
                Component.translatable("beyond_integration.config.enchant"));

        enchant.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.separation"),
                CommandConfig.SERVER.ENABLE_ENCHANTMENT_SEPARATION.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.ENABLE_ENCHANTMENT_SEPARATION::set)
                .build());

        enchant.addEntry(eb.startBooleanToggle(
                Component.translatable("beyond_integration.config.enchant.item_extraction"),
                CommandConfig.SERVER.ENABLE_ITEM_ENCHANTMENT_SEPARATION.get())
                .setDefaultValue(true)
                .setSaveConsumer(CommandConfig.SERVER.ENABLE_ITEM_ENCHANTMENT_SEPARATION::set)
                .build());

        enchant.addEntry(eb.startDoubleField(
                Component.translatable("beyond_integration.config.enchant.item_multiplier"),
                CommandConfig.SERVER.ITEM_SEPARATION_MULTIPLIER.get())
                .setDefaultValue(2.0)
                .setMin(1.0).setMax(100.0)
                .setSaveConsumer(CommandConfig.SERVER.ITEM_SEPARATION_MULTIPLIER::set)
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

        return builder.build();
    }
}

