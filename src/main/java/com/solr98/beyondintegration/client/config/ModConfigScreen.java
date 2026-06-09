package com.solr98.beyondintegration.client.config;
import com.solr98.beyondintegration.CommandConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;

public class ModConfigScreen {
    public static Screen createScreen(Screen parent) {
        var builder = ConfigBuilder.create().setParentScreen(parent).setTitle(Component.translatable("beyond_integration.config.title"));
        var eb = builder.entryBuilder();
        var cfg = CommandConfig.SERVER;

        // ── General ──
        var general = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.general"));
        general.addEntry(eb.startEnumSelector(Component.translatable("beyond_integration.config.general.language"), CommandConfig.Language.class, cfg.language.get())
                .setDefaultValue(CommandConfig.Language.EN_US).setSaveConsumer(cfg.language::set).build());
        general.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.general.max_networks_per_page"), cfg.maxNetworksPerPage.get())
                .setDefaultValue(10).setMin(1).setMax(100).setSaveConsumer(cfg.maxNetworksPerPage::set).build());

        // ── Enchant ──
        var enchant = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.enchant"));
        enchant.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.enchant.separation"), cfg.enchantSeparation.get())
                .setDefaultValue(true).setSaveConsumer(cfg.enchantSeparation::set).build());
        enchant.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.enchant.itemSeparation"), cfg.enchantItemSeparation.get())
                .setDefaultValue(true).setSaveConsumer(cfg.enchantItemSeparation::set).build());
        enchant.addEntry(eb.startDoubleField(Component.translatable("beyond_integration.config.enchant.itemMult"), cfg.enchantItemMult.get())
                .setDefaultValue(2.0).setMin(0.1).setMax(100.0).setSaveConsumer(cfg.enchantItemMult::set).build());
        enchant.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.enchant.base_cost"), cfg.enchantBaseCost.get())
                .setDefaultValue(5).setMin(0).setMax(100).setSaveConsumer(cfg.enchantBaseCost::set).build());
        enchant.addEntry(eb.startDoubleField(Component.translatable("beyond_integration.config.enchant.level_mult"), cfg.enchantLevelMult.get())
                .setDefaultValue(1.0).setMin(0.0).setMax(100.0).setSaveConsumer(cfg.enchantLevelMult::set).build());
        enchant.addEntry(eb.startDoubleField(Component.translatable("beyond_integration.config.enchant.default_mult"), cfg.enchantDefaultMult.get())
                .setDefaultValue(1.0).setMin(0.0).setMax(100.0).setSaveConsumer(cfg.enchantDefaultMult::set).build());
        enchant.addEntry(eb.startStrList(Component.translatable("beyond_integration.config.enchant.high_cost"), new ArrayList<>(cfg.enchantHighCostList.get()))
                .setDefaultValue(java.util.Arrays.asList("minecraft:mending:3.0", "minecraft:frost_walker:3.0",
                        "minecraft:sharpness:1.2", "minecraft:protection:1.2"))
                .setSaveConsumer(list -> cfg.enchantHighCostList.set(new ArrayList<>(list))).build());

        // ── Vehicle ──
        var vehicle = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.vehicle"));
        vehicle.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.vehicle.energyChargeRate"), cfg.swVehicleEnergyChargeRate.get())
                .setDefaultValue(500000).setMin(0).setMax(Integer.MAX_VALUE).setSaveConsumer(cfg.swVehicleEnergyChargeRate::set).build());
        vehicle.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.vehicle.chargeInterval"), cfg.swVehicleChargeInterval.get())
                .setDefaultValue(20).setMin(1).setMax(1200).setSaveConsumer(cfg.swVehicleChargeInterval::set).build());
        vehicle.addEntry(eb.startDoubleField(Component.translatable("beyond_integration.config.vehicle.chargePercentage"), cfg.swVehicleChargePercentage.get())
                .setDefaultValue(0.0).setMin(0.0).setMax(100.0).setSaveConsumer(cfg.swVehicleChargePercentage::set).build());

        // ── Ywzj Vehicle ──
        var ywzj = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.ywzj_vehicle"));
        ywzj.addEntry(eb.startEnumSelector(Component.translatable("beyond_integration.config.ywzj_vehicle.chargeMode"), CommandConfig.ChargeMode.class, cfg.ywzjChargeMode.get())
                .setDefaultValue(CommandConfig.ChargeMode.FLAT_RATE).setSaveConsumer(cfg.ywzjChargeMode::set).build());
        ywzj.addEntry(eb.startEnumSelector(Component.translatable("beyond_integration.config.ywzj_vehicle.fuelSource"), CommandConfig.FuelSource.class, cfg.ywzjFuelSource.get())
                .setDefaultValue(CommandConfig.FuelSource.FE).setSaveConsumer(cfg.ywzjFuelSource::set).build());
        ywzj.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.ywzj_vehicle.energyChargeRate"), cfg.ywzjVehicleEnergyChargeRate.get())
                .setDefaultValue(500000).setMin(0).setMax(Integer.MAX_VALUE).setSaveConsumer(cfg.ywzjVehicleEnergyChargeRate::set).build());
        ywzj.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.ywzj_vehicle.chargeInterval"), cfg.ywzjVehicleChargeInterval.get())
                .setDefaultValue(20).setMin(1).setMax(1200).setSaveConsumer(cfg.ywzjVehicleChargeInterval::set).build());
        ywzj.addEntry(eb.startDoubleField(Component.translatable("beyond_integration.config.ywzj_vehicle.chargePercentage"), cfg.ywzjVehicleChargePercentage.get())
                .setDefaultValue(5.0).setMin(0.1).setMax(100.0).setSaveConsumer(cfg.ywzjVehicleChargePercentage::set).build());
        ywzj.addEntry(eb.startIntField(Component.translatable("beyond_integration.config.ywzj_vehicle.energyConversion"), cfg.ywzjVehicleEnergyConversion.get())
                .setDefaultValue(1000).setMin(1).setMax(Integer.MAX_VALUE).setSaveConsumer(cfg.ywzjVehicleEnergyConversion::set).build());

        // ── Blacklist ──
        var blacklist = builder.getOrCreateCategory(Component.translatable("beyond_integration.config.blacklist"));
        blacklist.addEntry(eb.startBooleanToggle(Component.translatable("beyond_integration.config.blacklist.enable"), cfg.ENABLE_ITEM_BLACKLIST.get())
                .setDefaultValue(false).setSaveConsumer(cfg.ENABLE_ITEM_BLACKLIST::set).build());
        blacklist.addEntry(eb.startStrList(Component.translatable("beyond_integration.config.blacklist.items"), new ArrayList<>(cfg.ITEM_BLACKLIST.get()))
                .setDefaultValue(java.util.Arrays.asList("minecraft:barrier", "minecraft:command_block"))
                .setSaveConsumer(list -> cfg.ITEM_BLACKLIST.set(new ArrayList<>(list))).build());

        return builder.build();
    }
}
