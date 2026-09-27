package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.FtbTaskNetworkCountCache;
import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Quests 集成（客户端）：物品任务 tooltip 追加"BD 网络：N"一行。
 * <p>
 * 数量按需向服务端查询（{@link FtbTaskNetworkCountCache} 缓存 + 限频），
 * 仅在主网络存在且查询有结果时显示；FTB 未安装/RI 让路/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.task.ItemTask", remap = false)
public class FtbItemTaskTooltipMixin {

    /**
     * tooltip 网络数量：数量按需查询（缓存 TTL 5 秒 / 去重 2 秒兜底），
     * 网络变化时由服务端 {@code FtbTooltipPushService} 主动推送最新值（默认 0.5 秒节流），
     * 实现"网络变化后立即更新"。
     */
    @Inject(method = "addMouseOverText", at = @At("RETURN"), remap = false, require = 0)
    private void beyond$appendNetworkCount(TooltipList list, TeamData teamData, CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        try {
            ItemTask self = (ItemTask) (Object) this;
            FtbTaskNetworkCountCache.Entry entry = FtbTaskNetworkCountCache.get(self.id);
            if (entry != null) {
                list.add(Component.translatable("beyond_integration.ftb.network_count",
                        entry.netName(), com.solr98.beyondintegration.util.NumberFormatUtil.grouped(entry.count()))
                        .withStyle(ChatFormatting.AQUA));
            }
        } catch (Throwable ignored) {}
    }
}
