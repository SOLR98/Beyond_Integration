package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.GunSmithNetMode;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.TaczCraftPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 与 taczaddon 合成桥的兼容层（MixinPlugin 按 taczaddon 版本门控）：
 *
 * taczaddon 通过 @ModifyArg 替换 TACZ 合成按钮的 OnPress（改走 GunSmithCraftRequestPacket），
 * 与本模组「网络材料模式」的合成分支竞争；addon 的包装不调用原 OnPress，
 * 谁成为外层取决于 Mixin 应用顺序，网络模式可能被完全吞掉（表现为点击无反应）。
 * 这里注入 addon 点击流程的唯一发包入口 {@code GunSmithCraftBridgeState.requestCraft}
 * （HEAD cancellable），网络模式下改发本模组 TaczCraftPacket 并取消 addon 发包 —— 与实际包装顺序无关。
 */
@Mixin(targets = "com.mafuyu404.taczaddon.client.GunSmithCraftBridgeState", remap = false)
public abstract class TaczaddonCraftBridgeMixin {

    @Inject(method = "requestCraft", at = @At("HEAD"), cancellable = true, remap = false)
    private void beyond$redirectAddonCraft(int containerId, ResourceLocation recipeId,
                                           boolean shiftDown, int batchMax, CallbackInfo ci) {
        if (!GunSmithNetMode.isNetworkMode()) return;
        if (recipeId == null || !GunSmithNetMode.hasNetwork()) return;

        int count = shiftDown ? Math.max(1, Math.min(batchMax, 64)) : 1;
        PacketHandler.sendToServer(new TaczCraftPacket(recipeId, count, GunSmithNetMode.isOutputToNetwork()));
        ci.cancel();
    }
}
