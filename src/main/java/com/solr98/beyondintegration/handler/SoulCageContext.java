package com.solr98.beyondintegration.handler;

import net.minecraft.world.entity.player.Player;

/**
 * 灵魂方舟上下文访问器：由 {@code CursedCageBlockEntityMixin} 实现，
 * 供 {@code DarkAltarBlockEntityMixin} 在仪式 tick 时注入"施法者"，使牢笼可解析施法者主网络。
 */
public interface SoulCageContext {
    void beyond$setSoulContextPlayer(Player player);
}
