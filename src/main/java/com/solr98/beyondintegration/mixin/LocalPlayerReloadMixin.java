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

/**
 * 注入 TACZ 的 {@link LocalPlayerReload}（客户端玩家换弹逻辑），
 * 在 reload 头部提前执行客户端换弹流程（锁定状态、触发事件、发包并调用 doReload），
 * 以配合扩展后的 canReload —— 使维度网络弹药也能正常触发客户端换弹。
 */
@Mixin(value = LocalPlayerReload.class, remap = false)
public class LocalPlayerReloadMixin {

    /** 影射：本地玩家数据（状态锁等） */
    @Shadow private LocalPlayerDataHolder data;
    /** 影射：本地玩家 */
    @Shadow private LocalPlayer player;
    /** 影射：原类执行换弹的方法 */
    @Shadow private void doReload(IGun iGun, GunDisplayInstance display, GunData gunData, ItemStack mainHandItem) {}

    /** 在 reload 头部注入：校验换弹可行性后接管流程，锁定状态并通知服务端 */
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
