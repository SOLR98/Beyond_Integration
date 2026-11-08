package com.solr98.beyondintegration.client;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.client.Minecraft;

/**
 * 客户端“当前网络”解析：优先取当前打开的网络菜单（BD 存储/终端界面）同步下来的网络 ID，
 * 没有菜单（或菜单未同步）时回退主网络镜像（{@link PrimaryNetClientStorage}）。
 * <p>用于把服务端下发的网络级状态（附魔分离 / 自动充电 / 药水护符）写到正确的网络 ID 下，
 * 这样在存储界面里打开「网络设置」页时显示的是**该界面所连网络**的开关，而不是恒为主网络。
 */
public final class ClientMenuNet {

    private ClientMenuNet() {
    }

    /** 当前应读写的网络 ID；无法解析时返回 -1。 */
    public static int netId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof DimensionsNetMenu menu) {
            int id = menu.networkId();
            if (id != DimensionsNet.NO_PRIMARY_NET_ID) return id;
        }
        return PrimaryNetClientStorage.INSTANCE.netId();
    }
}
