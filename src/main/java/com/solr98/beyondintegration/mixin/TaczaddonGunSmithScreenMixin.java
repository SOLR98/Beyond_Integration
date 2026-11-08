package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.GunSmithNetMode;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/**
 * taczaddon 兼容：网络模式下清空工作台的"虚拟容器"外部来源（模式互斥）。
 * <p>与主 Mixin（{@code GunSmithTableScreenMixin}）分离实现：本类引用 taczaddon 类型，
 * 由 {@code MixinPlugin} 按「taczaddon 已安装 + 版本兼容」门控——未装 taczaddon 时本类
 * 不应用，TACZ 工作台网络材料模式等主功能不受影响。
 * <p>仅在「切到网络模式」的那一帧清空一次（虚拟容器由 taczaddon 服务端快照写入，
 * 网络模式下不应叠加到材料计数里）。
 */
@Mixin(targets = "com.tacz.guns.client.gui.GunSmithTableScreen", remap = false)
public abstract class TaczaddonGunSmithScreenMixin {

    @Unique private boolean beyond$lastNetMode;

    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void beyond$clearVirtualContainerOnModeSwitch(GuiGraphics graphics, int mouseX, int mouseY,
                                                          float partialTick, CallbackInfo ci) {
        boolean netMode = GunSmithNetMode.isNetworkMode();
        if (netMode == beyond$lastNetMode) return;
        beyond$lastNetMode = netMode;
        if (!netMode) return;
        if (this instanceof com.mafuyu404.taczaddon.init.VirtualContainerLoader loader) {
            loader.taczaddon$setVirtualContainer(new ArrayList<>());
        }
    }
}
