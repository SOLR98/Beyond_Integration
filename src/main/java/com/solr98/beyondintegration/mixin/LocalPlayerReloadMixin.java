package com.solr98.beyondintegration.mixin;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.gameplay.LocalPlayerReload;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerReloadGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.LogicalSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LocalPlayerReload.class, remap = false)
public class LocalPlayerReloadMixin {

    @Shadow private LocalPlayerDataHolder data;
    @Shadow private LocalPlayer player;
    @Shadow private void doReload(IGun iGun, GunDisplayInstance display, GunData gunData, ItemStack mainHandItem) {}

    @Inject(method = "reload", at = @At("HEAD"), cancellable = true)
    private void onReload(CallbackInfo ci) {
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof AbstractGunItem gunItem)) return;
        if (!gunItem.useInventoryAmmo(mainHandItem)) return;

        ResourceLocation gunId = gunItem.getGunId(mainHandItem);
        GunData gunData = TimelessAPI.getClientGunIndex(gunId).map(ClientGunIndex::getGunData).orElse(null);
        if (gunData == null) return;

        TimelessAPI.getGunDisplay(mainHandItem).ifPresent(display -> {
            if (data.clientStateLock) return;
            boolean canReload = gunItem.canReload(player, mainHandItem);
            if (IGunOperator.fromLivingEntity(player).needCheckAmmo() && !canReload) return;
            data.lockState(operator -> operator.getSynReloadState().getStateType().isReloading());
            if (MinecraftForge.EVENT_BUS.post(new GunReloadEvent(player, player.getMainHandItem(), LogicalSide.CLIENT))) return;
            NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerReloadGun());
            this.doReload(gunItem, display, gunData, mainHandItem);
        });
        ci.cancel();
    }
}
