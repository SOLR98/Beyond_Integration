package com.solr98.beyondintegration.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GeneralConfig {

    private static Fields fields;

    private GeneralConfig() {}

    public static void build(ModConfigSpec.Builder builder) {
        builder.comment("通用设置").comment("General settings").push("general");
        fields = new Fields(builder);
        builder.pop();
    }

    public static class Fields {
        public final ModConfigSpec.EnumValue<com.solr98.beyondintegration.CommandConfig.Language> language;
        public final ModConfigSpec.IntValue maxNetworksPerPage;

        Fields(ModConfigSpec.Builder builder) {
            language = builder.defineEnum("command_language", com.solr98.beyondintegration.CommandConfig.Language.EN_US);
            maxNetworksPerPage = builder.defineInRange("max_networks_per_page", 10, 1, 100);
        }
    }

    public static Fields fields() { return fields; }

    public static com.solr98.beyondintegration.CommandConfig.Language language() { return fields.language.get(); }
    public static int maxNetworksPerPage() { return fields.maxNetworksPerPage.get(); }
}
