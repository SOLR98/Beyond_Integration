package com.solr98.beyondintegration.client.mirror;

import com.solr98.beyondintegration.core.sync.NetDataType;
import com.solr98.beyondintegration.core.sync.NetFlag;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

import java.util.Map;
import java.util.OptionalLong;

/**
 * 客户端「主网络镜像」只读接口：业务消费端（{@code SharedNetData} / HUD / JEI 助手）
 * 只依赖本接口，不依赖具体实现。
 * <p>镜像同时实现 BD 的 {@code IStackHandler}（继承 {@code AbstractUnorderedStackHandler}），
 * 因此也可直接用于 {@code getStackByKey} / {@code getBucket} 等既有只读扫描逻辑。
 */
public interface ClientNetworkMirror {

    /** 是否已同步到可用主网络。 */
    boolean hasNetwork();

    /** 当前主网络 ID（-1 表示无）。 */
    int netId();

    /** 服务端本次实际推送了哪些类型（由包内 typeMask 决定）。 */
    boolean supports(NetDataType type);

    /** 变更版本号（每次 apply 自增，用于 UI 失效判断）。 */
    int version();

    /** 原子键数量（缺失返回 0）。 */
    long getCount(IStackKey<?> key);

    /** 单一数值类型的量（ENERGY / SOUL）；不支持或缺失返回 empty。 */
    OptionalLong getAmount(NetDataType numericType);

    /** {@link NetDataType#EXT_AMMO}：虚拟弹药映射（不支持返回空 Map）。 */
    Map<String, Long> getExtAmmo();

    /** {@link NetDataType#EXT_FLAGS}：网络自定义名称（不支持返回空串）。 */
    String getNetworkName();

    /** {@link NetDataType#EXT_FLAGS}：开关状态（不支持返回 false）。 */
    boolean getFlag(NetFlag flag);

    /** 断线 / 进服 / 无网络时复位。 */
    void clear();
}
