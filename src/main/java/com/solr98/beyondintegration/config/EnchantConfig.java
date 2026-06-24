package com.solr98.beyondintegration.config;

import com.solr98.beyondintegration.CommandConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

public final class EnchantConfig {

    private static Fields fields;

    private EnchantConfig() {}

    public static void build(ModConfigSpec.Builder builder) {
        builder.comment("附魔分离设置").comment("Enchantment Separation Settings").push("enchant");
        fields = new Fields(builder);
        builder.pop();
    }

    public static class Fields {
        public final ModConfigSpec.BooleanValue enableSeparation;
        public final ModConfigSpec.BooleanValue enableItemSeparation;
        public final ModConfigSpec.DoubleValue itemMult;
        public final ModConfigSpec.IntValue baseCost;
        public final ModConfigSpec.DoubleValue levelMult;
        public final ModConfigSpec.DoubleValue defaultMult;
        public final ModConfigSpec.ConfigValue<List<? extends String>> highCostList;
        public final ModConfigSpec.EnumValue<CommandConfig.EnchantFilterMode> filterMode;
        public final ModConfigSpec.ConfigValue<List<? extends String>> filterList;
        public final ModConfigSpec.EnumValue<CommandConfig.EnchantItemFilterMode> itemFilterMode;
        public final ModConfigSpec.ConfigValue<List<? extends String>> itemFilterList;

        Fields(ModConfigSpec.Builder builder) {
            enableSeparation = builder.define("separation", true);
            enableItemSeparation = builder.define("itemSeparation", true);
            itemMult = builder.defineInRange("itemMult", 2.0, 0.1, 100.0);
            baseCost = builder.defineInRange("base_cost", 5, 0, 100);
            levelMult = builder.defineInRange("level_mult", 1.0, 0.0, 100.0);
            defaultMult = builder.defineInRange("default_mult", 1.0, 0.0, 100.0);
            highCostList = builder.defineList("high_cost",
                    Arrays.asList("minecraft:mending:3.0", "minecraft:frost_walker:3.0",
                            "minecraft:sharpness:1.2", "minecraft:protection:1.2"),
                    obj -> obj instanceof String);
            filterMode = builder.defineEnum("filterMode", CommandConfig.EnchantFilterMode.DISABLED);
            filterList = builder.defineList("filterList", Arrays.asList("minecraft:mending"), obj -> obj instanceof String);
            itemFilterMode = builder.defineEnum("itemFilterMode", CommandConfig.EnchantItemFilterMode.DISABLED);
            itemFilterList = builder.defineList("itemFilterList",
                    Arrays.asList("minecraft:diamond_sword", "minecraft:enchanted_book"),
                    obj -> obj instanceof String);
        }
    }

    public static Fields fields() { return fields; }

    public static boolean enableSeparation() { return fields.enableSeparation.get(); }
    public static boolean enableItemSeparation() { return fields.enableItemSeparation.get(); }
    public static double itemMult() { return fields.itemMult.get(); }
    public static int baseCost() { return fields.baseCost.get(); }
    public static double levelMult() { return fields.levelMult.get(); }
    public static double defaultMult() { return fields.defaultMult.get(); }
    public static List<? extends String> highCostList() { return fields.highCostList.get(); }
    public static CommandConfig.EnchantFilterMode filterMode() { return fields.filterMode.get(); }
    public static List<? extends String> filterList() { return fields.filterList.get(); }
    public static CommandConfig.EnchantItemFilterMode itemFilterMode() { return fields.itemFilterMode.get(); }
    public static List<? extends String> itemFilterList() { return fields.itemFilterList.get(); }
}
