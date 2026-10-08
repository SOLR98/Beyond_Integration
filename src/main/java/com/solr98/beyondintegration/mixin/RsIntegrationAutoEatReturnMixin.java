package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.compat.RsIntegrationCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * rs_integration（RI）自动进食选择界面关闭返回。
 * 本模组替代按钮（RsAutoEatButton.SELECT）打开的 AutoEatScreen 关闭（ESC / 完成）后
 * 应返回来源 BD 界面，而 RI 原实现直接回游戏。这里在关闭末尾恢复本模组记录的来源界面。
 * RI 未安装或方法名不匹配时（{@code @Pseudo} + require=0）自动跳过。
 */
@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.autoeat.client.AutoEatScreen", remap = false)
public class RsIntegrationAutoEatReturnMixin {

    /** Forge 运行时映射：Screen#onClose 的 SRG 名 */
    @Inject(method = "m_7046_", at = @At("TAIL"), remap = false, require = 0)
    private void beyond$returnToBdSrg(CallbackInfo ci) {
        beyond$returnToBd();
    }

    /** 开发环境/命名映射：onClose */
    @Inject(method = "onClose", at = @At("TAIL"), remap = false, require = 0)
    private void beyond$returnToBdNamed(CallbackInfo ci) {
        beyond$returnToBd();
    }

    @Unique
    private void beyond$returnToBd() {
        Screen parent = RsIntegrationCompat.takeReturnScreen();
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
        }
    }
}
