package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.common.menu.MagnetMenu;
import com.wintercogs.beyonddimensions.common.item.NetMagnetItem;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 网络磁铁（1.21.1）：整段替换 BD {@code NetMagnetItem.use}（替换原先对 openMenu 的 @Redirect），
 * 改为直接打开 BI 复制的 {@link MagnetMenu}（对应 BI 的 {@code MagnetGUI}）。
 * 同时复刻原 {@code super(NetedItem).use} 的副作用（主手 + Shift 绑定/解绑网络）。
 */
@Mixin(value = NetMagnetItem.class, remap = false)
public class NetMagnetItemOpenMixin {

    @Overwrite(remap = false)
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemstack = player.getItemInHand(usedHand);
        // 复刻 super(NetedItem).use 的副作用：主手 + Shift 时绑定/解绑网络（其返回值被原实现忽略）
        if (usedHand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && !level.isClientSide()) {
            NetedItem.setNet(itemstack, player);
        }
        if (usedHand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResultHolder.fail(itemstack);
        }
        if (!level.isClientSide()) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new MagnetMenu(id, inv, itemstack),
                    Component.translatable("menu.title.beyonddimensions.magnet_menu")));
        }
        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }
}
