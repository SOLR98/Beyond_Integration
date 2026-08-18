package com.solr98.beyondintegration.api;

import net.minecraftforge.fml.ModList;

/**
 * 模组集成通用接口。
 * 定义一个可选的（按 modId 检测是否加载）模组集成单元，
 * 仅在目标模组存在时才执行注册逻辑。
 */
public interface IModIntegration {

    /** 目标模组的 ID，例如 "tacz"、"superbwarfare"。 */
    String modId();

    /** 执行实际的注册逻辑（仅在目标模组已加载时调用）。 */
    void doRegister();

    /** 检查目标模组是否已加载。 */
    default boolean isLoaded() {
        return ModList.get().isLoaded(modId());
    }

    /** 若目标模组已加载则执行注册，否则静默跳过。 */
    default void register() {
        if (!isLoaded()) return;
        doRegister();
    }
}
