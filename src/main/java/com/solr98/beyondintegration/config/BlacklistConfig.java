package com.solr98.beyondintegration.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

public final class BlacklistConfig {

    private static Fields fields;

    private BlacklistConfig() {}

    public static void build(ModConfigSpec.Builder builder) {
        builder.comment("物品黑名单").comment("Item Blacklist").push("blacklist");
        fields = new Fields(builder);
        builder.pop();
    }

    public static class Fields {
        public final ModConfigSpec.BooleanValue enable;
        public final ModConfigSpec.ConfigValue<List<? extends String>> items;

        Fields(ModConfigSpec.Builder builder) {
            enable = builder.define("enable", false);
            items = builder.defineList("items",
                    Arrays.asList("minecraft:barrier", "minecraft:command_block"),
                    obj -> obj instanceof String);
        }
    }

    public static Fields fields() { return fields; }

    public static boolean enable() { return fields.enable.get(); }
    public static List<? extends String> items() { return fields.items.get(); }
}
