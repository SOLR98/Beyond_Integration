package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.BucketFluidHelper;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * BD 终端槽位增强：左键点击流体键槽位时，经 {@link BucketFluidHelper} 从网络取出流体与空容器，
 * 组合成装满的桶（或水瓶）放到光标上。
 * <p>水类流体（本模组 4 档水 / 原版水）会在取出时打上对应的 Thirst 水纯度标签；
 * 手持玻璃瓶（单瓶）时改为产出"水"药水瓶。资源不足、流体无对应容器、光标不兼容或槽位不允许拾取时保持原逻辑。
 * <p>菜单引用经反射访问父类 {@code AbstractStackTypedSlot.menu}（Mixin 0.8.5 的 @Shadow
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
            boolean water = BucketFluidHelper.isWaterFluid(fluidKey);
            // 手持单个玻璃瓶 → 产出水瓶
            boolean bottle = water && !carried.isEmpty() && carried.is(Items.GLASS_BOTTLE)
                    && carried.getCount() == 1;
            if (water && !carried.isEmpty() && !bottle && !carried.is(Items.WATER_BUCKET)) return;

            Item targetItem = bottle ? Items.POTION
                    : (water ? Items.WATER_BUCKET : BucketFluidHelper.bucketItemOf(fluidKey));
            boolean stackable = !carried.isEmpty() && carried.is(targetItem)
                    && carried.getCount() < carried.getMaxStackSize();
            if (!bottle && !carried.isEmpty() && !stackable) return;

            if (!self.mayPickup(player)) return;

            ItemStack filled = bottle
                    ? BucketFluidHelper.fillWaterBottles(self.getStorage(), fluidKey, 1)
                    : BucketFluidHelper.fillBuckets(self.getStorage(), fluidKey, 1);
            if (filled.isEmpty()) return;

            if (bottle) {
                carried.shrink(1);
                menu.setCarried(filled);
            } else if (carried.isEmpty()) {
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
