package com.solr98.beyondintegration.mixin;

import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.common.item.XpExchangeItem;
import com.wintercogs.beyonddimensions.common.item.XpExchangeSettings;
import com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 经验棒：在 {@code XpExchangeItem.use} 头部整段接管（HEAD + cancel，等价替换实现），
 * 改为打开 BI 复制的经验棒菜单（{@code ModMenus.XP_EXCHANGE} → {@code BiXpExchangeGUI}）。
 * 同时复刻原 {@code super(NetedItem).use} 的副作用（主手 + Shift 绑定/解绑网络）。
 * <p>与网络磁铁 GUI 替换方式一致：使用 BI 自有菜单类型，不覆盖 BD 的菜单屏幕注册。
 */
@Mixin(value = XpExchangeItem.class, remap = false)
public class XpExchangeItemOpenMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, remap = true)
    private void beyond$openBiMenu(Level level, Player player, InteractionHand usedHand,
                                   CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack itemstack = player.getItemInHand(usedHand);
        // 复刻 super(NetedItem).use 的副作用：主手 + Shift 时绑定/解绑网络（其返回值被原实现忽略）
        if (usedHand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && !level.isClientSide()) {
            NetedItem.setNet(itemstack, player);
        }
        if (usedHand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            cir.setReturnValue(InteractionResultHolder.fail(itemstack));
            return;
        }

        XpExchangeSettings.ensureComponents(itemstack);
        if (!level.isClientSide()) {
            NetworkHooks.openScreen((ServerPlayer) player, new SimpleMenuProvider(
                            (containerId, inv, serverPlayer) -> new XpExchangeMenu(containerId, inv, itemstack),
                            Component.translatable("menu.title.beyonddimensions.xp_exchange_menu")),
                    buf -> buf.writeEnum(usedHand));
        }
        cir.setReturnValue(InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide()));
    }
}
