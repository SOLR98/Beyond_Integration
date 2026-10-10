package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.soul.NetworkSoulSource;
import com.solr98.beyondintegration.feature.soul.SoulEnergyAccess;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 Goety <b>原版灵魂条 HUD</b> 显示"网络灵魂"：在 {@code SEUpdatePacket(Player)} 构造时，
 * 把玩家主网络的灵魂量并入待发送的 SE tag（并把 {@code seActive} 置真），
 * 使客户端 capability 表现为"原生 SE + 网络灵魂"，原版 HUD 自然渲染。
 * <p>服务端保留原生 SE（只改推送 tag，不污染权威值）；仅 1.20.1，{@code goety} 门控。
 */
@Mixin(targets = "com.Polarice3.Goety.common.capabilities.soulenergy.SEUpdatePacket", remap = false)
public abstract class SEUpdatePacketMixin {

    @Shadow(remap = false)
    private CompoundTag tag;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;)V", at = @At("TAIL"), remap = false)
    private void beyond$mergeNetworkSoul(Player player, CallbackInfo ci) {
        if (this.tag == null || player == null) return;
        if (!CommandConfig.soulEnabled()) return;
        DimensionsNet net = NetworkSoulSource.usableNetOf(player);
        if (net == null) return;
        long souls = SoulEnergyAccess.getSouls(net);
        if (souls <= 0L) return;

        int nativeSouls = this.tag.getInt("soulEnergy");
        this.tag.putInt("soulEnergy", SoulEnergyAccess.safeLongToInt((long) nativeSouls + souls));
        this.tag.putBoolean("seActive", true);
    }
}
