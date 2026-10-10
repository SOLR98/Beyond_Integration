package com.solr98.beyondintegration.client;

import java.util.HashMap;
import java.util.Map;

/**
 * 网络药水护符客户端状态（按网络 ID 存储）：生效目标（PotionCharmMode 序号，默认 0=仅玩家）
 * 与经验修补是否已献祭解锁（默认未解锁）。
 * 由服务端 Sync 包（切换/献祭应答 / 打开界面请求应答）更新，供网络终端左侧按钮显示与切换。
 */
public final class PotionCharmState {

    private static final class Entry {
        final int mode;
        final boolean mending;
        Entry(int mode, boolean mending) {
            this.mode = mode;
            this.mending = mending;
        }
    }

    private static final Map<Integer, Entry> STATES = new HashMap<>();

    private PotionCharmState() {}

    /** 生效目标（无记录默认 0=仅玩家） */
    public static int getMode(int netId) {
        Entry e = STATES.get(netId);
        return e == null ? 0 : e.mode;
    }

    /** 经验修补是否已献祭解锁（无记录默认未解锁） */
    public static boolean isMendingUnlocked(int netId) {
        Entry e = STATES.get(netId);
        return e != null && e.mending;
    }

    /** 写入完整状态（服务端同步包） */
    public static void set(int netId, int mode, boolean mendingUnlocked) {
        STATES.put(netId, new Entry(mode, mendingUnlocked));
    }

    /** 仅更新生效目标（保留已解锁状态；本地乐观切换用） */
    public static void setMode(int netId, int mode) {
        Entry e = STATES.get(netId);
        boolean mending = e != null && e.mending;
        STATES.put(netId, new Entry(mode, mending));
    }

    public static void remove(int netId) {
        STATES.remove(netId);
    }

    public static void clear() {
        STATES.clear();
    }
}
