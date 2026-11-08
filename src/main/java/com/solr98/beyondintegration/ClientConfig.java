package com.solr98.beyondintegration;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import com.solr98.beyondintegration.core.config.ConfigCommentLang;

import java.util.Collections;
import java.util.List;

/**
 * 模组客户端配置（ModConfigSpec，CLIENT 类型，仅客户端生效）。
 * 记录客户端 UI 偏好：TACZ 枪械工作台的“网络模式/原版模式”、HUD 位置布局等。
 */
public class ClientConfig {

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ClientValues CLIENT;

    static {
        final Pair<ClientValues, ModConfigSpec> specPair =
                new ModConfigSpec.Builder().configure(ClientValues::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static class ClientValues {
        /** TACZ 枪械工作台是否使用网络材料模式（默认开启） */
        public final ModConfigSpec.BooleanValue taczSmithUseNetwork;
        /** TACZ 枪械工作台合成产物是否输出到网络（默认关闭） */
        public final ModConfigSpec.BooleanValue taczSmithOutputToNetwork;
        /** 工作站归还方向按钮持久化：关闭/清空时物品的优先归还方向（false=背包优先，true=网络优先） */
        public final ModConfigSpec.BooleanValue workstationReturnToStorage;
        /** 附魔台悬停预览（客户端偏好；是否可用由服务端 enchantPreviewEnabled 决定） */
        public final ModConfigSpec.BooleanValue enchantPreviewOn;
        /** 附魔等级显示为罗马数字（关闭则用普通数字） */
        public final ModConfigSpec.BooleanValue enchantLevelRoman;
        /** 附魔合并工作站每页候选行数（默认 5，最高 10；改动需重开界面生效） */
        public final ModConfigSpec.IntValue enchantMergeRows;
        /** 右侧工作站切换按钮的顺序/可见集（有序枚举名列表；可隐藏或重排） */
        public final ModConfigSpec.ConfigValue<List<? extends String>> workstationOrder;
        /** 快捷栏上方显示主网络同步状态 HUD（客户端偏好；仅同步启用时显示） */
        public final ModConfigSpec.BooleanValue showNetSyncStatus;
        /** HUD 位置布局：每项 {@code <id>=RELATIVE:dx:dy} 或 {@code <id>=ABSOLUTE:x:y}；无项=默认位置 */
        public final ModConfigSpec.ConfigValue<List<? extends String>> hudLayout;
        /** 是否允许用快捷键打开 HUD 位置可视化编辑器 */
        public final ModConfigSpec.BooleanValue hudVisualEdit;
        /** 网络魔力 HUD（铁魔法联动）：法力条徽标 + 独立 HUD（客户端偏好） */
        public final ModConfigSpec.BooleanValue ironsManaHud;
        /** JEI 集成总开关（物品角标 + 鼠标点击取物；关闭后 BI 不再介入 JEI） */
        public final ModConfigSpec.BooleanValue jeiIntegration;

        ClientValues(ModConfigSpec.Builder builder) {
            taczSmithUseNetwork = builder
                    .comment(ConfigCommentLang.comment("tacz_smith_use_network"))
                    .define("tacz_smith_use_network", true);
            taczSmithOutputToNetwork = builder
                    .comment(ConfigCommentLang.comment("tacz_smith_output_to_network"))
                    .define("tacz_smith_output_to_network", false);
            workstationReturnToStorage = builder
                    .comment(ConfigCommentLang.comment("workstation_return_to_storage"))
                    .define("workstation_return_to_storage", false);
            enchantPreviewOn = builder
                    .comment(ConfigCommentLang.comment("enchant_preview_on"))
                    .define("enchant_preview_on", true);
            enchantLevelRoman = builder
                    .comment(ConfigCommentLang.comment("enchant_level_roman"))
                    .define("enchant_level_roman", true);
            enchantMergeRows = builder
                    .comment(ConfigCommentLang.comment("enchant_merge_rows"))
                    .defineInRange("enchant_merge_rows", 5, 1, 10);
            workstationOrder = builder
                    .comment(ConfigCommentLang.comment("workstation_order"))
                    .defineList("workstation_order",
                            java.util.Arrays.asList("ANVIL", "CUT", "GRIND", "SMITH", "CRAFT", "ENCHANT", "ENCHANT_MERGE"),
                            obj -> obj instanceof String);
            showNetSyncStatus = builder
                    .comment(ConfigCommentLang.comment("show_net_sync_status"))
                    .define("show_net_sync_status", false);
            hudLayout = builder
                    .comment(ConfigCommentLang.comment("hud_layout"))
                    .defineList("hud_layout", Collections.emptyList(), obj -> obj instanceof String);
            hudVisualEdit = builder
                    .comment(ConfigCommentLang.comment("hud_visual_edit"))
                    .define("hud_visual_edit", true);
            ironsManaHud = builder
                    .comment(ConfigCommentLang.comment("irons_mana_hud"))
                    .define("irons_mana_hud", true);
            jeiIntegration = builder
                    .comment(ConfigCommentLang.comment("jei_integration"))
                    .define("jei_integration", true);
        }
    }

    /** 读取 TACZ 工作台网络模式（客户端配置） */
    public static boolean taczSmithUseNetwork() { return CLIENT.taczSmithUseNetwork.get(); }

    /** 写入 TACZ 工作台网络模式并保存配置 */
    public static void setTaczSmithUseNetwork(boolean v) {
        CLIENT.taczSmithUseNetwork.set(v);
        CLIENT_SPEC.save();
    }

    /** 读取 TACZ 工作台产物入网络（客户端配置） */
    public static boolean taczSmithOutputToNetwork() { return CLIENT.taczSmithOutputToNetwork.get(); }

    /** 写入 TACZ 工作台产物入网络并保存配置 */
    public static void setTaczSmithOutputToNetwork(boolean v) {
        CLIENT.taczSmithOutputToNetwork.set(v);
        CLIENT_SPEC.save();
    }

    /** 读取工作站归还方向（客户端 UI 偏好） */
    public static boolean workstationReturnToStorage() { return CLIENT.workstationReturnToStorage.get(); }

    /** 读取附魔台悬停预览（客户端偏好） */
    public static boolean enchantPreviewOn() { return CLIENT.enchantPreviewOn.get(); }

    public static void setEnchantPreviewOn(boolean v) {
        CLIENT.enchantPreviewOn.set(v);
        CLIENT_SPEC.save();
    }

    /** 读取附魔等级是否用罗马数字（客户端偏好） */
    public static boolean enchantLevelRoman() { return CLIENT.enchantLevelRoman.get(); }

    public static void setEnchantLevelRoman(boolean v) {
        CLIENT.enchantLevelRoman.set(v);
        CLIENT_SPEC.save();
    }

    /** 读取右侧工作站按钮顺序/可见集（有序模式名列表） */
    public static List<? extends String> workstationOrder() { return CLIENT.workstationOrder.get(); }

    /** 写入右侧工作站按钮顺序/可见集并保存客户端配置 */
    public static void setWorkstationOrder(List<? extends String> v) {
        CLIENT.workstationOrder.set(v);
        CLIENT_SPEC.save();
    }

    public static void setWorkstationReturnToStorage(boolean v) {
        CLIENT.workstationReturnToStorage.set(v);
        CLIENT_SPEC.save();
    }

    /** 读取附魔合并每页候选行数（1-10，默认 5） */
    public static int enchantMergeRows() { return CLIENT.enchantMergeRows.get(); }

    /** 是否显示主网络同步状态 HUD（客户端偏好；默认关闭） */
    public static boolean showNetSyncStatus() { return CLIENT.showNetSyncStatus.get(); }

    /** 写入主网络同步状态 HUD 开关并保存 */
    public static void setShowNetSyncStatus(boolean v) {
        CLIENT.showNetSyncStatus.set(v);
        CLIENT_SPEC.save();
    }

    /** 写入附魔合并每页候选行数并保存客户端配置 */
    public static void setEnchantMergeRows(int v) {
        CLIENT.enchantMergeRows.set(v);
        CLIENT_SPEC.save();
    }

    // ── HUD 位置布局（客户端，统一）──
    public static List<? extends String> hudLayout() { return CLIENT.hudLayout.get(); }

    public static void setHudLayout(List<? extends String> v) {
        CLIENT.hudLayout.set(v);
        CLIENT_SPEC.save();
    }

    public static boolean hudVisualEdit() { return CLIENT.hudVisualEdit.get(); }

    /** 是否显示网络魔力 HUD（客户端偏好）。 */
    public static boolean ironsManaHud() { return CLIENT.ironsManaHud.get(); }

    /** 写入网络魔力 HUD 开关并保存 */
    public static void setIronsManaHud(boolean v) {
        CLIENT.ironsManaHud.set(v);
        CLIENT_SPEC.save();
    }

    public static void setHudVisualEdit(boolean v) {
        CLIENT.hudVisualEdit.set(v);
        CLIENT_SPEC.save();
    }

    /** 是否启用 JEI 集成（物品角标 + 点击取物；默认开启） */
    public static boolean jeiIntegration() { return CLIENT.jeiIntegration.get(); }

    /** 写入 JEI 集成开关并保存 */
    public static void setJeiIntegration(boolean v) {
        CLIENT.jeiIntegration.set(v);
        CLIENT_SPEC.save();
    }

}
