package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.NetworkItemCache;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.TaczCraftPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 与 taczaddon 合成桥的兼容层（仅 1.1.8.1，MixinPlugin 门控）：
 *
 * taczaddon 通过 @ModifyArg 替换 TACZ 合成按钮的 OnPress（发 GunSmithCraftRequestPacket），
 * 导致本模组"网络材料模式"的合成分支被 addon 流程绕过（点击只走 taczaddon 路径）。
 * 这里注入 addon 点击流程的唯一发包入口 GunSmithCraftBridgeState.requestCraft（HEAD cancellable），
 * 网络模式下改发本模组 TaczCraftPacket 并取消 addon 发包——顺序无关，双模组共存正确。
 */
@Mixin(targets = "com.mafuyu404.taczaddon.client.GunSmithCraftBridgeState", remap = false)
public abstract class TaczaddonCraftBridgeMixin {

    @Inject(method = "requestCraft", at = @At("HEAD"), cancellable = true, remap = false)
    private void beyond$redirectAddonCraft(int containerId, ResourceLocation recipeId,
                                           boolean shiftDown, int batchMax, CallbackInfo ci) {
        if (!com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode()) return;
        if (recipeId == null || !NetworkItemCache.hasNetwork()) return;

        int count = shiftDown ? 64 : 1;
        PacketHandler.sendToServer(new TaczCraftPacket(recipeId, count,
                com.solr98.beyondintegration.client.GunSmithNetMode.isOutputToNetwork()));
        ci.cancel();
    }
}
