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
import net.minecraft.world.entity.player.Inventory;
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
 * 在弹药 HUD 上追加维度网络弹药行：网络名称(Net#id)与网络弹药数（无限弹显示 ∞），
 * 数量来自 handleInventoryAmmo 注入点读取的客户端弹药缓存。
 */
@Mixin(value = GunHudOverlay.class, remap = false)
public class GunHudOverlayMixin {

    /** 影射原类缓存的背包弹药数（原 HUD 使用） */
    @Shadow
    private static int cacheInventoryAmmoCount;

    /** 缓存的网络弹药数量（本轮注入结果） */
    @Unique
    private static int beyond$networkAmmoCount = 0;

    /** 在原弹药统计后记录网络弹药数量（原值达到 9999 上限时视为已满则不叠加） */
    @Inject(method = "handleInventoryAmmo", at = @At("RETURN"))
    private static void onHandleInventoryAmmo(ItemStack stack, Inventory inventory, CallbackInfo ci) {
        if (cacheInventoryAmmoCount >= 9999) return;

        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(stack);
        if (ammoId == null) return;

        beyond$networkAmmoCount = TaczAmmoCache.getCount(ammoId);
    }

    /** 在原 HUD 渲染末尾追加网络弹药信息（网络名 + 数量，0.8 倍缩放绘制） */
    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height, CallbackInfo ci) {
        if (beyond$networkAmmoCount <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun)) return;

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
