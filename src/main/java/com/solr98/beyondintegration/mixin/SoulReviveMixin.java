package com.solr98.beyondintegration.mixin;

import com.Polarice3.Goety.config.MainConfig;
import com.Polarice3.Goety.utils.SEHelper;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.soul.NetworkSoulSource;
import com.solr98.beyondintegration.feature.soul.SoulDebug;
import com.solr98.beyondintegration.feature.soul.SoulEnergyAccess;
import com.solr98.beyondintegration.feature.soul.SoulVanillaSource;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 网络灵魂参与复活（{@code goety_soul.network_revive} 开启时）。
 * <p>灵魂源优先级：<b>原版（Arca → 图腾）→ 网络</b>。故在 Goety
 * {@code SoulEnergyEvents.onLivingDeathEvent} 的 {@code RETURN} 注入：
 * 原版已复活（事件被 cancel）则直接放行；否则在原版魂不足时由网络兜底
 * （合计魂量 ≥ 原版 {@code MaxSouls} 才复活，扣取先原版、余量由网络补）。
 * <p>落点：{@code network_revive_to_arca} 且玩家有绑定方舟 → 传送到方舟位置；否则原地。
 * <p>不改 {@code seActive}/Arca 权威；仅 1.20.1，{@code MixinPlugin} 按 {@code goety} 门控。
 */
@Mixin(targets = "com.Polarice3.Goety.common.events.SoulEnergyEvents", remap = false)
public abstract class SoulReviveMixin {

    @Inject(method = "onLivingDeathEvent", at = @At("RETURN"), cancellable = true, remap = false)
    private static void beyond$networkRevive(LivingDeathEvent event, CallbackInfo ci) {
        if (event.isCanceled()) return; // 原版 Arca/图腾 已复活：原版优先
        if (!CommandConfig.soulEnabled() || !CommandConfig.soulNetworkRevive()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        DimensionsNet net = NetworkSoulSource.usableNetOf(player);
        if (net == null) return;

        long cost = MainConfig.MaxSouls.get();
        if (cost <= 0L) return;
        // 合计（原版 + 网络）足够才复活
        long total = SoulVanillaSource.nativeSouls(player) + SoulEnergyAccess.getSouls(net);
        if (total < cost) return;

        // 扣取先原版、不足再由网络补（统一走 SEHelperMixin 的混合逻辑）
        com.Polarice3.Goety.utils.SEHelper.decreaseSouls(
                player, SoulEnergyAccess.safeLongToInt(cost));

        player.setHealth(1.0F);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        if (CommandConfig.soulNetworkReviveToArca() && SEHelper.getArcaBlock(player) != null) {
            SEHelper.teleportToArca(player);
        }
        SEHelper.sendSEUpdatePacket(player);
        event.setCanceled(true);
        ci.cancel();
        SoulDebug.log("network revive: net {} cost {} player {}", net.getId(), cost,
                player.getName().getString());
    }
}
