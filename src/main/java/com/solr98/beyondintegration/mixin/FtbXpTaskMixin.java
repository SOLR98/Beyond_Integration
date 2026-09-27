package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.XPTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Quests 集成：XP 任务提交时从 BD 主网络消耗经验补给玩家后由原版逻辑提交。
 * <p>
 * 在 {@code XPTask.submitTask} HEAD 计算玩家经验缺口（等级模式补到剩余需求等级、
 * 点数模式补足剩余点数），从主网络提取 XP 流体（1 点 = 20 mB）并直接发放给玩家；
 * 随后 FTB 原生逻辑扣除玩家经验并计入进度（网络经验不足时按实际补充量部分提交）。
 * <p>
 * FTB Quests 未安装（@Pseudo）/RI 加载（MixinPlugin 让路）/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.task.XPTask", remap = false)
public class FtbXpTaskMixin {

    /** 任务是否为经验点模式（false = 等级模式） */
    @Shadow(remap = false) private boolean points;

    @Inject(method = "submitTask", at = @At("HEAD"), remap = false, require = 0)
    private void beyond$supplyXpFromNetwork(TeamData teamData, ServerPlayer player, ItemStack craftedItem, CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        try {
            XPTask self = (XPTask) (Object) this;
            if (teamData.isCompleted(self)) return;
            long need = self.getMaxProgress() - teamData.getProgress(self);
            if (need <= 0) return;

            long missing;
            if (points) {
                // 点数模式：补足剩余点数
                missing = need - XPTask.getPlayerXP(player);
            } else {
                // 等级模式：补到剩余需求等级（换算为总经验点）
                if (need > Integer.MAX_VALUE) return;
                missing = (long) XPTask.getExperienceForLevel((int) need) - XPTask.getPlayerXP(player);
            }
            if (missing <= 0) return;

            long got = FtbIntegrationHelper.extractXpFluid(player, missing);
            if (got > 0) {
                XPTask.addPlayerXP(player, (int) Math.min(got, Integer.MAX_VALUE));
            }
            // 不取消：由 FTB 原生逻辑扣除玩家经验并计入进度
        } catch (Throwable ignored) {}
    }
}
