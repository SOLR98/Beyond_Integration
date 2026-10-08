package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.common.menu.MagnetMenu;
import com.wintercogs.beyonddimensions.common.item.NetMagnetItem;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
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
 * 网络磁铁：在 {@code NetMagnetItem.use} 头部整段接管（HEAD + cancel，等价替换实现），
 * 改为直接打开 BI 复制的 {@link MagnetMenu}（对应 BI 的 {@code MagnetGUI}）。
 * 同时复刻原 {@code super(NetedItem).use} 的副作用（主手 + Shift 绑定/解绑网络）。
 * <p>
 * {@code remap = true}：1.20.1 运行时为 SRG，由 refmap 把 {@code use} 映射到 {@code m_7203_}。
 */
@Mixin(value = NetMagnetItem.class, remap = false)
public class NetMagnetItemOpenMixin {

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
        if (!level.isClientSide()) {
            NetworkHooks.openScreen((ServerPlayer) player, new SimpleMenuProvider(
                    (id, inv, p) -> new MagnetMenu(id, inv, itemstack),
                    Component.translatable("menu.title.beyonddimensions.magnet_menu")));
        }
        cir.setReturnValue(InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide()));
    }
}
