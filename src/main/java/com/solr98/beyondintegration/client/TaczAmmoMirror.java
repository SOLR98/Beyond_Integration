package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.client.mirror.SharedNetData;
import com.solr98.beyondintegration.compat.tud.TudAmmoCompat;
import com.solr98.beyondintegration.core.sync.NetSyncDebug;
import com.tacz.guns.api.item.IAmmo;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TACZ 弹药：主网络镜像重算（客户端）。
 * <p>物理弹药从镜像 storage 的 {@link ItemStackKey} 按弹药 ID 聚合（跨 NBT 变种）；
 * 创造箱无限来自镜像 {@code EXT_AMMO} 的 {@code tacz:} 前缀键（服务端从 {@code TaczCreativeAccessor} 打包）。
 * 按镜像版本缓存一次全量聚合结果，查询 O(1)。
 * <p>仅在 tacz 加载时被 TACZ 相关代码调用（{@link TaczAmmoCache}），故对 tacz 接口的引用安全。
 */
public final class TaczAmmoMirror {

    private static int cachedVersion = -1;
    private static Map<String, Integer> cachedCounts = Map.of();

    private TaczAmmoMirror() {}

    /** 镜像全量弹药计数（按镜像版本缓存）。 */
    public static Map<String, Integer> counts() {
        int ver = PrimaryNetClientStorage.INSTANCE.version();
        if (ver == cachedVersion) return cachedCounts;

        long t0 = NetSyncDebug.start();
        Map<String, Integer> result = new LinkedHashMap<>();

        // 创造箱无限（EXT_AMMO "tacz:*" / "tacz:<ammoId>"）
        SharedNetData.extAmmo().ifPresent(ext -> {
            if (ext.getOrDefault("tacz:*", 0L) > 0) {
                result.put("*", Integer.MAX_VALUE);
            }
            for (Map.Entry<String, Long> e : ext.entrySet()) {
                String key = e.getKey();
                if (!key.startsWith("tacz:")) continue;
                if (e.getValue() != null && e.getValue() > 0) {
                    result.put(key.substring("tacz:".length()), Integer.MAX_VALUE);
                }
            }
        });

        // 非全类型无限 → 累加物理弹药
        if (!Integer.valueOf(Integer.MAX_VALUE).equals(result.get("*"))) {
            for (KeyAmount ka : PrimaryNetClientStorage.INSTANCE.getStorage()) {
                if (ka == null || ka.isEmpty()) continue;
                if (!(ka.key() instanceof ItemStackKey ik)) continue;
                ItemStack stack = ik.getReadOnlyStack();
                if (stack.isEmpty()) continue;

                ResourceLocation id = null;
                if (stack.getItem() instanceof IAmmo ia) {
                    id = ia.getAmmoId(stack);
                } else if (TudAmmoCompat.isLoaded()) {
                    id = TudAmmoCompat.getItemAmmoId(stack);
                }
                if (id == null) continue;

                long count = ka.amount();
                if (count <= 0) continue;
                Integer existing = result.get(id.toString());
                if (existing != null && existing == Integer.MAX_VALUE) continue;
                long sum = count + (existing == null ? 0L : (long) existing);
                result.put(id.toString(), (int) Math.min(sum, Integer.MAX_VALUE));
            }
        }

        cachedVersion = ver;
        cachedCounts = result;
        NetSyncDebug.perf("taczAmmoMirror.counts", t0, "entries", result.size());
        return result;
    }

    /** 指定弹药 ID 的镜像计数（无限返回 {@code Integer.MAX_VALUE}）。 */
    public static int getCount(ResourceLocation ammoId) {
        if (ammoId == null) return 0;
        Map<String, Integer> m = counts();
        Integer all = m.get("*");
        if (all != null && all == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        Integer v = m.get(ammoId.toString());
        return v == null ? 0 : v;
    }
}
