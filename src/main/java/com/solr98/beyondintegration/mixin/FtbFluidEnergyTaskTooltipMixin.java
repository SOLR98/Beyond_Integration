package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.FtbTaskNetworkCountCache;
import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.EnergyTask;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Quests 集成（客户端）：流体 / 能量任务 tooltip 追加"网络：N"（主网络库存）。
 * <p>
 * 数量按需向服务端查询（{@link FtbTaskNetworkCountCache} 缓存 + 限频）：
 * 流体任务返回匹配流体总量（mB），能量任务返回网络 FE 库存；无网络/无结果不显示。
 * ItemTask 的 tooltip 由 {@code FtbItemTaskTooltipMixin} 处理（本注入仅针对父类未覆盖的方法）。
 * <p>
 * FTB 未安装/RI 让路/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.task.Task", remap = false)
public class FtbFluidEnergyTaskTooltipMixin {

    @Inject(method = "addMouseOverText", at = @At("RETURN"), remap = false, require = 0)
    private void beyond$appendNetworkCount(TooltipList list, TeamData teamData, CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        try {
            Task self = (Task) (Object) this;
            if (!(self instanceof FluidTask) && !(self instanceof EnergyTask)) return;
            FtbTaskNetworkCountCache.Entry entry = FtbTaskNetworkCountCache.get(self.id);
            if (entry != null) {
                list.add(Component.translatable("beyond_integration.ftb.network_count",
                        entry.netName(), com.solr98.beyondintegration.util.NumberFormatUtil.grouped(entry.count()))
                        .withStyle(ChatFormatting.AQUA));
            }
        } catch (Throwable ignored) {}
    }
}
