package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import com.solr98.beyondintegration.feature.ftb.FtbTaskNetworkScanner;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FTB Quests 集成（1.20.1 实际依赖）：把 BD 网络物品并入 FTB 的背包汇总缓存。
 * <p>
 * 1.20.1 版 FTB 由 {@code FTBQuestsInventoryListener.buildInventorySummary} 构建汇总
 * （{@code inventorySummaryCache} + {@code nonEmptyStacks}，detect 扫描结束后清空），
 * 非消耗型物品任务经 {@code getStacksForPlayerOfType / getAllNonEmptyStacksForPlayer}
 * 读取该汇总。这里在汇总构建完成后追加主网络物品（含已有存量），使检测类任务计入网络库存。
 * <p>
 * <b>递归保护</b>：FTB 每完成一个任务会在 {@code markTaskCompleted} 中递归调用 {@code detect}
 * （检查后续任务）。大任务书（数百~数千个物品任务）重置后一次检测可能链式完成大量任务，
 * 递归深度足以触发 {@code StackOverflowError}；网络物品恒存在还会放大链式规模。
 * 这里限制 detect 递归深度（超限取消并调度 FTB 延迟检测，剩余任务后续 tick 分批完成），
 * 且每次最外层检测仅注入一次网络物品（防止递归中汇总缓存重复累加）。
 * <p>
 * FTB Quests 未安装（@Pseudo）/RI 加载（MixinPlugin 让路）/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.util.FTBQuestsInventoryListener", remap = false)
public class FtbInventorySummaryMixin {

    @Shadow(remap = false) private static Map<Item, List<ItemStack>> inventorySummaryCache;
    @Shadow(remap = false) private static List<ItemStack> nonEmptyStacks;

    /** 检测递归保护：进入检测（超限则取消并调度延迟检测，避免链式完成导致栈溢出） */
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

    /** 背包汇总完成后追加网络物品（含存量），使检测类物品任务计入网络库存（每次最外层检测仅注入一次） */
    @Inject(method = "buildInventorySummary", at = @At("RETURN"), remap = false, require = 0)
    private static void beyond$includeNetworkItems(ServerPlayer player, CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        if (!FtbIntegrationHelper.markNetworkItemsInjected()) return;
        try {
            // 按任务需求精准收集网络物品（仅保留任务目标匹配的键）
            for (KeyAmount ka : FtbTaskNetworkScanner.collect(player)) {
                if (!(ka.key() instanceof ItemStackKey ik)) continue;
                ItemStack stack = ik.getReadOnlyStack();
                if (stack.isEmpty()) continue;
                ItemStack copy = stack.copyWithCount((int) Math.min(ka.amount(), Integer.MAX_VALUE));
                inventorySummaryCache.computeIfAbsent(copy.getItem(), k -> new ArrayList<>()).add(copy);
                nonEmptyStacks.add(copy);
            }
        } catch (Throwable ignored) {}
    }
}
