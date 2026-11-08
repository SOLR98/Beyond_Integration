package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.xp.XpTargetLevels;
import com.wintercogs.beyonddimensions.common.item.XpExchangeSettings;
import com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu;
import dev.compixel.forge.sync.MenuAction;
import dev.compixel.forge.sync.MenuSync;
import dev.compixel.sync.state.SyncCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 经验棒「目标等级」自定义上限在菜单侧的补全。
 * <p>BD 的 {@code XpExchangeMenu} 在 {@code requestTarget} 与 {@code SET_TARGET} 两处都用
 * 编译期常量 {@link XpExchangeSettings#MAX_TARGET_LEVEL}（21863）校验，BI 的
 * {@code XpExchangeSettingsMixin} 只放宽了 {@code sanitizeTargetLevel}（输入框/数据读取），
 * 因此「界面能填但提交被拒」。
 * <p>这里：
 * <ol>
 *   <li>{@code requestTarget} 改为按 {@link XpTargetLevels#max()} 校验后自行发请求；</li>
 *   <li>{@code SET_TARGET} 的处理器在类初始化末尾替换为按同一上限校验的版本
 *       （服务端回推时不会再拒）。</li>
 * </ol>
 * 开关关闭时两者都退回 BD 原上限，行为与未装 BI 一致。
 */
@Mixin(value = XpExchangeMenu.class, remap = false)
public abstract class XpExchangeTargetLimitMixin {

    @Shadow
    @Final
    private MenuSync<XpExchangeMenu> synchronization;

    @Shadow
    @Final
    @Mutable
    private static MenuAction<XpExchangeMenu, Integer> SET_TARGET;

    @Shadow
    private static boolean editable(XpExchangeMenu menu) {
        throw new AssertionError();
    }

    /** 类初始化末尾替换 SET_TARGET：校验按 BI 生效上限。 */
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void beyond$replaceSetTarget(CallbackInfo ci) {
        SET_TARGET = MenuAction.of("set_target", SyncCodecs.INT, (menu, player, value) -> {
            if (!editable(menu) || value < 0 || value > XpTargetLevels.max()) return false;
            if (XpExchangeSettings.getTargetLevel(menu.menuStack) != value.intValue()) {
                XpExchangeSettings.setTargetLevel(menu.menuStack, value);
                menu.player.getInventory().setChanged();
            }
            return true;
        });
    }

    /** 客户端请求路径：用 BI 生效上限取代 BD 的 21863。 */
    @Inject(method = "requestTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private void beyond$widenRequestTarget(int value, CallbackInfoReturnable<Boolean> cir) {
        XpExchangeMenu self = (XpExchangeMenu) (Object) this;
        if (!self.targetEditable() || value < 0 || value > XpTargetLevels.max()) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(synchronization.request(SET_TARGET, value).queued());
    }
}
