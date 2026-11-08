package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.compat.tacz.TaczAmmoShooterContext;
import com.solr98.beyondintegration.compat.tacz.TaczDummyAmmoCompat;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.solr98.beyondintegration.feature.sentry.SentryFakePlayerNetMarker;
import com.tacz.guns.api.item.IGun;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入目标：TacZ 的 {@code LivingEntityShoot}（服务端射击/消耗备弹逻辑）。
 * <p>作用：兼容 <b>TACZ 1.2.0 的「虚拟备弹 / 背包弹容」</b>（{@code useDummyAmmo} + {@code findAndExtractDummyAmmo}）。
 * 1.2.0 的 {@code consumeAmmoFromPlayer} 在虚拟备弹枪上只扣枪 NBT 的 {@code DummyAmmo}，
 * 不再走背包 {@code findAndExtractInventoryAmmo}；本 Mixin 在扣弹前后比较虚拟备弹余量，
 * 把不足部分从玩家/女仆/哨戒臂的维度网络补扣，使网络供弹在 1.2.0 下继续生效。
 * <p>{@code useDummyAmmo}/{@code getDummyAmmoAmount} 是 1.2.0 新增 API，1.1.x 编译期不存在，故用反射。
 */
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityShoot", remap = false)
public abstract class LivingEntityShootMixin {

    @Shadow(remap = false)
    private LivingEntity shooter;

    /** 扣弹前的虚拟备弹余量（-1 = 本次不处理）。 */
    @Unique
    private int beyond$dummyBefore = -1;



    @Inject(method = "consumeAmmoFromPlayer", at = @At("HEAD"), require = 0)
    private void beyond$onConsumeHead(int neededAmount, ItemStack itemStack, boolean needCheckAmmo, CallbackInfo ci) {
        TaczAmmoShooterContext.set(this.shooter);
        beyond$dummyBefore = -1;
        if (!needCheckAmmo || itemStack == null || itemStack.isEmpty()) return;
        IGun iGun = IGun.getIGunOrNull(itemStack);
        if (iGun == null || !TaczDummyAmmoCompat.useDummyAmmo(iGun, itemStack)) return;
        beyond$dummyBefore = TaczDummyAmmoCompat.dummyAmmoAmount(iGun, itemStack);
    }

    @Inject(method = "consumeAmmoFromPlayer", at = @At("RETURN"), require = 0)
    private void beyond$onConsumeReturn(int neededAmount, ItemStack itemStack, boolean needCheckAmmo, CallbackInfo ci) {
        int before = beyond$dummyBefore;
        beyond$dummyBefore = -1;
        if (before < 0 || itemStack == null || itemStack.isEmpty()) return;
        IGun iGun = IGun.getIGunOrNull(itemStack);
        if (iGun == null) return;
        int after = TaczDummyAmmoCompat.dummyAmmoAmount(iGun, itemStack);
        if (after < 0) return;
        int extracted = Math.max(0, before - after);
        int stillNeed = neededAmount - extracted;
        if (stillNeed <= 0) return;

        LivingEntity target = this.shooter;
        if (target == null) return;

        if (target instanceof FakePlayer fakePlayer) {
            if (SentryFakePlayerNetMarker.isMarked(fakePlayer)) {
                DimensionsNet net = SentryFakePlayerNetMarker.getNet(fakePlayer);
                if (net != null) {
                    TaczAmmoExtractor.consumeAmmoDirectly(itemStack, stillNeed, net);
                }
            }
            return;
        }
        if (target instanceof ServerPlayer serverPlayer) {
            TaczAmmoExtractor.tryConsumeFromAll(serverPlayer, itemStack, stillNeed);
            return;
        }
        if (target.level().isClientSide()) {
            TaczAmmoShooterContext.clear();
            return;
        }
        TaczAmmoExtractor.tryConsumeFromMaid(target, itemStack, stillNeed);
        TaczAmmoShooterContext.clear();
    }
}
