package com.solr98.beyondintegration.mixin;

import com.Polarice3.Goety.common.ritual.RitualChecker;
import com.solr98.beyondintegration.handler.RitualNetworkStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * 仪式结构方块检测的「网络兜底」。
 * <p>
 * <b>需求来源</b>：Goety 没有公开的「仪式方块需求清单」，需求硬编码在
 * {@code RitualRequirements.getStructures} 的 switch 里。但每次检查都会调用
 * {@code RitualChecker.hasBlocks(predicate, totalCount)} —— 这两个入参就是 Goety 声明的
 * 「这一类方块要多少个」，因此这里直接以它们为需求来源，不自行推断。
 * <p>
 * <b>已有数量</b>：{@code checkBlocks} 内部的计数是局部变量，通过 {@link Redirect}
 * 统计 {@code isCorrectBlock} 的命中次数拿到（与 Goety 同口径，且不重复扫世界）。
 * <p>
 * <b>清单完整性</b>：原版遇首个失败即 {@code return}（短路），拿不到后续条件。故网络补不上时
 * 先把缺口登记进清单、再「假装满足」让流程继续，最后由
 * {@code RitualRequirementsMixin} 在 {@code getStructures} 收尾时统一判失败并输出完整清单。
 * 功能未启用时不登记、也不改返回值，完全保持原版行为。
 * <p>仅 1.20.1，{@code MixinPlugin} 按 {@code goety} 门控。
 */
@Mixin(targets = "com.Polarice3.Goety.common.ritual.RitualChecker", remap = false)
public abstract class RitualCheckerMixin {

    /** 当前这一轮 {@code checkBlocks} 命中的方块数（由 {@link #beyond$countMatched} 累计）。 */
    @Unique
    private int beyond$found;

    /** 每轮计数前清零。 */
    @Inject(method = "checkBlocks", at = @At("HEAD"), remap = false)
    private void beyond$resetFound(CallbackInfoReturnable<Boolean> cir) {
        this.beyond$found = 0;
    }

    /** 统计命中数：保留原始判定，仅旁路计数（匿名子类重写 {@code isCorrectBlock} 时同样生效）。 */
    @Redirect(method = "checkBlocks", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lcom/Polarice3/Goety/common/ritual/RitualChecker;"
                            + "isCorrectBlock(Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/core/BlockPos;)Z"))
    private boolean beyond$countMatched(RitualChecker self, BlockState state, BlockPos pos) {
        boolean ok = self.isCorrectBlock(state, pos);
        if (ok) this.beyond$found++;
        return ok;
    }

    @Inject(method = "hasBlocks", at = @At("RETURN"), cancellable = true, remap = false)
    private void beyond$networkStructureBlocks(Predicate<BlockState> predicate, int totalCount,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (totalCount <= 0) return;

        RitualChecker self = (RitualChecker) (Object) this;
        int found = Math.max(0, this.beyond$found);
        int missing = totalCount - found;
        if (missing <= 0) return;

        if (RitualNetworkStructure.hasBlocks(self.level, self.initialPos, predicate, missing)) {
            cir.setReturnValue(true);
            return;
        }

        // 网络也补不上：登记缺口并让流程继续，以便收集完整清单（收尾时统一判失败）
        if (RitualNetworkStructure.recordMissing(predicate, totalCount, found)) {
            cir.setReturnValue(true);
        }
    }
}
