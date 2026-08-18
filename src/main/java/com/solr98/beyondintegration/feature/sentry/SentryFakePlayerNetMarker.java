package com.solr98.beyondintegration.feature.sentry;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandler;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 哨戒炮（Sentry）假玩家网络标记器：
 * 以弱引用 Map 维护"假玩家 → 绑定网络 ID"的映射，供其他模块
 * （如混合注入）查询假玩家所属的网络；网络销毁时自动解绑。
 */
public final class SentryFakePlayerNetMarker {

    /** 假玩家 → 绑定网络 ID 的弱引用映射（假玩家被回收时条目自动清理） */
    private static final Map<FakePlayer, Integer> bindings = new WeakHashMap<>();

    private SentryFakePlayerNetMarker() {}

    /** 将假玩家绑定到指定网络 ID */
    public static void mark(FakePlayer fp, int netId) {
        bindings.put(fp, netId);
    }

    /** 判断假玩家是否已绑定网络 */
    public static boolean isMarked(FakePlayer fp) {
        return bindings.containsKey(fp);
    }

    /** 获取假玩家绑定的网络 ID，未绑定时返回 -1 */
    public static int getNetId(FakePlayer fp) {
        return bindings.getOrDefault(fp, -1);
    }

    /** Get the actual network if bound, or null. Auto-unbinds on missing network. */
    public static DimensionsNet getNet(FakePlayer fp) {
        int id = getNetId(fp);
        if (id < 0) return null;
        DimensionsNet net = DimensionsNet.getNetFromId(id);
        if (net == null) bindings.remove(fp);
        return net;
    }

    public static void unmark(FakePlayer fp) {
        bindings.remove(fp);
    }

    /**
     * 通过物品处理器查找绑定了相同网络 ID 的假玩家：
     * 遍历处理器各槽位的 NetedItem，匹配其网络 ID 与绑定表。
     */
    public static FakePlayer getFakePlayerFromHandler(IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int netId = NetedItem.getNetId(stack);
            if (netId >= 0) {
                for (var entry : bindings.entrySet()) {
                    if (entry.getValue() == netId) return entry.getKey();
                }
            }
        }
        return null;
    }
}
