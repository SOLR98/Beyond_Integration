package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.soul.NetworkSoulSource;
import com.solr98.beyondintegration.feature.soul.SoulDebug;
import com.solr98.beyondintegration.feature.soul.SoulEnergyAccess;
import com.solr98.beyondintegration.handler.SoulCageContext;
import com.solr98.beyondintegration.handler.RitualEnvSoulCharge;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import com.wintercogs.beyonddimensions.common.init.BDBlocks;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 暗黑祭坛：
 * <ul>
 *   <li>{@code activate} 起始时捕获施法者；</li>
 *   <li>{@code checkCage}：原判定失败时按 <b>维度网络通道</b>（正下方 {@code net_pathway}，
 *       取其绑定网络）或 <b>施法者主网络</b> 放宽；</li>
 *   <li>{@code tick} 中对 {@code cursedCageTile.getSouls()/decreaseSouls()} 的调用
 *       <b>重定向</b>（按 {@code ordinal} 消歧）：牢笼为 null 时改从通道绑定的网络取/扣魂。
 *       {@code @Redirect} 会把 null 接收者安全传入，不会 NPE；也不再需要假牢笼（避免其
 *       {@code level} 为 null 导致 {@code markUpdated} NPE）。</li>
 * </ul>
 * 仅 1.20.1，{@code goety} 门控。
 */
@Mixin(targets = "com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity", remap = false)
public abstract class DarkAltarBlockEntityMixin implements RitualEnvSoulCharge {

    @Shadow(remap = false)
    private com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity cursedCageTile;

    @Unique
    private Player beyond$pendingCaster;

    /** 玩家起始仪式时捕获施法者（此时 castingPlayer 字段尚未设置）。 */
    @Inject(method = "activate", at = @At("HEAD"), remap = false)
    private void beyond$captureCaster(Level world, BlockPos pos, Player player,
                                      InteractionHand hand, Direction face, CallbackInfoReturnable<Boolean> cir) {
        this.beyond$pendingCaster = player;
    }

    /** 服务端：祭坛正下方为 {@code net_pathway} 且其绑定网络可用时返回该网络；否则 null。 */
    @Unique
    private DimensionsNet beyond$pathwayNet() {
        var self = (com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) return null;
        if (!CommandConfig.ritualPathwayCage()) return null;
        BlockPos below = self.getBlockPos().below();
        if (!level.getBlockState(below).is(BDBlocks.NET_PATHWAY.get())) return null;
        if (!(level.getBlockEntity(below) instanceof NetedBlockEntity nbe)) return null;
        DimensionsNet net = nbe.getNet();
        return NetworkSoulSource.usable(net) ? net : null;
    }

    /** 正下方是否为已绑定的维度网络通道（含客户端，仅看方块/绑定）。 */
    @Unique
    private boolean beyond$pathwayBelow(Level level, BlockPos below) {
        if (!CommandConfig.ritualPathwayCage()) return false;
        if (!level.getBlockState(below).is(BDBlocks.NET_PATHWAY.get())) return false;
        return level.getBlockEntity(below) instanceof NetedBlockEntity nbe && nbe.getNetId() >= 0;
    }

    @Inject(method = "checkCage", at = @At("RETURN"), cancellable = true, remap = false)
    private void beyond$cageNetworkFallback(CallbackInfoReturnable<Boolean> cir) {
        var self = (com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity) (Object) this;
        Player caster = self.castingPlayer != null ? self.castingPlayer : this.beyond$pendingCaster;
        Level level = self.getLevel();

        // 真牢笼通过：注入施法者上下文
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            if (this.cursedCageTile instanceof SoulCageContext ctx) {
                ctx.beyond$setSoulContextPlayer(caster);
            }
            return;
        }

        // 维度网络通道替代牢笼（正下方 net_pathway）：不设 cursedCageTile（保持 null），
        // 取/扣魂由 tick 的重定向处理。
        if (level != null) {
            BlockPos below = self.getBlockPos().below();
            if (beyond$pathwayBelow(level, below)) {
                if (level.isClientSide()) {
                    cir.setReturnValue(true); // 客户端：仅供 LIT 显示
                    return;
                }
                DimensionsNet net = beyond$pathwayNet();
                if (net != null && SoulEnergyAccess.getSouls(net) > 0L) {
                    SoulDebug.log("altar checkCage pathway -> net {} souls={}",
                            net.getId(), SoulEnergyAccess.getSouls(net));
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        // 空笼 / 原生魂不足：回退到施法者主网络
        DimensionsNet net = NetworkSoulSource.mainNetOf(caster);
        if (net == null || this.cursedCageTile == null) return;
        if (this.cursedCageTile instanceof SoulCageContext ctx) {
            ctx.beyond$setSoulContextPlayer(caster);
        }
        long souls = SoulEnergyAccess.getSouls(net);
        if (souls > 0L) {
            SoulDebug.log("altar checkCage fallback -> net {} souls={}", net.getId(), souls);
            cir.setReturnValue(true);
        }
    }

    // ── tick 中牢笼取魂/扣魂：牢笼为 null 时改走通道绑定的网络 ──
    // getSouls() 在 tick 中出现两次，用 ordinal 消歧。

    @Redirect(method = "tick", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/Polarice3/Goety/common/blocks/entities/CursedCageBlockEntity;getSouls()I"), remap = false)
    private int beyond$tickGetSouls0(com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity cage) {
        return beyond$resolveSouls(cage);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/Polarice3/Goety/common/blocks/entities/CursedCageBlockEntity;getSouls()I"), remap = false)
    private int beyond$tickGetSouls1(com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity cage) {
        return beyond$resolveSouls(cage);
    }

    @Unique
    private int beyond$resolveSouls(com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity cage) {
        if (cage != null) return cage.getSouls();
        DimensionsNet net = beyond$pathwayNet();
        return net == null ? 0 : SoulEnergyAccess.safeLongToInt(SoulEnergyAccess.getSouls(net));
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/Polarice3/Goety/common/blocks/entities/CursedCageBlockEntity;decreaseSouls(I)V"), remap = false)
    private void beyond$tickDecreaseSouls(com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity cage, int souls) {
        if (cage != null) {
            cage.decreaseSouls(souls);
            return;
        }
        DimensionsNet net = beyond$pathwayNet();
        if (net != null) SoulEnergyAccess.extractSouls(net, souls);
    }

    // ── 环境绕过的额外魂消耗（按次；仅仪式起始阶段） ──

    @Override
    public boolean beyond$chargeEnvBypassSoul(long amount) {
        var self = (com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity) (Object) this;
        // 仪式进行中（tick 期间的绕过判定）：起始阶段已扣，允许绕过、不再重复扣
        if (self.getCurrentRitualRecipe() != null) return true;
        if (amount <= 0L) return true;
        // 与仪式扣魂一致：先牢笼（其 decreaseSouls 原生优先，不足由网络补），无牢笼则走通道网络
        if (this.cursedCageTile != null) {
            long have = this.cursedCageTile.getSouls();
            if (have < amount) return false; // 魂不足 → 阻止绕过（不扣除）
            this.cursedCageTile.decreaseSouls((int) Math.min(amount, (long) SoulEnergyAccess.MAX_SAFE_INT));
            return true;
        }
        DimensionsNet net = beyond$pathwayNet();
        if (net == null || SoulEnergyAccess.getSouls(net) < amount) return false; // 魂不足/无魂源 → 阻止绕过
        SoulEnergyAccess.extractSouls(net, amount);
        return true;
    }
}
