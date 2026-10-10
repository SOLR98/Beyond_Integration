package com.solr98.beyondintegration.feature.soul;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.level.ServerPlayer;

/**
 * 手动转化来源：把玩家自身 SE 转化为网络灵魂（需 {@code source_manual=true}）。
 * <p>类体引用 Goety {@code SEHelper}，仅在调用时类加载；调用方需先确认 {@code goety} 已加载。
 */
public final class SoulManualConvert {

    private SoulManualConvert() {
    }

    public static boolean goetyLoaded() {
        var ml = net.minecraftforge.fml.ModList.get();
        return ml != null && ml.isLoaded("goety");
    }

    /** 把玩家 SE 中的 {@code amount} 转化为网络灵魂；返回实际转化量。 */
    public static long convert(ServerPlayer player, DimensionsNet net, int amount) {
        if (player == null || net == null || amount <= 0) return 0L;
        if (!CommandConfig.soulEnabled() || !CommandConfig.soulSourceManual()) return 0L;
        if (!com.Polarice3.Goety.utils.SEHelper.getSoulsAmount(player, amount)) return 0L;
        com.Polarice3.Goety.utils.SEHelper.decreaseSouls(player, amount);
        return SoulEnergyAccess.insertSouls(net, amount);
    }
}
