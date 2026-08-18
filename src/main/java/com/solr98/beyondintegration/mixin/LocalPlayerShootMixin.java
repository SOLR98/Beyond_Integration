package com.solr98.beyondintegration.mixin;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 注入 TACZ 的客户端射击逻辑 LocalPlayerShoot：
 * 修复使用"物品栏弹药"(useInventoryAmmo)的枪械在 doShoot 时弹药数为 0
 * 导致无法开火的问题——若枪械走物品栏弹药，将弹药数修正为无限大，交由后续逻辑从物品栏扣除。
 */
@Mixin(value = LocalPlayerShoot.class, remap = false)
public class LocalPlayerShootMixin {

    /** 本模组对应玩家实体 */
    @Shadow
    private LocalPlayer player;

    /** 改写 doShoot 中 Math.min 的第一个参数（当前弹匣量），绕过弹药为 0 的拦截 */
    @ModifyArg(method = "doShoot", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", remap = false), index = 0)
    private int fixAmmoCount(int ammoCount) {
        if (ammoCount > 0) return ammoCount;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun)) return ammoCount;
        if (iGun.useInventoryAmmo(stack)) {
            return Integer.MAX_VALUE;
        }
        return ammoCount;
    }
}
