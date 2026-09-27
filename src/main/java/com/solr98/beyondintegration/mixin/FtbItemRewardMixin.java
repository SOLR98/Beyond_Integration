package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import dev.ftb.mods.ftbquests.net.NotifyItemRewardMessage;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Quests 集成：物品奖励发放方向。
 * <p>
 * 仅当玩家处于"领取进网络"状态（任务界面 Shift+点击"领取所有"）时接管：
 * 奖励直接插入 BD 主网络；网络容量不足的余量按原版方式发给玩家（不丢失）。
 * 普通领取保持 FTB 原版行为（放入背包）。
 * <p>
 * 进网络范围限制：仅物品奖励（ItemReward，可映射为 BD 物品键；流体/能量只能以物品形态随其落库）。
 * FTB 没有流体/能量奖励类型，XP/等级/命令/战利品表/成就/阶段/提示/自定义/货币等奖励
 * 一律保持 FTB 原版发放，不进入网络。
 * <p>
 * FTB Quests 未安装（@Pseudo）/RI 加载（MixinPlugin 让路）/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.reward.ItemReward", remap = false)
public class FtbItemRewardMixin {

    @Shadow(remap = false) private ItemStack item;
    @Shadow(remap = false) private int count;
    @Shadow(remap = false) private int randomBonus;
    @Shadow(remap = false) private boolean onlyOne;
    /** Reward 基类字段：是否禁用奖励界面模糊（通知消息携带，与原版一致） */
    @Shadow(remap = false) protected boolean disableRewardScreenBlur;

    @Inject(method = "claim", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void beyond$claimToNetwork(ServerPlayer player, boolean notify, CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        // 仅"领取进网络"路径接管；普通领取保持原版背包发放
        if (!FtbIntegrationHelper.isNetworkClaim(player)) return;
        try {
            if (item == null || item.isEmpty() || count <= 0) return;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return;  // 无网络 → 保持原版发放
            // 原版 onlyOne 语义：背包已拥有则不发
            if (onlyOne && player.getInventory().contains(item)) return;

            int size = count + player.level().getRandom().nextInt(randomBonus + 1);
            if (size <= 0) return;

            ItemStackKey key = new ItemStackKey(item.copyWithCount(1));
            KeyAmount leftover = net.getUnifiedStorage().insert(key, size, false);
            net.setDirty();

            long remain = leftover.amount();
            long inserted = size - remain;
            if (remain > 0) {
                // 网络容量不足/被拦截：未入网部分按原版方式发给玩家，不丢失
                ItemStack template = item.copyWithCount(1);
                while (remain > 0) {
                    int give = (int) Math.min(remain, template.getMaxStackSize());
                    ItemStack stack = template.copyWithCount(give);
                    player.getInventory().add(stack);
                    if (!stack.isEmpty()) player.drop(stack, false);
                    remain -= give;
                }
            }
            // 简化通知（批量领取）：逐条静默，仅累计，结束时由批量入口发一条汇总
            boolean batch = FtbIntegrationHelper.isBatchNotify(player);
            // 补发 FTB 物品奖励通知（与原版 claim 行为一致）+ "已收到网络"标识
            if (notify && !batch) {
                try {
                    PacketDistributor.sendToPlayer(player,
                            new NotifyItemRewardMessage(item, 0, disableRewardScreenBlur));
                } catch (Throwable ignored) {}
            }
            if (inserted > 0) {
                if (batch) {
                    FtbIntegrationHelper.addBatchNotify(player, inserted);
                } else {
                    player.displayClientMessage(Component.translatable(
                            "beyond_integration.ftb.reward.network_notice", item.getHoverName(), inserted)
                            .withStyle(ChatFormatting.AQUA), true);
                }
            }
            ci.cancel();
        } catch (Throwable ignored) {}
    }
}
