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

@Mixin(value = GunHudOverlay.class, remap = false)
public class GunHudOverlayMixin {

    @Shadow
    private static int cacheInventoryAmmoCount;

    @Unique
    private static int beyond$networkAmmoCount = 0;

    @Inject(method = "handleInventoryAmmo", at = @At("RETURN"))
    private static void onHandleInventoryAmmo(ItemStack stack, Inventory inventory, CallbackInfo ci) {
        if (cacheInventoryAmmoCount >= 9999) return;

        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(stack);
        if (ammoId == null) return;

        beyond$networkAmmoCount = TaczAmmoCache.getCount(ammoId);
    }

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

        Component text = beyond$networkAmmoCount == Integer.MAX_VALUE
                ? Component.translatable("hud.beyond_integration.network_ammo.infinite")
                : Component.translatable("hud.beyond_integration.network_ammo", beyond$networkAmmoCount);
        graphics.drawString(font, text, (int) baseX, (int) baseY, 0x55FFFF, false);

        poseStack.popPose();
    }
}
