package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.GunSmithNetMode;
import com.solr98.beyondintegration.client.NetworkItemCache;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.TaczCraftPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 与 taczaddon 1.1.8.x 合成桥的兼容层（旧移植）：
 * 发包入口为 {@code requestCraft(int, ResourceLocation, boolean, int)}。
 * 1.2.0 起改为 {@code gunsmith.client.GunSmithCraftBridgeState.request(int, ResourceLocation, int)}，
 * 由 {@link TaczaddonCraftBridgeMixinNew} 处理；MixinPlugin 按目标类方法二选一。
 */
@Pseudo
@Mixin(targets = "com.mafuyu404.taczaddon.client.GunSmithCraftBridgeState", remap = false)
public abstract class TaczaddonCraftBridgeMixin {

    @Inject(method = "requestCraft", at = @At("HEAD"), cancellable = true, remap = false)
    private void beyond$redirectAddonCraft(int containerId, ResourceLocation recipeId,
                                           boolean shiftDown, int batchMax, CallbackInfo ci) {
        if (!GunSmithNetMode.isNetworkMode()) return;
        if (recipeId == null || !NetworkItemCache.hasNetwork()) return;

        int count = shiftDown ? Math.max(1, Math.min(batchMax, 64)) : 1;
        PacketHandler.sendToServer(new TaczCraftPacket(recipeId, count, GunSmithNetMode.isOutputToNetwork()));
        ci.cancel();
    }
}
