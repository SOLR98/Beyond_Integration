package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.BucketFluidHelper;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * BD 终端槽位增强：空手（或手持同种未满桶）左键点击流体键槽位时，
 * 经 {@link BucketFluidHelper} 从网络取出流体与空桶，组合成装满的桶放到光标上
 * （与物品槽左键取出行为一致）。
 * <p>
 * 仅处理流体键 + 左键（BD 原生流体槽左键不处理，接管无副作用；手持空桶右键的原生装填保持不变）；
 * 资源不足、该流体无对应桶物品、光标物品不兼容或槽位不允许拾取时保持原逻辑。
 * <p>
 * 菜单引用经反射访问父类 {@code AbstractStackTypedSlot.menu}（Mixin 0.8.5 的 @Shadow
 * 无法定位继承字段，实测会导致整个 mixin 应用失败），字段句柄首次调用时解析并缓存。
 */
@Pseudo
@Mixin(targets = {
        "com.wintercogs.beyonddimensions.common.menu.widget.slot.DisorderedStackTypedSlot",
        "com.wintercogs.beyonddimensions.common.menu.widget.slot.OrderedStackTypedSlot"
}, remap = false)
public class BdBucketFluidSlotMixin {

    /** 反射缓存的父类 menu 字段（@Shadow 不支持继承字段） */
    @Unique private static Field beyond$menuField;

    @Inject(method = "click", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void beyond$autoFillBucket(KeyAmount clickStack, int button, Player player, CallbackInfo ci) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        try {
            if (!(clickStack.key() instanceof FluidStackKey fluidKey)) return;
            if (!BucketFluidHelper.hasBucketItem(fluidKey)) return;

            AbstractStackTypedSlot self = (AbstractStackTypedSlot) (Object) this;
            BDBaseMenu menu = beyond$menu(self);
            if (menu == null) return;

            ItemStack carried = menu.getCarried();
            Item bucketItem = fluidKey.getSource().getBucket();
            boolean stackable = !carried.isEmpty() && carried.is(bucketItem)
                    && carried.getCount() < carried.getMaxStackSize();
            if (!carried.isEmpty() && !stackable) return;

            if (!self.mayPickup(player)) return;

            ItemStack filled = BucketFluidHelper.fillBuckets(self.getStorage(), fluidKey, 1);
            if (filled.isEmpty()) return;

            if (carried.isEmpty()) {
                menu.setCarried(filled);
            } else {
                carried.grow(filled.getCount());
            }
            ci.cancel();
        } catch (Throwable ignored) {}
    }

    /** 反射获取父类 menu 字段（首次解析后缓存） */
    @Unique
    private static BDBaseMenu beyond$menu(AbstractStackTypedSlot slot) {
        try {
            if (beyond$menuField == null) {
                beyond$menuField = AbstractStackTypedSlot.class.getDeclaredField("menu");
                beyond$menuField.setAccessible(true);
            }
            return (BDBaseMenu) beyond$menuField.get(slot);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
