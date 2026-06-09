package com.solr98.beyondintegration.handler;

import com.mojang.logging.LogUtils;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import java.lang.reflect.Field;

public class MenuNetIdHelper {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Field beyond$STORAGE_FIELD;
    private static final Field beyond$NET_FIELD;
    private static final Field beyond$CONTROL_NET_FIELD;

    static {
        Field sf = null, nf = null, cnf = null;
        try {
            sf = Class.forName("com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu").getField("storage");
            nf = UnifiedStorage.class.getDeclaredField("net");
            nf.setAccessible(true);
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve DimensionsNetMenu storage/UnifiedStorage net field for MenuNetIdHelper", e);
        }
        try {
            cnf = Class.forName("com.wintercogs.beyonddimensions.common.menu.NetControlMenu").getDeclaredField("net");
            cnf.setAccessible(true);
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve NetControlMenu net field for MenuNetIdHelper", e);
        }
        beyond$STORAGE_FIELD = sf;
        beyond$NET_FIELD = nf;
        beyond$CONTROL_NET_FIELD = cnf;
    }

    /** 客户端：从当前打开的 BD 菜单中读取同步过来的网络 ID */
    public static int getNetIdFromMenu(Player player) {
        if (player.containerMenu instanceof NetIdAccessor acc)
            return acc.beyond$getNetId();
        return -1;
    }

    /**
     * 服务端：从当前打开的 BD 菜单中反射获取 DimensionsNet
     * 支持 DimensionsNetMenu（及子类）和 NetControlMenu
     */
    @Nullable
    public static DimensionsNet getNetFromMenu(ServerPlayer player) {
        var menu = player.containerMenu;
        if (menu == null) return null;

        // DimensionsNetMenu / DimensionsCraftMenu 走 storage → UnifiedStorage.net
        if (beyond$STORAGE_FIELD != null && beyond$NET_FIELD != null) {
            try {
                Object storage = beyond$STORAGE_FIELD.get(menu);
                if (storage != null) {
                    Object net = beyond$NET_FIELD.get(storage);
                    if (net instanceof DimensionsNet dn) return dn;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to access DimensionsNet via storage reflection", e);
            }
        }

        // NetControlMenu 走其私有 net 字段
        if (beyond$CONTROL_NET_FIELD != null) {
            try {
                Object net = beyond$CONTROL_NET_FIELD.get(menu);
                if (net instanceof DimensionsNet dn) return dn;
            } catch (Exception e) {
                LOGGER.warn("Failed to access DimensionsNet via NetControlMenu reflection", e);
            }
        }

        return null;
    }
}
