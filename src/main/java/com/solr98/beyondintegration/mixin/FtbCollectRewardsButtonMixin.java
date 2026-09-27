package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Quests 集成（1.20.1 客户端）：任务界面"收集奖励"按钮 Shift+点击时，
 * 打开奖励领取选择界面（逐条选择网络/背包去向）；普通点击保持原版领取。
 * <p>
 * FTB 未安装/RI 让路/配置关闭时自动跳过。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.quests.CollectRewardsButton", remap = false)
public class FtbCollectRewardsButtonMixin {

    @Inject(method = "onClicked", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void beyond$shiftOpenRewardSelect(MouseButton button, CallbackInfo ci) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        if (!Screen.hasShiftDown()) return;
        com.solr98.beyondintegration.client.gui.FtbRewardSelectScreen.openFromClient();
        ci.cancel();
    }
}
