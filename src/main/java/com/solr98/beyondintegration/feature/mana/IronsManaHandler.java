package com.solr98.beyondintegration.feature.mana;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Iron's Spells 'n Spellbooks 网络魔力源——施法消耗接入。
 * <p><b>消耗</b>（{@link SpellOnCastEvent}，扣蓝前）：本次施法蓝耗优先由玩家自身魔力支付，
 * 不足的缺口从网络扣减。
 * <p><b>回复沉淀</b>（满蓝溢出）由 {@code mixin/MagicManagerManaMixin} 处理。
 * <p>仅服务端玩家；需 {@code irons_spellbooks} 已加载（由主类门控注册）。
 */
public class IronsManaHandler {

    /** 消耗：优先玩家、缺口网络补。 */
    @SubscribeEvent
    public void onSpellOnCast(SpellOnCastEvent event) {
        if (!CommandConfig.ironsManaEnabled() || !CommandConfig.ironsManaPayConsumption()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var castSource = event.getCastSource();
        if (castSource == null || !castSource.consumesMana()) return;
        MagicData md = MagicData.getPlayerMagicData(sp);
        // 复刻原版扣蓝前提：recast 期间、创造绕过时原版不扣蓝，故网络也不参与
        if (md.getPlayerRecasts().hasRecastForSpell(event.getSpellId())) return;
        if (sp.isCreative() && !IronsConfigCompat.creativeManaCost()) return;
        int cost = event.getManaCost();
        if (cost <= 0) return;
        DimensionsNet net = ManaNetSource.usableNetOf(sp);
        if (net == null) return;
        float mana = md.getMana();
        // 持续法术的中断判定已由 MagicManagerContinuousManaMixin 计入网络蓝，
        // 这里统一按「本地优先、缺口网络补」支付（不写回本地）
        if (mana >= cost) return; // 优先玩家：自身够则不干预
        long deficit = (long) Math.ceil(cost - mana);
        if (deficit > 0L) {
            ManaAccess.extractMana(net, deficit);
        }
    }
}
