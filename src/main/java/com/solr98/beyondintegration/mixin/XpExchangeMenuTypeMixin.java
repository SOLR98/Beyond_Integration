package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.init.ModMenus;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 把 BD 经验棒菜单（{@code XpExchangeMenu}）的菜单类型替换为 BI 注册的
 * {@code ModMenus.XP_EXCHANGE}，使客户端打开 BI 复制版界面 {@code BiXpExchangeGUI}。
 * <p>两个构造（数据包/物品栈）都会经过 super 构造，统一替换。
 */
@Mixin(value = com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu.class, remap = false)
public class XpExchangeMenuTypeMixin {

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/wintercogs/beyonddimensions/common/menu/BDBaseMenu;<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;)V",
            remap = false), index = 0, remap = false)
    private MenuType<?> beyondintegration$replaceMenuType(MenuType<?> original) {
        return ModMenus.XP_EXCHANGE.get();
    }
}
