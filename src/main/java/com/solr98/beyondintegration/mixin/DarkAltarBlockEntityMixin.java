package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.soul.NetworkSoulSource;
import com.solr98.beyondintegration.feature.soul.SoulDebug;
import com.solr98.beyondintegration.feature.soul.SoulEnergyAccess;
import com.solr98.beyondintegration.handler.SoulCageContext;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 暗黑祭坛：
 * <ul>
 *   <li>{@code activate} 起始时捕获施法者（用于空笼/原生魂不足时回退主网络）；</li>
 *   <li>{@code checkCage} 结束时把施法者注入牢笼上下文，并在原判定失败时按主网络放宽。</li>
 * </ul>
 * 仅 1.20.1，{@code goety} 门控。默认配置下行为不变。
 */
@Mixin(targets = "com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity", remap = false)
public abstract class DarkAltarBlockEntityMixin {

    @Shadow(remap = false)
    private com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity cursedCageTile;

    @Unique
    private Player beyond$pendingCaster;

    /** 玩家起始仪式时捕获施法者（此时 castingPlayer 字段尚未设置）。 */
    @Inject(method = "activate", at = @At("HEAD"), remap = false)
    private void beyond$captureCaster(Level world, BlockPos pos, Player player,
                                      InteractionHand hand, Direction face, CallbackInfo ci) {
        this.beyond$pendingCaster = player;
    }

    @Inject(method = "checkCage", at = @At("RETURN"), cancellable = true, remap = false)
    private void beyond$cageNetworkFallback(CallbackInfoReturnable<Boolean> cir) {
        var self = (com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity) (Object) this;
        Player caster = self.castingPlayer != null ? self.castingPlayer : this.beyond$pendingCaster;

        // 注入施法者上下文（若下方是牢笼）
        if (this.cursedCageTile instanceof SoulCageContext ctx) {
            ctx.beyond$setSoulContextPlayer(caster);
        }

        if (Boolean.TRUE.equals(cir.getReturnValue())) return;

        // 空笼 / 原生魂不足：回退到施法者主网络
        DimensionsNet net = NetworkSoulSource.mainNetOf(caster);
        if (net == null || this.cursedCageTile == null) return;
        long souls = SoulEnergyAccess.getSouls(net);
        if (souls > 0L) {
            SoulDebug.log("altar checkCage fallback -> net {} souls={}", net.getId(), souls);
            cir.setReturnValue(true);
        }
    }
}
