package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.NetIdAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu", remap = false)
public class DimensionsNetMenuMixin {
    @Shadow(remap = false) public AbstractUnorderedStackHandler storage;

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lcom/wintercogs/beyonddimensions/api/storage/handler/impl/AbstractUnorderedStackHandler;)V",
            at = @At("RETURN"), remap = false)
    private void onServerInit(MenuType<?> menuType, int id, Inventory inv, AbstractUnorderedStackHandler data, CallbackInfo ci) {
        if (data instanceof UnifiedStorage us) {
            if (us instanceof UnifiedStorageAccessor acc) {
                var net = acc.beyond$getNet();
                if (net != null)
                    ((NetIdAccessor) this).beyond$setNetId(net.getId());
            }
        }
    }
}
