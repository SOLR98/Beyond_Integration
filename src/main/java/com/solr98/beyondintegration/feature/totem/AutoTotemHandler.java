package com.solr98.beyondintegration.feature.totem;

import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 死亡时自动从网络（仅网络）使用图腾救援：黑名单伤害不触发，可配置启用/CD；
// BYPASSES_INVULNERABILITY 检查可配置（respect_bypasses_invulnerability，默认 false = 虚空/命令等也可触发）；
// 效果对齐原版 LivingEntity.checkTotemDeathProtection（1.20.1）：回血、清效果、buff、
// broadcastEntityEvent(35) 由客户端播图腾粒子/音效/弹出动画。
/**
 * 自动图腾处理器（Forge 事件订阅）：玩家死亡时自动从网络存储（仅网络）抽取图腾救援。
 * 支持启用开关、冷却时间与伤害黑名单（空列表 = 所有伤害类型均可触发）。
 */
public class AutoTotemHandler {

    // 玩家 UUID → 上次使用时间戳（冷却判定）
    private static final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    // 死亡事件处理：黑名单与 CD 内跳过；网络无图腾跳过；触发后取消死亡并施加原版图腾效果
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CommandConfig.autoTotemEnabled()) return;
        DamageSource source = event.getSource();
        // 可配置的原版判定：respect_bypasses_invulnerability=true 时，无视无敌标签的伤害（虚空/命令等）不触发图腾
        if (CommandConfig.autoTotemRespectBypassesInvulnerability()
                && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (isBlacklisted(source)) return;

        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUUID());
        if (last != null && now - last < CommandConfig.autoTotemCooldownSeconds() * 1000L) return;

        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return;
        KeyAmount got = net.getUnifiedStorage().extract(
                new ItemStackKey(new ItemStack(Items.TOTEM_OF_UNDYING)), 1, false, false);
        if (got.amount() <= 0) return;

        net.setDirty();
        lastUse.put(player.getUUID(), now);
        event.setCanceled(true);

        // 对齐原版 checkTotemDeathProtection
        player.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
        CriteriaTriggers.USED_TOTEM.trigger(player, new ItemStack(Items.TOTEM_OF_UNDYING));
        player.setHealth(1.0F);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        // 客户端实体事件 35：图腾粒子 + TOTEM_USE 音效 + 图腾弹出动画
        player.level().broadcastEntityEvent(player, (byte) 35);
    }

    // 登出时清理冷却记录，避免内存残留
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) lastUse.remove(event.getEntity().getUUID());
    }

    // 伤害源消息 ID（带或不带 minecraft: 前缀）是否命中配置黑名单（空列表 = 全部放行）
    private static boolean isBlacklisted(DamageSource source) {
        List<? extends String> list = CommandConfig.autoTotemDamageBlacklist();
        if (list == null || list.isEmpty()) return false;
        String msgId = source.getMsgId();
        String full = "minecraft:" + msgId;
        for (String entry : list) {
            if (entry == null || entry.trim().isEmpty()) continue;
            String e = entry.trim();
            if (e.equals(msgId) || e.equals(full)) return true;
        }
        return false;
    }
}
