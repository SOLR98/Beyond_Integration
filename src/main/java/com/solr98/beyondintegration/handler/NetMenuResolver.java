package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端“当前网络”解析：优先取玩家当前打开的网络菜单所绑定的网络
 * （BD 存储/终端界面，`DimensionsNetMenu`），取不到时回退到主网络。
 * <p>BI 的网络级开关（附魔分离 / 自动充电 / 网络药水护符）原先只按主网络处理；
 * 这些开关现在既出现在「网络设置」页（挂在存储界面，可能打开的是某个非主网络），
 * 也兼容旧线的网络主界面，所以统一走本解析：
 * <ul>
 *   <li>有网络菜单：用菜单绑定的网络（服务端经 {@link INetMenuAccessor#getBoundNet()} 反射取得）；</li>
 *   <li>没有（命令、HUD、或菜单未绑定）：回退 {@link DimensionsNet#getPrimaryNetFromPlayer}。</li>
 * </ul>
 * 调用方仍需自行做权限校验（经理 / 所有者）。
 */
public final class NetMenuResolver {

    private NetMenuResolver() {
    }

    /** 解析玩家当前应操作的网络（可能为 null）。 */
    public static DimensionsNet resolve(ServerPlayer player) {
        if (player == null) return null;
        if (player.containerMenu instanceof INetMenuAccessor accessor) {
            try {
                DimensionsNet bound = accessor.getBoundNet();
                if (bound != null) return bound;
            } catch (Throwable ignored) {
                // 反射失败/菜单结构变化：回退主网络
            }
        }
        return DimensionsNet.getPrimaryNetFromPlayer(player);
    }

    /**
     * 便捷重载：接受任意 {@link net.minecraft.world.entity.player.Player}。
     * 非服务端玩家（客户端侧）返回 null，避免在客户端误读服务端网络状态。
     */
    public static DimensionsNet resolve(net.minecraft.world.entity.player.Player player) {
        return player instanceof ServerPlayer sp ? resolve(sp) : null;
    }
}
