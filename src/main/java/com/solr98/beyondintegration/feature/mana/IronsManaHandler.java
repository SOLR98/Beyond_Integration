package com.solr98.beyondintegration.feature.mana;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Iron's Spells 网络魔力源——施法消耗接入。
 * <p>本次施法蓝耗优先由玩家自身魔力支付，不足的缺口从网络扣减。
 */
public class IronsManaHandler {

    @SubscribeEvent
    public void onSpellOnCast(SpellOnCastEvent event) {
        if (!CommandConfig.ironsManaEnabled() || !CommandConfig.ironsManaPayConsumption()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var castSource = event.getCastSource();
        if (castSource == null || !castSource.consumesMana()) return;
        MagicData md = MagicData.getPlayerMagicData(sp);
        if (md.getPlayerRecasts().hasRecastForSpell(event.getSpellId())) return;
        if (sp.isCreative() && !IronsConfigCompat.creativeManaCost()) return;
        int cost = event.getManaCost();
        if (cost <= 0) return;
        DimensionsNet net = ManaNetSource.usableNetOf(sp);
        if (net == null) return;
        float mana = md.getMana();
        if (mana >= cost) return;
        long deficit = (long) Math.ceil(cost - mana);
        if (deficit > 0L) {
            ManaAccess.extractMana(net, deficit);
        }
    }
}
