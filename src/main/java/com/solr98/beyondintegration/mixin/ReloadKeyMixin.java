package com.solr98.beyondintegration.mixin;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.ReloadKey;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 注入 TACZ 客户端换弹按键处理 ReloadKey：
 * 使"物品栏弹药"枪械也能通过 R 键手动换弹——将 useInventoryAmmo 判定改为始终 false，
 * 放行手动换弹流程（网络弹药由本模组其他逻辑补弹）。
 */
@Mixin(value = ReloadKey.class, remap = false)
public class ReloadKeyMixin {

    // 仅保留 R 键手动换弹放行（useInventoryAmmo 枪也允许手动换弹）；
    // autoReload 维持原版行为（原版对 useInventoryAmmo 枪跳过且要求弹量归零），不再注入
    /** 强制 useInventoryAmmo 返回 false，使物品栏弹药枪械不跳过 R 键换弹 */
    @Redirect(method = "onReloadPress", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IGun;useInventoryAmmo(Lnet/minecraft/world/item/ItemStack;)Z", remap = false), remap = false)
    private static boolean allowReloadPress(IGun iGun, ItemStack stack) {
        return false;
    }
}
