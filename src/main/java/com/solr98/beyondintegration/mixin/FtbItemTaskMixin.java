package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import com.solr98.beyondintegration.feature.ftb.FtbItemSubmitSelectionService;
import com.solr98.beyondintegration.network.OpenFtbItemSubmitSelectPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * FTB Quests 集成：消耗型物品任务提交时，背包与 BD 主网络合并取料。
 * <p>
 * 接管"点击提交"路径（craftedItem 为空）：
 * 候选种类 &gt; 1（典型为标签/过滤器任务）时打开选择界面，由玩家分配各候选数量后提交；
 * 单一候选保持原自动路径——先按原版规则从背包扣料（复用 {@link ItemTask#insert}，含进度累加），
 * 背包不足的部分直接从主网络扣除并计入进度（不经过背包，背包满也能提交）。
 * 合成提交（craftedItem 非空）保持 FTB 原版行为。
 * <p>
 * FTB Quests 未安装（@Pseudo）/RI 加载（MixinPlugin 让路）/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.task.ItemTask", remap = false)
public abstract class FtbItemTaskMixin {

    @Inject(method = "submitTask", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void beyond$submitWithNetwork(TeamData teamData, ServerPlayer player, ItemStack craftedItem, CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        try {
            // 仅处理点击提交路径（合成提交保持原版）
            if (!craftedItem.isEmpty()) return;
            ItemTask self = (ItemTask) (Object) this;
            if (!self.consumesResources() || self.isTaskScreenOnly()) return;
            if (teamData.isCompleted(self) || !FtbItemSubmitSelectionService.checkTaskSequence(self, teamData)) return;

            // 多候选（标签/过滤器任务等）：打开选择界面，由玩家分配数量后提交
            List<FtbItemSubmitSelectionService.Entry> entries =
                    FtbItemSubmitSelectionService.collectEntries(player, self);
            if (entries != null && entries.size() > 1) {
                long remaining = Math.max(0, self.getMaxProgress() - teamData.getProgress(self));
                List<OpenFtbItemSubmitSelectPacket.Entry> packetEntries = new ArrayList<>(entries.size());
                for (FtbItemSubmitSelectionService.Entry entry : entries) {
                    packetEntries.add(new OpenFtbItemSubmitSelectPacket.Entry(
                            entry.stack(), entry.bag(), entry.net()));
                }
                String title = "";
                try {
                    var altTitle = self.getAltTitle();
                    if (altTitle != null) {
                        // 标题数量前缀格式化为 FTB 显示格式（如 "600000000000x" → "600Bx"）
                        title = altTitle.getString().replaceFirst("^\\d+", java.util.regex.Matcher.quoteReplacement(
                                com.solr98.beyondintegration.util.NumberFormatUtil.compact(self.getMaxProgress())));
                    }
                } catch (Throwable ignored) {}
                PacketHandler.sendToPlayer(player,
                        new OpenFtbItemSubmitSelectPacket(self.id, title, remaining, packetEntries));
                ci.cancel();
                return;
            }

            // 1) 背包部分：原版扣料（ItemTask.insert 内部扣物品并累加进度）
            boolean changed = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                ItemStack rest = self.insert(teamData, stack, false);
                if (stack != rest) {
                    changed = true;
                    player.getInventory().setItem(i, rest.isEmpty() ? ItemStack.EMPTY : rest);
                }
            }
            if (changed) {
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            }

            // 2) 网络部分：背包不足的缺口直接从主网络扣除并计入进度
            long remain = self.getMaxProgress() - teamData.getProgress(self);
            if (remain > 0) {
                long consumed = FtbIntegrationHelper.consumeFromNetwork(player, self::test, remain);
                if (consumed > 0) {
                    teamData.addProgress(self, consumed);
                }
            }
            ci.cancel();
        } catch (Throwable ignored) {}
    }
}
