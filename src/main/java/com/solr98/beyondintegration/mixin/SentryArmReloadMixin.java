package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入哨戒机械臂的 SentryArmBlockEntity：
 * 扩展 performInstantReload——哨戒发射弹药后，自动从绑定的维度网络直接补充弹匣
 * （支持无限弹药与按弹药类型计数），实现哨戒的"网络供弹"。
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryArmReloadMixin {

    /** 拦截哨戒瞬时换弹：从网络弹药库扣取并填入枪械弹匣，成功后跳过原逻辑 */
    @Inject(method = "performInstantReload", at = @At("HEAD"), cancellable = true)
    private void onReload(net.minecraftforge.common.util.FakePlayer fakePlayer,
                           com.tacz.guns.api.item.IGun iGun,
                           net.minecraft.world.item.ItemStack gunStack,
                           CallbackInfoReturnable<Boolean> cir) {
        try {
            if (iGun.useInventoryAmmo(gunStack)) return;
            DimensionsNet net = getTerminalNetwork();
            if (net == null) return;

            var gunIndexOpt = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack));
            if (gunIndexOpt.isEmpty()) return;

            int maxAmmo = gunIndexOpt.get().getGunData().getAmmoAmount();
            int currentAmmo = iGun.getCurrentAmmoCount(gunStack);
            int need = maxAmmo - currentAmmo;
            if (need <= 0) return;

            int networkCount = TaczAmmoExtractor.countAmmoInNetwork(gunStack, net);
            if (networkCount == Integer.MAX_VALUE) need = Math.min(need, 9000);
            else if (networkCount > 0) need = Math.min(need, Math.min(networkCount, 9000));
            else return;

            int fromNet = TaczAmmoExtractor.consumeAmmoDirectly(gunStack, need, net);
            if (fromNet <= 0) return;

            iGun.setCurrentAmmoCount(gunStack, currentAmmo + fromNet);
            Bolt bolt = gunIndexOpt.get().getGunData().getBolt();
            if (bolt != Bolt.OPEN_BOLT && !iGun.hasBulletInBarrel(gunStack) && iGun.getCurrentAmmoCount(gunStack) > 0) {
                iGun.reduceCurrentAmmoCount(gunStack);
                iGun.setBulletInBarrel(gunStack, true);
            }
            cir.setReturnValue(true);
        } catch (Exception ignored) {}
    }

    /** 获取哨戒绑定的维度网络；仅在服务端且已绑定时返回，否则为 null */
    @Unique
    private DimensionsNet getTerminalNetwork() {
        BlockEntity be = (BlockEntity) (Object) this;
        if (be.getLevel() == null || be.getLevel().isClientSide) return null;
        if (!(be instanceof SentryNetIdAccessor accessor)) return null;
        int netId = accessor.getSentryNetId();
        if (netId < 0) return null;
        return DimensionsNet.getNetFromId(netId);
    }
}
