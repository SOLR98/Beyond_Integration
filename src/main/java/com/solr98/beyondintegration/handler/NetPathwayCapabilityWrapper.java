package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.feature.netpathway.NetPathwayFilterAccess;
import com.wintercogs.beyonddimensions.api.capability.helper.CapabilityHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;

/**
 * 让 BD 暴露给邻居的存储带上「网络通道白名单过滤」。
 * <p>BD 0.8 的能力暴露统一走 {@link CapabilityHelper#USHandlerMap}：能力注册时按类型缓存
 * {@code USHandler}，运行时用 {@code apply(UnifiedStorage, CapCtx)} 生成对外视图。
 * 这里在所有模组初始化完成后把表里的 handler 包一层——当上下文方块实体实现了
 * {@link NetPathwayFilterAccess} 且开启了过滤时，先把存储换成
 * {@code NetPathwayFilteredStorage} 再交给原 handler。
 * <p>这样不需要注入 BD 的能力注册 lambda（0.7/0.8 结构不同），也不再依赖
 * {@code NetPathwayBlockEntity#getCapability} 是否存在。
 */
public final class NetPathwayCapabilityWrapper {

    private static boolean installed = false;

    private NetPathwayCapabilityWrapper() {
    }

    /** 包装 BD 的能力视图工厂（幂等，由主类在模组加载完成时调用）。 */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            CapabilityHelper.USHandlerMap.replaceAll((typeId, handler) -> (us, ctx) -> {
                UnifiedStorage storage = us;
                if (ctx != null && ctx.be() instanceof NetPathwayFilterAccess access) {
                    storage = access.beyond$wrapStorage(us);
                }
                return handler.apply(storage, ctx);
            });
        } catch (Throwable ignored) {
            // BD 结构变化时静默跳过：通道过滤退化为原样暴露，不影响其它功能
        }
    }
}
