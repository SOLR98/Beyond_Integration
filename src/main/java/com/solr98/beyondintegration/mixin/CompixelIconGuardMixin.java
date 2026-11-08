package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.CompixelIconGuard;
import dev.compixel.forge.drawing.NativeRefresh;
import dev.compixel.forge.item.ItemIcon;
import dev.compixel.forge.item.NativeIconAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * CompixelUI 物品图标刷新策略的安全兜底。
 * <p>
 * 触发点：{@code NativeItemAtlas$Pages.refresh(icon)} → {@code animations.resolve(icon)}。
 * CompixelUI 0.1.15 的 {@code resolve} 内部把 {@code BakedModel.getParticleIcon()} 当非空
 * （Kotlin 对平台类型插了非空断言），而 Forge 的自定义 BakedModel 允许返回 null →
 * {@code NullPointerException: getParticleIcon(...) must not be null}，直接炸客户端渲染线程
 * （复现：打开 BD 存储界面 / 退出存档时仍在渲染该界面）。
 * <p>
 * 这里包住 {@code resolve} 调用：抛异常时交给 {@link CompixelIconGuard} 记录
 * 「正在渲染哪个物品（资源键 + NBT）」，并回退为 {@link NativeRefresh#STATIC}
 * （该图标照常画一次，只是不再按动画重绘），界面继续渲染。
 * <p>
 * {@code require = 0}：上游若改了这里的实现（例如已修复并重构），注入自然失效而不是让启动崩溃。
 */
@Mixin(targets = "dev.compixel.forge.item.NativeItemAtlas$Pages", remap = false)
public abstract class CompixelIconGuardMixin {

    @Redirect(
            // 带描述符：Pages 还有编译期生成的桥接 refresh(Object)，不加描述符会尝试注入它
            method = "refresh(Ldev/compixel/forge/item/ItemIcon;)Ldev/compixel/bridge/NativeImageRefresh;",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/compixel/forge/item/NativeIconAnimation;resolve(Ldev/compixel/forge/item/ItemIcon;)Ldev/compixel/forge/drawing/NativeRefresh;"
            ),
            require = 0,
            remap = false
    )
    private NativeRefresh beyond$guardResolve(NativeIconAnimation animation, ItemIcon icon) {
        try {
            return animation.resolve(icon);
        } catch (Throwable error) {
            CompixelIconGuard.report(icon, error);
            return NativeRefresh.STATIC;
        }
    }
}
