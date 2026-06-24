package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.vehicle.VehicleNetStorage;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.item.NetTerminalItem", remap = false)
public class NetTerminalItemMixin {

    private static Class<?> swVehicleClass;
    private static boolean swChecked;

    private static boolean isSwVehicle(Entity e) {
        if (!swChecked) {
            swChecked = true;
            try {
                swVehicleClass = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            } catch (Exception ignored) {}
        }
        return swVehicleClass != null && swVehicleClass.isInstance(e);
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, remap = true)
    private void onUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        int netId = stack.getOrDefault(BDDataComponents.NET_ID_DATA, -1);
        if (netId < 0) return;

        Entity vehicle = findVehicle(player);
        if (vehicle == null) return;

        cir.setReturnValue(InteractionResultHolder.sidedSuccess(stack, level.isClientSide()));
        if (!level.isClientSide()) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net != null) VehicleNetStorage.bindVehicle(vehicle.getUUID(), netId);
        }
    }

    private static Entity findVehicle(Player player) {
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 to = from.add(look.scale(8));
        AABB box = new AABB(from, to).inflate(2);
        for (Entity e : player.level().getEntities(player, box, e -> {
            if (e == player) return false;
            return isSwVehicle(e);
        })) {
            var hit = e.getBoundingBox().clip(from, to).orElse(null);
            if (hit != null) return e;
        }
        return null;
    }
}
