package com.solr98.beyondintegration.feature.maidpower;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;

/**
 * 网络「女仆 P 点」读写入口（单位 = {@code EntityPowerPoint#getValue()}，100 = 1.0 点）。
 * <p>供拾取入网、自动回填与命令/界面使用。
 */
public final class MaidPowerAccess {

    /** 1.0 点对应的存储单位。 */
    public static final long UNITS_PER_POINT = 100L;

    private MaidPowerAccess() {
    }

    /** 读取网络内 P 点余额（value 单位）。 */
    public static long read(DimensionsNet net) {
        if (net == null) return 0L;
        UnifiedStorage storage = net.getUnifiedStorage();
        if (storage == null) return 0L;
        return storage.getStackByKey(MaidPowerStackKey.INSTANCE).amount();
    }

    /** 写入网络，返回未能存入的余量（0 = 全部存入）。 */
    public static long insert(DimensionsNet net, long value) {
        if (net == null || value <= 0L) return value;
        UnifiedStorage storage = net.getUnifiedStorage();
        if (storage == null) return value;
        KeyAmount left = storage.insert(MaidPowerStackKey.INSTANCE, value, false);
        return left == null ? value : Math.max(0L, left.amount());
    }

    /** 从网络取出，返回实际取出量。 */
    public static long extract(DimensionsNet net, long value) {
        if (net == null || value <= 0L) return 0L;
        UnifiedStorage storage = net.getUnifiedStorage();
        if (storage == null) return 0L;
        KeyAmount got = storage.extract(MaidPowerStackKey.INSTANCE, value, false, false);
        return got == null ? 0L : Math.max(0L, got.amount());
    }

    /** value 单位 → 点（浮点）。 */
    public static float toPoints(long value) {
        return value / (float) UNITS_PER_POINT;
    }

    /** 点（浮点）→ value 单位（向上取整，避免少给）。 */
    public static long toUnits(double points) {
        return (long) Math.ceil(Math.max(0.0, points) * UNITS_PER_POINT);
    }
}
