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
 * <p>
 * 注入说明：{@code onTake} 是 BD 类覆写原版 {@code ResultSlot.onTake} 的方法，
 * 1.20.1 生产环境方法名为 SRG 名 {@code m_142406_}，而 Mixin refmap 不会为 mod 类生成映射，
 * 故同时注册 MCP 名（开发环境）与 SRG 名（生产环境）两组注入，各环境只有一个命中。
 */
@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.common.menu.widget.slot.AutoRefillResultSlot", remap = false)
public class BdBucketAutoRefillMixin {

    // ── 开发环境（MCP 名 onTake） ──
    @Redirect(method = "onTake", at = @At(value = "INVOKE",
            target = "Lcom/wintercogs/beyonddimensions/api/storage/handler/impl/AbstractUnorderedStackHandler;extract(Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;JZZ)Lcom/wintercogs/beyonddimensions/api/storage/key/KeyAmount;"),
            remap = false, require = 0)
    private KeyAmount beyond$extractWithFluidDev(AbstractUnorderedStackHandler storage, IStackKey<?> key,
                                                 long amount, boolean simulate, boolean fuzzy) {
        return beyond$extractWithFluid(storage, key, amount, simulate, fuzzy);
    }

    // ── 生产环境（SRG 名 m_142406_） ──
    @Redirect(method = "m_142406_", at = @At(value = "INVOKE",
            target = "Lcom/wintercogs/beyonddimensions/api/storage/handler/impl/AbstractUnorderedStackHandler;extract(Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;JZZ)Lcom/wintercogs/beyonddimensions/api/storage/key/KeyAmount;"),
            remap = false, require = 0)
    private KeyAmount beyond$extractWithFluidProd(AbstractUnorderedStackHandler storage, IStackKey<?> key,
                                                  long amount, boolean simulate, boolean fuzzy) {
        return beyond$extractWithFluid(storage, key, amount, simulate, fuzzy);
    }

    /**
     * 桶物品提取不足时用网络流体替代缺失部分（宽松语义）：BD 认为补扣成功，
     * 合成格桶保留（合成实际消耗网络流体，而不是网络桶物品）。
     */
    @Unique
    private KeyAmount beyond$extractWithFluid(IStackHandler storage, IStackKey<?> key,
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
