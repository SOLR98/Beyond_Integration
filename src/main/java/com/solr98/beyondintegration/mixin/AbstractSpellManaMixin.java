package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.mana.ManaAccess;
import com.solr98.beyondintegration.feature.mana.ManaNetSource;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Iron's Spells 网络魔力接入——施法校验放行。
 * <p>重定向 {@code canBeCastedBy} 中读取玩家魔力的 {@code playerMagicData.getMana()} 调用，返回
 * 「玩家魔力 + 网络魔力」，使原有的 {@code hasEnoughMana} 判定把网络计入——网络充足时自然放行，
 * 且不会影响该方法内的冷却/未学等其它判定分支。
 * <p>之后的实际扣减由 {@code IronsManaHandler.onSpellOnCast} 优先扣玩家、缺口从网络补。
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class AbstractSpellManaMixin {

    @Redirect(method = "canBeCastedBy",
              at = @At(value = "INVOKE",
                       target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;getMana()F"))
    private float beyond$manaWithNetwork(MagicData magicData, int spellLevel, CastSource castSource,
                                         MagicData playerMagicData, Player player) {
        float mana = magicData.getMana();
        if (!(player instanceof ServerPlayer sp)) return mana;
        if (!CommandConfig.ironsManaEnabled()) return mana;
        if (castSource == null || !castSource.consumesMana()) return mana;
        DimensionsNet net = ManaNetSource.usableNetOf(sp);
        if (net == null) return mana;
        // 合计上限封顶到 int-1（MAX_SAFE_INT），避免 long 溢出与超范围
        long netMana = Math.min(ManaAccess.getMana(net), (long) ManaAccess.MAX_SAFE_INT);
        long total = Math.min((long) mana + netMana, (long) ManaAccess.MAX_SAFE_INT);
        return (float) total;
    }
}
