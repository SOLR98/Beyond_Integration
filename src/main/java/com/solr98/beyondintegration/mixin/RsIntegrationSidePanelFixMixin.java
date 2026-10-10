package com.solr98.beyondintegration.mixin;

import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * rs_integration（RI）兼容修复：让 {@code RSSidePanelClient.registerKeyMappings} 读取配置时安全回退。
 * <p>
 * 构造期调用会读 {@code RSIntegrationConfig.RS_SIDE_PANEL_KEY.get()}（配置未加载 → 抛异常）。
 * 重定向为"成功取实值、失败取默认值"，方法可正常完成、{@code KEY_TOGGLE_PANEL} 被创建。
 * 未装 RI 或结构变化时静默跳过。
 */
@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.sidepanel.RSSidePanelClient", remap = false)
public class RsIntegrationSidePanelFixMixin {

    @Redirect(method = "registerKeyMappings",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/common/ForgeConfigSpec$IntValue;get()Ljava/lang/Object;"),
            remap = false, require = 0)
    private static Object beyond$safeConfigGet(ForgeConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (Throwable t) {
            return value.getDefault();
        }
    }
}
