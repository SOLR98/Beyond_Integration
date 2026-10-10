package com.solr98.beyondintegration.client.mirror;

import com.solr98.beyondintegration.client.PrimaryNetClientStorage;
import com.solr98.beyondintegration.core.sync.NetDataType;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 条件式取数入口：业务统一走此 API。
 * <p>「共享优先，缺失回退」：{@link #available(NetDataType)} 为真（服务端实际推送了该类型、
 * 且已同步到可用主网络）时读共享镜像，否则调用方回退到各自独立通道。
 * <p>以服务端实际推送的类型位为准，不重复读本地配置做可用性判断，避免同步竞态。
 */
public final class SharedNetData {

    private SharedNetData() {}

    /** 只依赖接口，不依赖具体实现。 */
    private static ClientNetworkMirror mirror() {
        return PrimaryNetClientStorage.INSTANCE;
    }

    /** 该类型是否可从共享镜像取（已同步到主网络 + 服务端实际推了该类型）。 */
    public static boolean available(NetDataType type) {
        ClientNetworkMirror m = mirror();
        return m.hasNetwork() && m.supports(type);
    }

    /** 灵魂量（SOUL）；不可用返回 empty。 */
    public static OptionalLong soul(int netId) {
        return available(NetDataType.SOUL) ? mirror().getAmount(NetDataType.SOUL) : OptionalLong.empty();
    }

    /** 网络能量（ENERGY）；不可用返回 empty。 */
    public static OptionalLong energy(int netId) {
        return available(NetDataType.ENERGY) ? mirror().getAmount(NetDataType.ENERGY) : OptionalLong.empty();
    }

    /** 虚拟弹药（EXT_AMMO）；不可用返回 empty。 */
    public static Optional<Map<String, Long>> extAmmo() {
        return available(NetDataType.EXT_AMMO) ? Optional.of(mirror().getExtAmmo()) : Optional.empty();
    }

    /** 原子键数量直达（镜像缺失返回 0）。 */
    public static long itemCount(IStackKey<?> key) {
        return mirror().getCount(key);
    }
}
