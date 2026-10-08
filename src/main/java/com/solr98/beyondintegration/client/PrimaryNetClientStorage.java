package com.solr98.beyondintegration.client;

import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局「主网络」物品计数镜像（会话级，由服务端推送；与任何菜单无关）。
 * <p>
 * 服务端在玩家入服→退出期间持续推送主网络的物品计数；JEI 助手据此在任意界面显示库存/取物。
 * 服务端禁用该同步时不会推送（或发清空信号），此时助手回退到仅在 BD 终端界面读取菜单存储。
 */
public final class PrimaryNetClientStorage {

    /** 精确键 → 数量 */
    private static final Map<IStackKey<?>, Long> COUNTS = new HashMap<>();
    /** 是否已同步到可用主网络 */
    private static boolean hasNetwork = false;
    /** 当前主网络 ID（-1 表示无） */
    private static int netId = -1;
    /** 版本号（每次应用 +1） */
    private static int version = 0;

    private PrimaryNetClientStorage() {}

    /** 应用服务端同步：{@code clear=true} 清空缓存（禁用/无网络）。 */
    public static void apply(boolean clear, boolean hasNet, int id,
                             List<IStackKey<?>> keys, List<Long> counts) {
        if (clear) {
            COUNTS.clear();
            hasNetwork = hasNet;
            netId = id;
            version++;
            return;
        }
        int n = Math.min(keys.size(), counts.size());
        for (int i = 0; i < n; i++) {
            IStackKey<?> key = keys.get(i);
            if (key == null || key.isEmpty()) continue;
            long amount = counts.get(i);
            if (amount <= 0L) COUNTS.remove(key);
            else COUNTS.put(key, amount);
        }
        hasNetwork = hasNet;
        netId = id;
        version++;
    }

    /** 精确键的数量（缺失返回 0） */
    public static long getCount(IStackKey<?> key) {
        if (key == null || key.isEmpty()) return 0L;
        return COUNTS.getOrDefault(key, 0L);
    }

    /** 是否已同步到主网络 */
    public static boolean hasNetwork() {
        return hasNetwork;
    }

    public static int getNetId() {
        return netId;
    }

    public static int getVersion() {
        return version;
    }

    /** 清空缓存（断线/进服复位） */
    public static void clear() {
        COUNTS.clear();
        hasNetwork = false;
        netId = -1;
        version++;
    }
}
