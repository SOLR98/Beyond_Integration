package com.solr98.beyondintegration.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB 任务界面（客户端）：在顶部按钮面板（"收集奖励"按钮所在）追加「任务检测扫描」按钮，
 * 供玩家手动触发一次任务检测（图标：BD 维度网络发生器）。
 * <p>
 * FTB 未安装/RI 让路时自动跳过（@Pseudo + require=0）。
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.quests.OtherButtonsPanelTop", remap = false)
public class FtbTaskScanButtonMixin {

    @Inject(method = "addWidgets", at = @At("TAIL"), remap = false, require = 0)
    private void beyond$addScanButton(CallbackInfo ci) {
        try {
            var self = (dev.ftb.mods.ftblibrary.ui.Panel) (Object) this;
            self.add(new com.solr98.beyondintegration.client.gui.BeyondTaskScanButton(self));
        } catch (Throwable ignored) {}
    }
}
