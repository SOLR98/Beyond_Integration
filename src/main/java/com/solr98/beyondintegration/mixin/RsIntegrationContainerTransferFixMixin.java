package com.solr98.beyondintegration.mixin;

import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * rs_integration（RI）兼容修复：让 {@code ContainerTransferClient.registerKeyMappings} 读取配置时安全回退。
 * <p>
 * RI 在 mod 构造期调用该方法，此时其 {@code ForgeConfigSpec} 已注册但未加载，直接读
 * {@code RSIntegrationConfig.CONTAINER_TRANSFER_KEY.get()} 会抛
 * {@code IllegalStateException: Cannot get config value before config is loaded}（开发环境），
 * 导致启动失败。
 * <p>
 * 这里把该次 {@code IntValue.get()} 重定向为"成功则取实值、失败则取配置默认值
 * {@link ForgeConfigSpec.ConfigValue#getDefault()}"。如此方法可正常完成、两个 KeyMapping 均被创建
 * （避免"取消整个方法"造成的 {@code KEY_TOGGLE_MODE} 为 null 的运行期 NPE），且键位取 RI 的默认值。
 * 未装 RI 或结构变化时静默跳过（{@code @Pseudo} + {@code require = 0}）。
 */
@Pseudo
@Mixin(targets = "com.huanghuang.rsintegration.transfer.ContainerTransferClient", remap = false)
public class RsIntegrationContainerTransferFixMixin {

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
