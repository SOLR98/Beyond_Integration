package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.GunSmithNetMode;
import com.solr98.beyondintegration.client.NetworkItemCache;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.TaczCraftPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 与 taczaddon 1.2.0 合成桥的兼容层（新移植）：
 * 类迁到 {@code gunsmith.client}，发包入口改为
 * {@code static request(int, ResourceLocation, int)}。
 * MixinPlugin 按目标类是否含 {@code request} 方法与旧版 Mixin 二选一。
 */
@Mixin(targets = "com.mafuyu404.taczaddon.gunsmith.client.GunSmithCraftBridgeState", remap = false)
public abstract class TaczaddonCraftBridgeMixinNew {

    @Inject(method = "request", at = @At("HEAD"), cancellable = true, remap = false)
    private static void beyond$redirectAddonCraft(int containerId, ResourceLocation recipeId,
                                                  int requested, CallbackInfo ci) {
        if (!GunSmithNetMode.isNetworkMode()) return;
        if (recipeId == null || !NetworkItemCache.hasNetwork()) return;

        int count = Math.max(1, Math.min(requested, 64));
        PacketHandler.sendToServer(new TaczCraftPacket(recipeId, count, GunSmithNetMode.isOutputToNetwork()));
        ci.cancel();
    }
}
