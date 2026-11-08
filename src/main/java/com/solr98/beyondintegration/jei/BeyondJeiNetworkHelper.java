package com.solr98.beyondintegration.jei;

import com.solr98.beyondintegration.compat.RsIntegrationCompat;
import com.solr98.beyondintegration.network.ExtractNetworkItemPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * JEI 集成客户端助手：查询主网络库存，并请求从网络提取物品。
 * <p>
 * 数据源：{@link com.solr98.beyondintegration.client.PrimaryNetClientStorage}
 * （服务端 {@code bd_tweaks.primary_net_sync} 开启时下发的会话级主网络镜像）。
 * <b>不依赖 BD 界面</b>：BD 0.8.3 的存储界面已是 Compixel 实现，本模组不再持有其菜单/界面引用。
 * <p>
 * 数量缓存按<b>逻辑刻</b>更新：同一游戏刻内多次查询（多帧渲染 / tooltip / 点击判定）只查一次镜像，
 * 新刻自动清空缓存（容量不超过当前可见物品数，无长期内存增长）。
 * <p>
 * <b>装了 rs_integration（RI）时整套 JEI 集成禁用</b>：RI 自己给 BD 网络界面挂了自动进食等交互，
 * 两边的角标/点击取物会互相干扰（同一次点击被处理两次、角标语义重复）。禁用点有三处，
 * 这里是运行时兜底（即便 mixin 因 JEI 版本变化漏网，也不会显示角标、不会响应取物）。
 */
public final class BeyondJeiNetworkHelper {

    /** 当前刻的数量缓存（key → 数量）；每逻辑刻清空重建 */
    private static final Map<ItemStackKey, Long> COUNT_CACHE = new HashMap<>();
    /** 缓存对应的逻辑刻（{@code Long.MIN_VALUE} = 尚未建立；世界未加载时用 -1） */
    private static long cachedTick = Long.MIN_VALUE;

    private BeyondJeiNetworkHelper() {
    }

    /** 当前客户端逻辑刻（未进入世界返回 -1）。 */
    private static long currentTick() {
        var level = Minecraft.getInstance().level;
        return level == null ? -1L : level.getGameTime();
    }

    /** JEI 集成是否因 rs_integration 而整体禁用。 */
    public static boolean isDisabledByRsIntegration() {
        return RsIntegrationCompat.isLoaded();
    }

    /** 计数/取物是否可用：总开关开启、服务端已同步主网络，且未被 rs_integration 禁用。 */
    public static boolean isActive() {
        if (!com.solr98.beyondintegration.ClientConfig.jeiIntegration()) return false;
        if (isDisabledByRsIntegration()) return false;
        return com.solr98.beyondintegration.client.PrimaryNetClientStorage.INSTANCE.hasNetwork();
    }

    /** 查询主网络中该存储键的数量（按逻辑刻缓存；未同步或被 RI 禁用返回 0）。 */
    public static long getNetworkCount(ItemStackKey key) {
        if (key == null || key.isEmpty()) return 0L;
        if (!isActive()) return 0L;
        long tick = currentTick();
        if (tick != cachedTick) {
            COUNT_CACHE.clear();
            cachedTick = tick;
        }
        Long cached = COUNT_CACHE.get(key);
        if (cached != null) return cached;
        long count = com.solr98.beyondintegration.client.PrimaryNetClientStorage.INSTANCE.getCount(key);
        COUNT_CACHE.put(key, count);
        return count;
    }

    /** 查询主网络中该物品的数量（按逻辑刻缓存；未同步返回 0）。 */
    public static long getNetworkCount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        return getNetworkCount(new ItemStackKey(stack.copyWithCount(1)));
    }

    /** 请求从网络提取物品到背包（服务端校验权限与库存）。 */
    public static void requestExtract(ItemStack stack, int amount) {
        if (isDisabledByRsIntegration()) return;
        if (stack == null || stack.isEmpty() || amount <= 0) return;
        PacketHandler.sendToServer(new ExtractNetworkItemPacket(stack.copyWithCount(1), amount));
    }
}
