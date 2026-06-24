package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.mixin.DimensionsNetMenuStorageAccessor;
import com.solr98.beyondintegration.mixin.NetControlMenuAccessor;
import com.solr98.beyondintegration.mixin.UnifiedStorageAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class MenuNetIdHelper {

    public static int getNetIdFromMenu(Player player) {
        if (player.containerMenu instanceof NetIdAccessor acc)
            return acc.beyond$getNetId();
        return -1;
    }

    @Nullable
    public static DimensionsNet getNetFromMenu(ServerPlayer player) {
        var menu = player.containerMenu;
        if (menu == null) return null;

        if (menu instanceof DimensionsNetMenuStorageAccessor storageAcc) {
            var storage = storageAcc.getStorage();
            if (storage instanceof UnifiedStorageAccessor usAcc) {
                var net = usAcc.beyond$getNet();
                if (net != null) return net;
            }
        }

        if (menu instanceof NetControlMenuAccessor ncmAcc) {
            var net = ncmAcc.getNet();
            if (net != null) return net;
        }

        return null;
    }
}
