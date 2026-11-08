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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 TACZ 的 {@link GunHudOverlay}（枪械 HUD 覆层），在右下角弹药区上方
 * 额外独立绘制两行维度网络弹药信息：网络名称(Net#id) 与网络弹药数（无限弹显示 infinity）。
 * <p>数值由 {@link TaczAmmoExtractor#getAmmoIdClient} + {@link TaczAmmoCache} 每帧现算，
 * 不读取也不修改 TACZ 原 HUD 的背包弹药统计（独立显示，背包弹药满也不影响）。
 */
@Mixin(value = GunHudOverlay.class, remap = false)
public class GunHudOverlayMixin {

    /** 在原 HUD 渲染末尾追加网络弹药行（右对齐；TACZ 改签名时跳过注入，不崩溃） */
    @Inject(method = "render", at = @At("RETURN"), require = 0, remap = false)
    private void beyond$renderNetworkAmmo(ForgeGui gui, GuiGraphics graphics, float partialTick,
                                          int width, int height, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun)) return;

        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(stack);
        if (ammoId == null) return;
        int networkAmmo = TaczAmmoCache.getCount(ammoId);
        if (networkAmmo <= 0) return;

        // 无限弹药文本固定为 "infinity"（见 lang 键 hud.beyond_integration.network_ammo.infinite）；
        // 请勿改回 "∞" 或数字——字体/第三方文本覆写对符号宽度不一致，会导致 HUD 错位。
        Component text = networkAmmo == Integer.MAX_VALUE
                ? Component.translatable("hud.beyond_integration.network_ammo.infinite")
                : Component.translatable("hud.beyond_integration.network_ammo", networkAmmo);

        Font font = mc.font;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.scale(0.8f, 0.8f, 1);

        // 缩放空间坐标 = 屏幕坐标 / 0.8；右边缘对齐屏幕右侧，行位（height-53/-63）取 TACZ 1.1.8 弹药区上方
        int rightX = (int) ((width - 8) / 0.8f);
        int ammoY = (int) ((height - 53) / 0.8f);
        int nameY = ammoY - 10;

        int netId = TaczAmmoCache.getNetId();
        if (netId >= 0) {
            String netName = TaczAmmoCache.getNetName();
            Component netLine = netName != null && !netName.isEmpty()
                    ? Component.translatable("hud.beyond_integration.network_title.name", netName, netId)
                    : Component.translatable("hud.beyond_integration.network_title", netId);
            graphics.drawString(font, netLine, rightX - font.width(netLine), nameY, 0x55FFFF, false);
        }
        graphics.drawString(font, text, rightX - font.width(text), ammoY, 0x55FFFF, false);

        poseStack.popPose();
    }
}
