package com.solr98.beyondintegration.feature.bind;

import net.minecraft.core.BlockPos;
import java.util.UUID;

public class ExtractFlag {

    public enum Source {
        NONE,
        NET_INTERFACE,
        GUI,
        AMMO_CONSUMPTION,
        MAID_CONSUMPTION
    }

    private static final ThreadLocal<Source> source = ThreadLocal.withInitial(() -> Source.NONE);
    private static final ThreadLocal<BlockPos> flagPos = new ThreadLocal<>();
    private static final ThreadLocal<UUID> maidUuid = new ThreadLocal<>();
    private static final ThreadLocal<String> maidName = new ThreadLocal<>();

    public static void set(Source s) { source.set(s); }
    public static Source getSource() { return source.get(); }
    public static boolean isSet() { return source.get() != Source.NONE; }
    public static boolean isNetInterface() { return source.get() == Source.NET_INTERFACE; }
    public static void clear() { source.remove(); flagPos.remove(); maidUuid.remove(); maidName.remove(); }

    public static void setAmmoConsumption(BlockPos sentryPos) {
        source.set(Source.AMMO_CONSUMPTION);
        flagPos.set(sentryPos);
    }

    public static void setMaidConsumption(UUID uuid, String name, BlockPos pos) {
        source.set(Source.MAID_CONSUMPTION);
        flagPos.set(pos);
        maidUuid.set(uuid);
        maidName.set(name);
    }

    public static BlockPos getFlagPos() { return flagPos.get(); }
    public static UUID getMaidUuid() { return maidUuid.get(); }
    public static String getMaidName() { return maidName.get(); }
}
