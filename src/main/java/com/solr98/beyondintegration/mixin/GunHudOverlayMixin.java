package com.solr98.beyondintegration.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 TACZ 的 {@link GunHudOverlay}（枪械 HUD 覆层），
 * 在弹药 HUD 上追加维度网络弹药行：网络名称(Net#id)与网络弹药数（无限弹显示 ∞）。
 *
 * <p>网络弹药数量只依赖 {@code render} 这一个稳定入口（TACZ 1.1.4 / 1.1.8 签名一致），
 * 不再注入内部统计方法（{@code handleInventoryAmmo} / {@code handleCacheCount}）：
 * 后两者的参数在 1.1.4（4 参）与 1.1.8（5 参）之间不同，单一注入无法同时命中，
 * 且 {@code handleInventoryAmmo} 只在非虚拟弹药分支被调用，覆盖不到 {@code useDummyAmmo} 枪械。
 */
@Mixin(value = GunHudOverlay.class, remap = false)
public class GunHudOverlayMixin {

    /** 影射原类缓存的背包弹药数（原 HUD 使用） */
    @Shadow
    private static int cacheInventoryAmmoCount;

    /** 缓存的网络弹药数量（本轮 render 刷新结果） */
    @Unique
    private static int beyond$networkAmmoCount = 0;

    /** 在原 HUD 渲染末尾刷新网络弹药数量并追加绘制网络信息（0.8 倍缩放） */
    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun)) {
            beyond$networkAmmoCount = 0;
            return;
        }

        // 原值达到 9999 上限时视为已满则不叠加网络弹药
        if (cacheInventoryAmmoCount >= 9999) {
            beyond$networkAmmoCount = 0;
            return;
        }

        // 每帧按主手枪刷新网络弹药数量（客户端缓存读取与请求均有内部节流）
        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(stack);
        if (ammoId == null) {
            beyond$networkAmmoCount = 0;
            return;
        }
        beyond$networkAmmoCount = TaczAmmoCache.getCount(ammoId);
        if (beyond$networkAmmoCount <= 0) return;

        Font font = mc.font;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.scale(0.8f, 0.8f, 1);

        float baseX = (width - 70) / 0.8f;
        float baseY = (height - 53) / 0.8f;

        String netName = TaczAmmoCache.getNetName();
        int netId = TaczAmmoCache.getNetId();
        Component netLine = netId >= 0
                ? (netName != null && !netName.isEmpty()
                        ? Component.translatable("hud.beyond_integration.network_title.name", netName, netId)
                        : Component.translatable("hud.beyond_integration.network_title", netId))
                : null;
        if (netLine != null) {
            graphics.drawString(font, netLine, (int) baseX, (int) baseY - 10, 0x55FFFF, false);
        }

        Component text = beyond$networkAmmoCount == Integer.MAX_VALUE
                ? Component.translatable("hud.beyond_integration.network_ammo.infinite")
                : Component.translatable("hud.beyond_integration.network_ammo", beyond$networkAmmoCount);
        graphics.drawString(font, text, (int) baseX, (int) baseY, 0x55FFFF, false);

        poseStack.popPose();
    }
}
