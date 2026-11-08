package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.feeder.ThirstBridge;
import com.solr98.beyondintegration.handler.BucketFluidHelper;
import com.wintercogs.beyonddimensions.api.capability.helper.wrapper.IStackHandlerWrapper;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.menu.interaction.SlotClick;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * BD 0.8 原生「流体→容器」装填的纯度补写。
 * <p>BD 0.8 把终端槽位点击重构到 {@code BuiltinSlotInteractions}：
 * <ul>
 *   <li>{@code fillRound}：手持空桶/空瓶左键装填（产物经 {@code handler.getContainer()} 得到）；</li>
 *   <li>{@code fillBucketInto}：Shift 转移时把流体装成桶放进目标槽。</li>
 * </ul>
 * 两者生成容器时只按流体类型出物品，不带 Thirst 纯度组件 → 读取时被当作「无组件 = 纯净」。
 * 这里在这两处产物上按 {@link FluidStackKey} 对应的 BI 档位补写纯度，保证把 dirty/acceptable
 * 水装出来时仍是对应档次（旧 {@code BdBucketFluidSlotMixin} 注入的 {@code click} 在 BD 0.8 已不存在）。
 */
@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.interaction.BuiltinSlotInteractions", remap = false)
public abstract class BdBucketFillPurityMixin {

    /** 左键装填：容器由 getContainer() 生成，补写纯度后再返回。 */
    @Redirect(
            method = "fillRound",
            at = @At(value = "INVOKE",
                    target = "Lcom/wintercogs/beyonddimensions/api/capability/helper/wrapper/IStackHandlerWrapper;getContainer()Ljava/util/Optional;"),
            remap = false,
            require = 1)
    private static Optional<ItemStack> beyond$tagFilledContainer(IStackHandlerWrapper<Object> handler,
                                                                 SlotClick c, ItemStack single,
                                                                 IStackKey<?> key, long amount) {
        Optional<ItemStack> original = handler.getContainer();
        if (original.isEmpty() || !(key instanceof FluidStackKey fluid)) return original;
        beyond$tagContainer(original.get(), fluid);
        return original;
    }

    /** Shift 转移装桶：产物已插入目标槽，直接对槽内该桶补写纯度。 */
    @Inject(method = "fillBucketInto", at = @At("RETURN"), remap = false, require = 1)
    private static void beyond$tagInsertedBucket(SlotClick c, FluidStackKey fluid, Slot target,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        ItemStack inSlot = target.getItem();
        if (inSlot.isEmpty() || inSlot.getItem() != fluid.getSource().getBucket()) return;
        beyond$tagContainer(inSlot, fluid);
        target.setChanged();
    }

    /** 按流体键对应的 BI 档位给容器补写纯度（未知纯度不写，避免被 Thirst 当作纯净）。 */
    private static void beyond$tagContainer(ItemStack stack, FluidStackKey fluid) {
        if (stack == null || stack.isEmpty()) return;
        if (!BucketFluidHelper.isWaterFluid(fluid)) return;
        int purity = BucketFluidHelper.waterPurity(fluid);
        if (purity < 0) return;
        ThirstBridge.get().tagPurity(stack, purity);
    }
}

