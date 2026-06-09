package com.solr98.beyondintegration.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solr98.beyondintegration.cache.AmmoCountCache;
import com.solr98.beyondintegration.handler.NetworkAmmoExtractor;
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

import java.util.HashMap;
import java.util.Map;

@Mixin(value = GunHudOverlay.class, remap = false)
public class GunHudOverlayMixin {

    @Shadow
    private static int cacheInventoryAmmoCount;

    @Unique
    private static Map<Integer, Integer> beyond$networkAmmoMap = new HashMap<>();

    @Inject(method = "handleInventoryAmmo", at = @At("RETURN"))
    private static void onHandleInventoryAmmo(ItemStack stack, Inventory inventory, CallbackInfo ci) {
        if (cacheInventoryAmmoCount >= 9999) return;

        ResourceLocation ammoId = NetworkAmmoExtractor.getAmmoIdClient(stack);
        if (ammoId == null) return;

        beyond$networkAmmoMap = AmmoCountCache.getAllNetworks(ammoId);
        AmmoCountCache.requestQuick(ammoId);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun)) return;

        if (beyond$networkAmmoMap.isEmpty()) return;

        int netId = -1;
        int count = 0;
        for (var entry : beyond$networkAmmoMap.entrySet()) {
            if (entry.getKey() >= 0 && entry.getValue() > 0) {
                netId = entry.getKey();
                count = entry.getValue();
                break;
            }
        }
        if (netId < 0) return;

        Font font = mc.font;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.scale(0.8f, 0.8f, 1);

        float baseX = (width - 70) / 0.8f;
        float baseY = (height - 53) / 0.8f;

        Component text = count == Integer.MAX_VALUE
                ? Component.translatable("hud.beyond_integration.network_ammo.infinite", netId)
                : Component.translatable("hud.beyond_integration.network_ammo", netId, count);
        graphics.drawString(font, text, (int) baseX, (int) baseY, 0x55FFFF, false);

        poseStack.popPose();
    }
}
