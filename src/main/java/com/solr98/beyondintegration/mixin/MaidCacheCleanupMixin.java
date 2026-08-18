package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.maid.MaidNetworkCache;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入东方小女仆模组(TLM)的 EntityMaid：
 * 修复女仆实体移除时 MaidNetworkCache（女仆网络弹药缓存）残留导致的内存泄漏，
 * 在 remove 时按女仆 UUID 清除对应缓存。
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid", remap = false)
public class MaidCacheCleanupMixin {

    /** 女仆被移除（死亡/卸载）时清理其网络缓存 */
    @Inject(method = "remove", at = @At("HEAD"))
    private void beyond$onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        MaidNetworkCache.remove(((Entity) (Object) this).getUUID());
    }
}
