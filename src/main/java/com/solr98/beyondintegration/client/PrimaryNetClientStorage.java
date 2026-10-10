package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.client.mirror.ClientMirrorSink;
import com.solr98.beyondintegration.client.mirror.ClientNetworkMirror;
import com.solr98.beyondintegration.core.sync.NetDataType;
import com.solr98.beyondintegration.core.sync.NetFlag;
import com.solr98.beyondintegration.core.sync.NetSyncDebug;
import com.solr98.beyondintegration.feature.soul.SoulEnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * 全局「主网络」镜像（会话级，由服务端推送；与任何菜单无关）。
 * <p>
 * 同时是：
 * <ul>
 *   <li>业务只读接口 {@link ClientNetworkMirror}（供 {@code SharedNetData} 等条件取数）；</li>
 *   <li>BD {@code IStackHandler}（继承 {@link AbstractUnorderedStackHandler}），
 *       因此可直接复用既有只读扫描逻辑（如 TACZ 配方材料统计、桶装流体折算）。</li>
 * </ul>
 * 服务端在玩家入服→退出期间持续推送主网络数据；客户端据此在任意界面显示 / 取物，
 * 并按服务端下发的 {@code typeMask}（可用类型位）支持条件式取数。
 */
public final class PrimaryNetClientStorage extends AbstractUnorderedStackHandler
        implements ClientNetworkMirror, ClientMirrorSink {

    /** 单例（唯一镜像实例）。 */
    public static final PrimaryNetClientStorage INSTANCE = new PrimaryNetClientStorage();

    /** 已同步的数据类型位（由服务端 typeMask 决定）。 */
    private int availableTypes = 0;
    /** 是否已同步到可用主网络。 */
    private boolean hasNetwork = false;
    /** 当前主网络 ID（-1 表示无）。 */
    private int netId = -1;
    /** 变更版本号（每次 apply / clear 自增）。 */
    private int version = 0;

    /** EXT_AMMO：虚拟弹药映射。 */
    private final Map<String, Long> extAmmo = new HashMap<>();
    /** EXT_FLAGS：网络自定义名称。 */
    private String networkName = "";
    /** EXT_FLAGS：开关位（{@link NetFlag#bit()} 组合，这里用 ordinal 位）。 */
    private long flagsBits = 0L;

    private PrimaryNetClientStorage() {
        super(ZeroPolicy.REMOVE_ON_ZERO, UiTimestampPolicy.NONE);
    }

    /** 镜像为只读派生，不触发 BD 事件回环。 */
    @Override
    public void onChange() {
        // no-op
    }

    // ───────────────── ClientNetworkMirror ─────────────────

    @Override
    public boolean hasNetwork() {
        return hasNetwork;
    }

    @Override
    public int netId() {
        return netId;
    }

    @Override
    public boolean supports(NetDataType type) {
        return type != null && (availableTypes & type.bit()) != 0;
    }

    @Override
    public int version() {
        return version;
    }

    @Override
    public long getCount(IStackKey<?> key) {
        if (key == null || key.isEmpty()) return 0L;
        return getStackByKey(key).amount();
    }

    @Override
    public OptionalLong getAmount(NetDataType numericType) {
        if (numericType == NetDataType.ENERGY) return OptionalLong.of(getCount(EnergyStackKey.INSTANCE));
        if (numericType == NetDataType.SOUL) return OptionalLong.of(getCount(SoulEnergyStackKey.INSTANCE));
        return OptionalLong.empty();
    }

    @Override
    public Map<String, Long> getExtAmmo() {
        return extAmmo;
    }

    @Override
    public String getNetworkName() {
        return networkName;
    }

    @Override
    public boolean getFlag(NetFlag flag) {
        return flag != null && (flagsBits & (1L << flag.ordinal())) != 0;
    }

    // ───────────────── ClientMirrorSink ─────────────────

    @Override
    public void apply(boolean clear, boolean hasNetwork, int netId, int typeMask,
                      Map<IStackKey<?>, Long> counts, Map<String, Long> extAmmo, String name, long flagsBits) {
        long t0 = NetSyncDebug.start();
        if (clear) {
            clearInternal();
            this.availableTypes = 0;
            this.hasNetwork = hasNetwork;
            this.netId = netId;
            this.version++;
            NetSyncDebug.perf("apply.clear", t0, "net", netId);
            return;
        }

        // scope 降级：清理本次不再推送的类型（越界类型）
        if ((this.availableTypes & ~typeMask) != 0) {
            removeTypesOutside(typeMask);
        }
        this.availableTypes = typeMask;

        if (counts != null) {
            for (Map.Entry<IStackKey<?>, Long> e : counts.entrySet()) {
                if (e.getKey() == null) continue;
                long amount = e.getValue() == null ? 0L : e.getValue();
                setAmountByKey(e.getKey(), amount);
            }
        }

        this.extAmmo.clear();
        if ((typeMask & NetDataType.EXT_AMMO.bit()) != 0 && extAmmo != null) {
            this.extAmmo.putAll(extAmmo);
        }
        if (name != null) this.networkName = name;
        this.flagsBits = flagsBits;

        this.hasNetwork = hasNetwork;
        this.netId = netId;
        this.version++;
        NetSyncDebug.perf("apply", t0, "net", netId, "mask", typeMask, "keys", counts == null ? 0 : counts.size());
    }

    @Override
    public void clear() {
        clearInternal();
        this.availableTypes = 0;
        this.hasNetwork = false;
        this.netId = -1;
        this.version++;
    }

    // ───────────────── 内部 ─────────────────

    private void clearInternal() {
        clearStorage();
        extAmmo.clear();
        networkName = "";
        flagsBits = 0L;
    }

    /** 移除不在 {@code typeMask} 内的已知类型键（未知键保留）。 */
    private void removeTypesOutside(int typeMask) {
        List<IStackKey<?>> toRemove = new ArrayList<>();
        for (IStackKey<?> key : new ArrayList<>(storage.keySet())) {
            NetDataType type = NetDataType.of(key);
            if (type != null && (typeMask & type.bit()) == 0) {
                toRemove.add(key);
            }
        }
        for (IStackKey<?> key : toRemove) {
            storage.remove(key);
            removeFromIndex(key);
        }
    }
}
