package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import com.solr98.beyondintegration.network.ClaimRewardToNetworkPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Quests 集成（客户端）：任务/奖励界面 Shift+点击单条奖励按钮时，
 * 改为发送"单条奖励领取到网络"请求（物品奖励入网），普通点击保持原版领取。
 * <p>
 * FTB 未安装/RI 让路/配置关闭时自动跳过；服务端调用时因 {@link Screen} 不可用自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.reward.Reward", remap = false)
public class FtbRewardButtonMixin {

    @Inject(method = "onButtonClicked", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void beyond$shiftClaimToNetwork(Button button, boolean canClaim, CallbackInfo ci) {
        if (!canClaim) return;
        if (!FtbIntegrationHelper.isEnabled()) return;
        try {
            if (!Screen.hasShiftDown()) return;
        } catch (Throwable ignored) {
            return;  // 服务端无 Screen 类，保持原版
        }
        Reward self = (Reward) (Object) this;
        // 选择奖励需要玩家在界面中选择子奖励（异步），网络标记无法覆盖，保持原版领取
        if (self instanceof dev.ftb.mods.ftbquests.quest.reward.ChoiceReward) return;
        PacketHandler.sendToServer(new ClaimRewardToNetworkPacket(self.getId()));
        ci.cancel();
    }

    /** 奖励 tooltip 追加"Shift+点击可收进网络"提示（仅可领取的物品奖励） */
    @Inject(method = "addMouseOverText", at = @At("RETURN"), remap = false, require = 0)
    private void beyond$appendShiftClaimHint(dev.ftb.mods.ftblibrary.util.TooltipList list, CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        try {
            Reward self = (Reward) (Object) this;
            // 仅物品奖励可入网（其余奖励 Shift 领取保持原版）
            if (!(self instanceof dev.ftb.mods.ftbquests.quest.reward.ItemReward)) return;
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) return;
            if (!dev.ftb.mods.ftbquests.client.ClientQuestFile.exists()) return;
            dev.ftb.mods.ftbquests.quest.TeamData data =
                    dev.ftb.mods.ftbquests.client.ClientQuestFile.INSTANCE.selfTeamData;
            if (data == null || !data.getClaimType(player.getUUID(), self).canClaim()) return;
            list.add(net.minecraft.network.chat.Component.translatable("beyond_integration.ftb.shift_claim_hint")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        } catch (Throwable ignored) {}
    }
}
