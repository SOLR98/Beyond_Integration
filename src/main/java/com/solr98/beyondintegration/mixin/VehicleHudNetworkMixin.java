/*
 * Beyond Cmd Extension - 超越命令扩展
 * Copyright (C) 2025 solr98
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program contains modifications of:
 *   Superb Warfare - Copyright (C) Atsuishio, Roki27, Light_Quanta (GPLv3)
 *     https://github.com/Mercurows/SuperbWarfare
 *   Timeless & Classics Guns: Zero - Copyright (C) Serene Wave Studio / Timeless Squad (GPLv3)
 *     https://github.com/MCModderAnchor/TACZ
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Source code: https://github.com/SOLR98/beyond_Cmd-Extension
 */
package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.client.overlay.RenderContext;
import com.atsuishio.superbwarfare.client.overlay.VehicleHudOverlay;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.text.NumberFormat;

@Mixin(value = VehicleHudOverlay.class, remap = false)
public abstract class VehicleHudNetworkMixin {

    @Inject(method = "render(Lcom/atsuishio/superbwarfare/client/overlay/RenderContext;)V",
            at = @At("RETURN"), remap = false)
    private void beyond$renderVehicleNetworkInfo(RenderContext context, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!(player.getVehicle() instanceof VehicleEntity vehicle)) return;

        int seatIndex = vehicle.getSeatIndex(player);
        if (seatIndex == -1) return;

        if (!SuperbAmmoCache.vehicleHasData()) return;
        if (SuperbAmmoCache.getVehicleNetId() < 0) return;

        Font font = mc.font;
        int h = context.getScreenHeight();
        int x = 10;
        int passengerCount = vehicle.getOrderedPassengers().size();
        int y = h - 35 - Math.max(passengerCount - 1, 0) * 12 - 1;

        String netName = SuperbAmmoCache.getVehicleNetName();
        int netId = SuperbAmmoCache.getVehicleNetId();

        // 第 1 行（顶部）：网络 : xxxx (Net#x)
        Component netLine = !netName.isEmpty()
                ? Component.translatable("hud.beyond_integration.network_title.name", netName, netId)
                : Component.translatable("hud.beyond_integration.network_title", "Net#" + netId);
        context.getGuiGraphics().drawString(font, netLine, x, y - 29, 0x55FFFF, true);

        // 第 2 行（中部）：FE能量⚡ : xxx,xxx FE
        long netEnergy = SuperbAmmoCache.getVehicleEnergy();
        String energyStr = netEnergy >= 0
                ? Component.translatable("hud.beyond_integration.energy_line",
                        NumberFormat.getIntegerInstance().format(netEnergy)).getString()
                : "";
        context.getGuiGraphics().drawString(font, energyStr, x, y - 19, 0xFFAA00, true);

        // 第 3 行（底部）：弹药 : xxx
        GunData data = vehicle.getGunData(seatIndex);
        String ammoStr = "";
        if (data != null) {
            AmmoConsumer consumer = data.selectedAmmoConsumer();
            if (consumer != null) {
                if (consumer.getType() == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
                    com.atsuishio.superbwarfare.data.gun.Ammo ammoType = consumer.getPlayerAmmoType();
                    if (ammoType != null) {
                        long count = SuperbAmmoCache.getVehicleCount(ammoType.serializationName);
                        boolean infinite = SuperbAmmoCache.getVehicleCount("__infinite__") > 0;
                        if (count > 0 || infinite) {
                            String countStr = infinite ? "∞" : NumberFormat.getIntegerInstance().format(count);
                            ammoStr = Component.translatable(ammoType.translationKey).getString()
                                    + " : " + countStr;
                        }
                    }
                } else if (consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM) {
                    String itemId = null;
                    String raw = consumer.stack().isEmpty() ? null : ForgeRegistries.ITEMS.getKey(consumer.stack().getItem()).toString();
                    if (raw == null || raw.isEmpty()) raw = consumer.getAmmo();
                    if (raw != null && !raw.isEmpty()) {
                        raw = raw.strip();
                        int space = raw.indexOf(' ');
                        if (space > 0) raw = raw.substring(space + 1).strip();
                        if (raw.startsWith("@") || raw.startsWith("#")) raw = raw.substring(1);
                        itemId = raw;
                    }
                    if (itemId != null) {
                        long count = SuperbAmmoCache.getVehicleCount("ITEM:" + itemId);
                        if (count > 0) {
                            var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
                            if (item != null && item != Items.AIR) {
                                ammoStr = item.getName(ItemStack.EMPTY).getString()
                                        + " : " + NumberFormat.getIntegerInstance().format(count);
                            }
                        }
                    }
                }
            }
        }
        context.getGuiGraphics().drawString(font, ammoStr, x, y - 9, 0x55FFFF, true);
    }
}
