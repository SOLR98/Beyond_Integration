package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.BucketFluidHelper;
import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * BD 结果槽自动补料增强（取走结果）：BD 的 {@code AutoRefillResultSlot} 在合成格桶材料
 * 将耗尽时按"桶物品键"从网络补扣（保持槽位满格）；网络只有流体（+空桶）时提取为 0，
 * 槽位被清空。这里在提取不足时用网络流体替代缺失部分（宽松语义，网络有空容器时一并扣除），
 * 使 BD 认为补扣成功、合成格中的桶保留。
 * <p>
 * 覆盖 BD 工作站合成与 BD 终端合成（结果槽均为 BD AutoRefillResultSlot）。
 */
@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.widget.slot.AutoRefillResultSlot", remap = false)
public class BdBucketAutoRefillMixin {

    @Redirect(method = "onTake", at = @At(value = "INVOKE",
            target = "Lcom/wintercogs/beyonddimensions/api/storage/handler/impl/AbstractUnorderedStackHandler;extract(Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;JZZ)Lcom/wintercogs/beyonddimensions/api/storage/key/KeyAmount;"),
            remap = true, require = 0)
    private KeyAmount beyond$extractWithFluid(AbstractUnorderedStackHandler storage, IStackKey<?> key,
                                              long amount, boolean simulate, boolean fuzzy) {
        KeyAmount result = storage.extract(key, amount, simulate, fuzzy);
        try {
            if (simulate || result.amount() >= amount) return result;
            if (!(key instanceof ItemStackKey isk)) return result;
            long missing = amount - result.amount();
            long sub = BucketFluidHelper.substituteWithFluid(storage, isk.getReadOnlyStack(), missing);
            if (sub <= 0L) return result;
            return new KeyAmount(isk, result.amount() + sub);
        } catch (Throwable ignored) {
            return result;
        }
    }
}
