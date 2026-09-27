package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Quests 集成（1.21.1 客户端）：奖励选择界面 Shift+点击"领取所有"时，
 * 打开奖励领取选择界面（逐条选择网络/背包去向）；"领取经验"与普通点击保持原版行为。
 * <p>
 * FTB 未安装/RI 让路/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.RewardSelectorScreen", remap = false)
public class FtbRewardSelectorScreenMixin {

    @Inject(method = "doClaimAll", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void beyond$shiftOpenRewardSelect(boolean xpOnly, CallbackInfo ci) {
        if (xpOnly) return;  // 仅领取经验，无法进网络，保持原版
        if (!FtbIntegrationHelper.isEnabled()) return;
        if (!Screen.hasShiftDown()) return;
        com.solr98.beyondintegration.client.gui.FtbRewardSelectScreen.openFromClient();
        ci.cancel();
    }
}
