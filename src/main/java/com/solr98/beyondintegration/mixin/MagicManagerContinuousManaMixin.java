package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.mana.ManaAccess;
import com.solr98.beyondintegration.feature.mana.ManaNetSource;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 持续法术的中断判定计入网络魔力。
 * <p>Iron's Spells 在 {@code MagicManager.tick} 的 {@code lambda$tick$0} 里用
 * {@code MagicData.getMana() - spell.getManaCost(level) * 2 < 0} 决定是否中断持续施法，
 * 只看玩家本地蓝。网络魔力充足时会被误判为"蓝不够"而立即断法。
 * <p>这里把该处 {@code getMana()} 换成「本地蓝 + 网络魔力」，扣费仍由
 * {@code SpellOnCastEvent}（{@code IronsManaHandler}）从网络扣缺口，本地不再需要填蓝。
 * <p>1.20.1 的 {@code getMana()} 返回 int、1.21.1 返回 float，两个 {@code @Redirect}
 * 各自只在自己的版本命中（{@code require = 0}）。
 */
@Mixin(targets = "io.redspace.ironsspellbooks.capabilities.magic.MagicManager", remap = false)
public abstract class MagicManagerContinuousManaMixin {

    @Redirect(
            method = "lambda$tick$0",
            at = @At(value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;getMana()I"),
            remap = false,
            require = 0)
    private int beyond$manaWithNetworkInt(MagicData magicData, boolean doManaRegen, Player player) {
        return beyond$totalMana(magicData, player);
    }

    @Redirect(
            method = "lambda$tick$0",
            at = @At(value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;getMana()F"),
            remap = false,
            require = 0)
    private float beyond$manaWithNetworkFloat(MagicData magicData, boolean doManaRegen, Player player) {
        return beyond$totalMana(magicData, player);
    }

    /** 本地蓝 + 网络魔力（int 安全截断）；无网络或非服务端玩家时原样返回本地蓝。 */
    private static int beyond$totalMana(MagicData magicData, Player player) {
        int local = (int) Math.floor((double) magicData.getMana());
        if (!(player instanceof ServerPlayer serverPlayer)) return local;
        DimensionsNet net = ManaNetSource.usableNetOf(serverPlayer);
        if (net == null) return local;
        long network = ManaAccess.getMana(net);
        return (int) Math.min((long) local + network, Integer.MAX_VALUE);
    }
}
