package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.soul.NetworkSoulSource;
import com.solr98.beyondintegration.feature.soul.SoulDebug;
import com.solr98.beyondintegration.feature.soul.SoulEnergyAccess;
import com.solr98.beyondintegration.handler.SoulCageContext;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 诅咒牢笼：把网络灵魂作为<b>额外魂源</b>（不改 Goety 的 SEActive/复活）。
 * <ul>
 *   <li>网络来源优先级：① 笼内携带 {@code NetId} 的"灵魂符"（BD 终端等）→ 该网络；
 *       ② 施法者上下文（{@link SoulCageContext}，由祭坛注入）或绑定玩家 → 主网络。</li>
 *   <li>{@code getSouls()}：原生魂 + 网络魂；{@code decreaseSouls(int)}：<b>原版优先</b>——
 *       原生魂够则不碰网络，不足时先扣光原生、余量由网络补。</li>
 * </ul>
 * 仅 1.20.1（Goety 仅此分支）；{@code MixinPlugin} 按 {@code goety} 门控。默认配置下行为不变。
 */
@Mixin(targets = "com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity", remap = false)
public abstract class CursedCageBlockEntityMixin implements SoulCageContext {

    @Unique
    private Player beyond$soulContextPlayer;

    @Override
    public void beyond$setSoulContextPlayer(Player player) {
        this.beyond$soulContextPlayer = player;
    }

    /** 该牢笼可用的灵魂网络。 */
    private DimensionsNet beyond$net() {
        var cage = (com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity) (Object) this;
        // ① 笼内灵魂符（携带 NetId 的 BD 物品）
        ItemStack item = cage.getItem();
        if ((item.getItem() instanceof NetedItem)) { // 仅便携网络终端可作灵魂符
            int netId = item.getOrDefault(com.wintercogs.beyonddimensions.common.init.BDDataComponents.NET_ID_DATA, -1);
            if (netId >= 0) {
                DimensionsNet net = DimensionsNet.getNetFromId(netId);
                if (NetworkSoulSource.usable(net)) return net;
            }
        }
        // ② 施法者上下文 → ③ 绑定玩家 的主网络（需 direct_main_net）
        Player p = this.beyond$soulContextPlayer != null ? this.beyond$soulContextPlayer : cage.getOwner();
        return NetworkSoulSource.mainNetOf(p);
    }

    @Inject(method = "getSouls", at = @At("RETURN"), cancellable = true, remap = false)
    private void beyond$addNetworkSouls(CallbackInfoReturnable<Integer> cir) {
        DimensionsNet net = beyond$net();
        if (net == null) return;
        long netSouls = SoulEnergyAccess.getSouls(net);
        if (netSouls <= 0L) return;
        long total = (long) cir.getReturnValue() + netSouls;
        SoulDebug.log("cage getSouls: native={} + net({})={} => {}",
                cir.getReturnValue(), net.getId(), netSouls, total);
        cir.setReturnValue(SoulEnergyAccess.safeLongToInt(total));
    }

    /** 该牢笼的<b>原生</b>魂量（不含网络魂，口径与原版 {@code getSouls} 一致）。 */
    @Unique
    private int beyond$nativeCageSouls() {
        var cage = (com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity) (Object) this;
        if (cage.getLevel() == null) return 0;
        Player owner = cage.getOwner();
        if (owner != null && com.Polarice3.Goety.utils.SEHelper.getSEActive(owner)) {
            int souls = com.Polarice3.Goety.utils.SEHelper.getSESouls(owner);
            if (souls > 0) return souls;
        }
        ItemStack item = cage.getItem();
        if (item != null && !item.isEmpty() && item.getItem() instanceof com.Polarice3.Goety.api.items.magic.ITotem) {
            return com.Polarice3.Goety.api.items.magic.ITotem.currentSouls(item);
        }
        return 0;
    }

    @ModifyVariable(method = "decreaseSouls", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int beyond$drainNetworkLast(int souls) {
        if (souls <= 0) return souls;
        DimensionsNet net = beyond$net();
        if (net == null) return souls;
        int nativeHave = beyond$nativeCageSouls();
        if (nativeHave >= souls) return souls; // 原生魂足够：完全不碰网络
        long netSouls = SoulEnergyAccess.getSouls(net);
        if (netSouls <= 0L) return souls;
        long got = SoulEnergyAccess.extractSouls(net, Math.min((long) souls - nativeHave, netSouls));
        if (got <= 0L) return souls;
        int remaining = (int) Math.max(0L, (long) souls - got);
        SoulDebug.log("cage decreaseSouls: net {} drained {} after native {} (req {} => native {})",
                net.getId(), got, nativeHave, souls, Math.min(remaining, nativeHave));
        // 原版先扣光其可用量，余量已由网络补
        return Math.min(remaining, nativeHave);
    }
}
