package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB 检测递归保护（NeoForge）：FTB 每完成一个任务会在 {@code markTaskCompleted} 中递归调用
 * {@code detect}（检查后续任务）。大任务书（数百~数千个物品任务）重置后一次检测可能链式完成
 * 大量任务，递归深度足以触发 {@code StackOverflowError}；BI 注入的网络物品恒存在还会放大链式规模。
 * <p>
 * 这里限制 detect 递归深度：超限时取消本次检测并调度 FTB 延迟检测
 * （剩余任务在后续 tick 分批完成），避免栈溢出。
 * <p>
 * FTB Quests 未安装（@Pseudo）/RI 加载（MixinPlugin 让路）/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.util.FTBQuestsInventoryListener", remap = false)
public class FtbDetectDepthGuardMixin {

    /** 进入检测（超限则取消并调度延迟检测） */
    @Inject(method = "detect", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void beyond$guardDetectDepth(ServerPlayer player, ItemStack craftedItem, long sourceTask,
                                                CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        if (!FtbIntegrationHelper.enterFtbDetect()) {
            FtbIntegrationHelper.scheduleDeferredDetect(player);
            ci.cancel();
        }
    }

    /** 检测返回时递减递归深度（被取消的调用不会进入） */
    @Inject(method = "detect", at = @At("RETURN"), remap = false, require = 0)
    private static void beyond$popDetectDepth(ServerPlayer player, ItemStack craftedItem, long sourceTask,
                                              CallbackInfo ci) {
        FtbIntegrationHelper.exitFtbDetect();
    }
}
