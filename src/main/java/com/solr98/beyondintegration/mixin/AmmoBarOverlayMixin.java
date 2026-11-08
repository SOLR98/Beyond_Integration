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

import com.atsuishio.superbwarfare.client.overlay.AmmoBarOverlay;
import com.atsuishio.superbwarfare.client.overlay.RenderContext;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestSuperbAmmoStatusPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.text.NumberFormat;

/**
 * 注入 Superb Warfare 的 {@link AmmoBarOverlay}，在原有弹药条之上追加渲染
 * 当前维度网络的三行信息：网络名称(Net#id)、网络能量(FE)、网络弹药数量(玩家弹药/物品弹药)。
 */
@Mixin(value = AmmoBarOverlay.class, remap = false)
public abstract class AmmoBarOverlayMixin {

    /** 在原弹药条渲染完毕后，追加绘制维度网络弹药状态（名称/能量/数量） */
    @Inject(method = "render(Lcom/atsuishio/superbwarfare/client/overlay/RenderContext;)V", at = @At("RETURN"), remap = false)
    private void beyond$renderNetworkStatus(RenderContext context, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        var player = context.getPlayer();
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem)) return;
        if (player.getVehicle() != null) return;

        GunData data = GunData.from(stack);
        AmmoConsumer consumer = data.selectedAmmoConsumer();
        if (consumer == null) return;

        if (!SuperbAmmoCache.hasData() || SuperbAmmoCache.isStale()) {
            if (SuperbAmmoCache.canRequest()) {
                SuperbAmmoCache.markRequested();
                PacketHandler.sendToServer(new RequestSuperbAmmoStatusPacket());
            }
            if (!SuperbAmmoCache.hasData()) return;
        }
        if (SuperbAmmoCache.getNetId() < 0) return;

        Font font = mc.font;
        int h = context.getScreenHeight();
        int rightEdge = context.getScreenWidth() - 12;
        int y = h - 60;

        String netName = SuperbAmmoCache.getNetworkName();
        int netId = SuperbAmmoCache.getNetId();

        // 第 1 行（顶部）：基地 (Net#1) —— 右对齐，永远渲染
        String netLine = !netName.isEmpty()
                ? netName + " (Net#" + netId + ")"
                : "(Net#" + netId + ")";
        context.getGuiGraphics().drawString(font, netLine, rightEdge - font.width(netLine), y - 29, 0x55FFFF, true);

        // 第 2 行（中部）：xxxx FE —— 右对齐，永远渲染
        String energyStr = "";
        long netEnergy = SuperbAmmoCache.getNetworkEnergy();
        if (netEnergy >= 0) {
            energyStr = NumberFormat.getIntegerInstance().format(netEnergy) + " FE";
        }
        context.getGuiGraphics().drawString(font, energyStr, rightEdge - font.width(energyStr), y - 19, 0xFFAA00, true);

        // 第 3 行（底部）：xxxx : 弹药名称 —— 右对齐，永远渲染
        String ammoStr = "";
        if (consumer.getType() == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
            com.atsuishio.superbwarfare.data.gun.Ammo ammoType = consumer.getPlayerAmmoType();
            if (ammoType != null) {
                long count = SuperbAmmoCache.getCount(ammoType.serializationName);
                boolean infinite = SuperbAmmoCache.getCount("__infinite__") > 0;
                if (count > 0 || infinite) {
                    String countStr = infinite ? "∞" : NumberFormat.getIntegerInstance().format(count);
                    String name = Component.translatable(ammoType.translationKey).getString();
                    ammoStr = countStr + " : " + name;
                }
            }
        } else if (consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM) {
            String raw = consumer.stack().isEmpty() ? null : ForgeRegistries.ITEMS.getKey(consumer.stack().getItem()).toString();
            if (raw == null || raw.isEmpty()) raw = consumer.getAmmo();
            if (raw != null && !raw.isEmpty()) {
                raw = raw.strip();
                int space = raw.indexOf(' ');
                if (space > 0) raw = raw.substring(space + 1).strip();
                if (raw.startsWith("@") || raw.startsWith("#")) raw = raw.substring(1);
                String itemId = raw;
                if (itemId != null) {
                    String itemKey = "ITEM:" + itemId;
                    long count = SuperbAmmoCache.getItemCount(itemKey, false);
                    if (count < 0) {
                        // 现查现用（换枪检测在 ClientRegistrar 统一触发，此处兜底）
                        SuperbAmmoCache.requestItems(SuperbAmmoCache.getNetId(),
                                java.util.Collections.singletonList(itemKey), false);
                        count = 0;
                    }
                    if (count > 0) {
                        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
                        if (item != null && item != Items.AIR) {
                            String name = item.getName(ItemStack.EMPTY).getString();
                            ammoStr = NumberFormat.getIntegerInstance().format(count) + " : " + name;
                        }
                    }
                }
            }
        }
        context.getGuiGraphics().drawString(font, ammoStr, rightEdge - font.width(ammoStr), y - 9, 0x55FFFF, true);
    }
}
