package com.solr98.beyondintegration;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 模组客户端配置（ForgeConfigSpec，CLIENT 类型，仅客户端生效）。
 * 记录客户端 UI 偏好：TACZ 枪械工作台的"网络模式/原版模式"等开关。
 */
public class ClientConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ClientValues CLIENT;

    static {
        final Pair<ClientValues, ForgeConfigSpec> specPair =
                new ForgeConfigSpec.Builder().configure(ClientValues::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static class ClientValues {
        /** TACZ 枪械工作台是否使用网络材料模式（默认开启） */
        public final ForgeConfigSpec.BooleanValue taczSmithUseNetwork;
        /** TACZ 枪械工作台合成产物是否输出到网络（默认关闭） */
        public final ForgeConfigSpec.BooleanValue taczSmithOutputToNetwork;
        /** 工作站归还方向按钮持久化：关闭/清空时物品的优先归还方向（false=背包优先，true=网络优先） */
        public final ForgeConfigSpec.BooleanValue workstationReturnToStorage;

        ClientValues(ForgeConfigSpec.Builder builder) {
            taczSmithUseNetwork = builder
                    .comment("TACZ gun smith table: use network materials mode by default")
                    .define("tacz_smith_use_network", true);
            taczSmithOutputToNetwork = builder
                    .comment("TACZ gun smith table: output crafted result to network by default")
                    .define("tacz_smith_output_to_network", false);
            workstationReturnToStorage = builder
                    .comment("Workstation return-direction button persistence (like BD uiCraftReturnButton)",
                            "false = player inventory first (default), true = network storage first",
                            "  Example: workstationReturnToStorage=true -> button defaults to network, kept after restart")
                    .define("workstation_return_to_storage", false);
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

    /** 写入工作站归还方向并保存客户端配置 */
    public static void setWorkstationReturnToStorage(boolean v) {
        CLIENT.workstationReturnToStorage.set(v);
        CLIENT_SPEC.save();
    }
}
