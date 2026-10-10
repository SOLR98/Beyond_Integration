package com.solr98.beyondintegration.mixin;

import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * rs_integration（RI）兼容修复：让 {@code RSSidePanelModule.isEnabled} 读取配置时安全回退。
 * <p>
 * 该方法被 {@code RSSidePanelClient.registerKeyMappings} 在构造期调用，会读
 * {@code RSIntegrationConfig.ENABLE_RS_SIDE_PANEL.get()}（配置未加载 → 抛异常）。
 * 重定向为"成功取实值、失败取默认值"。未装 RI 或结构变化时静默跳过。
 */
@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.sidepanel.RSSidePanelModule", remap = false)
public class RsIntegrationSidePanelModuleFixMixin {

    @Redirect(method = "isEnabled",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/common/ForgeConfigSpec$BooleanValue;get()Ljava/lang/Object;"),
            remap = false, require = 0)
    private static Object beyond$safeConfigGet(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (Throwable t) {
            return value.getDefault();
        }
    }
}
