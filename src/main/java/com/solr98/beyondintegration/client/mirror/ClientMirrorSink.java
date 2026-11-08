package com.solr98.beyondintegration.client.mirror;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

import java.util.Map;

/**
 * 客户端镜像写入接口：仅网络包 handler 依赖它（与只读 {@link ClientNetworkMirror} 解耦）。
 * <p>{@code typeMask} 声明本次含哪些 {@link com.solr98.beyondintegration.core.sync.NetDataType}；
 * 镜像据此落库并清理越界类型。
 */
public interface ClientMirrorSink {

    /**
     * 应用服务端推送。
     *
     * @param clear     true=清空（禁用 / 无网络），其余数据字段可忽略
     * @param hasNetwork 是否已同步到可用主网络
     * @param netId     主网络 ID（-1 表示无）
     * @param typeMask  本次包含的类型位（0 表示无存储数据）
     * @param counts    storage 键 → 绝对数量（可为空）
     * @param extAmmo   EXT_AMMO 虚拟弹药（可为空）
     * @param name      EXT_FLAGS 网络名（可为空）
     * @param flagsBits EXT_FLAGS 开关位（NetFlag.bit 组合）
     */
    void apply(boolean clear, boolean hasNetwork, int netId, int typeMask,
               Map<IStackKey<?>, Long> counts, Map<String, Long> extAmmo, String name, long flagsBits);
}
