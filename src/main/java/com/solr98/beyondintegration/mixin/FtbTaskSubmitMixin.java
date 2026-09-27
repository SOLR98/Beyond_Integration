package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.EnergyTask;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Quests 集成：流体 / 能量任务点击提交时从 BD 主网络提交。
 * <p>
 * FTB 原生这两类任务没有 submitTask 实现（原版只能通过任务屏输入），
 * 这里在 Task 基类提交入口接管：按任务剩余需求从主网络直接扣除流体
 * （fluid + components 精确匹配）或 FE 能量，并计入进度；网络不足时按实际扣除量部分提交。
 * <p>
 * FTB Quests 未安装（@Pseudo）/RI 加载（MixinPlugin 让路）/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.task.Task", remap = false)
public abstract class FtbTaskSubmitMixin {

    /** 任务顺序模式检查（Task 基类 protected final） */
    @Shadow(remap = false)
    protected abstract boolean checkTaskSequence(TeamData teamData);

    @Inject(method = "submitTask", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void beyond$submitFluidOrEnergyFromNetwork(TeamData teamData, ServerPlayer player, ItemStack craftedItem, CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        try {
            Task self = (Task) (Object) this;
            if (self instanceof FluidTask fluidTask) {
                if (beyond$submitFluid(teamData, player, fluidTask)) ci.cancel();
            } else if (self instanceof EnergyTask energyTask) {
                if (beyond$submitEnergy(teamData, player, energyTask)) ci.cancel();
            }
        } catch (Throwable ignored) {}
    }

    /** 流体任务：从主网络扣除匹配流体（fluid + components 精确匹配），返回是否接管 */
    private boolean beyond$submitFluid(TeamData teamData, ServerPlayer player, FluidTask task) {
        if (teamData.isCompleted(task) || !checkTaskSequence(teamData)) return false;
        long need = task.getMaxProgress() - teamData.getProgress(task);
        if (need <= 0) return false;
        FluidStackKey taskKey;
        try {
            taskKey = new FluidStackKey(new FluidStack(Holder.direct(task.getFluid()), 1,
                    task.getFluidDataComponentPatch()));
        } catch (Throwable ignored) {
            return false;
        }
        long got = FtbIntegrationHelper.consumeFluidFromNetwork(player,
                key -> key.isSameTypeSameComponents(taskKey), need);
        if (got <= 0) return false;
        teamData.addProgress(task, got);
        return true;
    }

    /** 能量任务：从主网络扣除 FE，返回是否接管 */
    private boolean beyond$submitEnergy(TeamData teamData, ServerPlayer player, EnergyTask task) {
        if (teamData.isCompleted(task) || !checkTaskSequence(teamData)) return false;
        long need = task.getMaxProgress() - teamData.getProgress(task);
        if (need <= 0) return false;
        long got = FtbIntegrationHelper.consumeEnergyFromNetwork(player, need);
        if (got <= 0) return false;
        teamData.addProgress(task, got);
        return true;
    }
}
