package com.solr98.beyondintegration.client;

import net.minecraft.core.BlockPos;

/**
 * 客户端灵魂源信标光柱（1.21.1 暂不渲染；仪式激活逻辑不受影响）。
 */
public final class SoulBeamClient {

    private SoulBeamClient() {
    }

    public static void add(BlockPos start, int height, int durationTicks,
                           float r, float g, float b, float beamRadius, float glowRadius) {
        // 1.21.1 渲染阶段 API 与 1.20.1 差异较大，暂不绘制；服务端动画结束仍会正式激活。
    }
}
