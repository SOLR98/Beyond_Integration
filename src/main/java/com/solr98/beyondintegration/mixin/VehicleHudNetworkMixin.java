package com.solr98.beyondintegration.mixin;
import com.atsuishio.superbwarfare.client.overlay.RenderContext;
import com.atsuishio.superbwarfare.client.overlay.VehicleHudOverlay;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.text.NumberFormat;

@Mixin(targets = "com.atsuishio.superbwarfare.client.overlay.VehicleHudOverlay", remap = false)
public abstract class VehicleHudNetworkMixin {
    @Inject(method = "render(Lcom/atsuishio/superbwarfare/client/overlay/RenderContext;)V", at = @At("RETURN"), remap = false)
    private void beyond$renderVehicleNetworkInfo(RenderContext context, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!(player.getVehicle() instanceof VehicleEntity vehicle)) return;

        int seatIndex = vehicle.getSeatIndex(player);
        if (seatIndex == -1) return;

        if (!SuperbAmmoCache.INSTANCE.vehicleHasData()) return;
        if (SuperbAmmoCache.INSTANCE.getVehicleNetId() < 0) return;

        Font font = mc.font;
        int h = context.getScreenHeight();
        int x = 10;
        int passengerCount = vehicle.getOrderedPassengers().size();
        int y = h - 35 - Math.max(passengerCount - 1, 0) * 12 - 1;

        String netName = SuperbAmmoCache.INSTANCE.getVehicleNetName();
        int netId = SuperbAmmoCache.INSTANCE.getVehicleNetId();

        Component netLine = !netName.isEmpty()
                ? Component.translatable("hud.beyond_integration.network_title.name", netName, netId)
                : Component.translatable("hud.beyond_integration.network_title", "Net#" + netId);
        context.getGuiGraphics().drawString(font, netLine, x, y - 29, 0x55FFFF, true);

        long netEnergy = SuperbAmmoCache.INSTANCE.getVehicleEnergy();
        if (netEnergy > 0) {
            String energyStr = Component.translatable("hud.beyond_integration.energy_line",
                    NumberFormat.getIntegerInstance().format(netEnergy)).getString();
            context.getGuiGraphics().drawString(font, energyStr, x, y - 19, 0xFFAA00, true);
        }

        GunData data = vehicle.getGunData(seatIndex);
        String ammoStr = "";
        if (data != null) {
            AmmoConsumer consumer = data.selectedAmmoConsumer();
            if (consumer != null) {
                if (consumer.getType() == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
                    com.atsuishio.superbwarfare.data.gun.Ammo ammoType = consumer.getPlayerAmmoType();
                    if (ammoType != null) {
                        long count = SuperbAmmoCache.INSTANCE.getVehicleCount(ammoType.serializationName);
                        boolean infinite = SuperbAmmoCache.INSTANCE.getVehicleCount("__infinite__") > 0;
                        if (count > 0 || infinite) {
                            String countStr = infinite ? "\u221E" : NumberFormat.getIntegerInstance().format(count);
                            ammoStr = Component.translatable(ammoType.translationKey).getString() + " : " + countStr;
                        }
                    }
                } else if (consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM) {
                    String raw = consumer.getAmmo();
                    if (raw != null && !raw.isEmpty()) {
                        raw = raw.strip();
                        int space = raw.indexOf(' ');
                        if (space > 0) raw = raw.substring(space + 1).strip();
                        if (raw.startsWith("@") || raw.startsWith("#")) raw = raw.substring(1);
                        String itemId = raw;
                        long count = SuperbAmmoCache.INSTANCE.getVehicleCount("ITEM:" + itemId);
                        if (count > 0) {
                            var rl = ResourceLocation.tryParse(itemId);
                            if (rl != null) {
                                var item = BuiltInRegistries.ITEM.get(rl);
                                ammoStr = item.getName(ItemStack.EMPTY).getString() + " : " + NumberFormat.getIntegerInstance().format(count);
                            }
                        }
                    }
                }
            }
        }
        context.getGuiGraphics().drawString(font, ammoStr, x, y - 9, 0x55FFFF, true);
    }
}
