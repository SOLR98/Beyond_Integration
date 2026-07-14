package com.solr98.beyondintegration.mixin;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.ReloadKey;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@Mixin(value = ReloadKey.class, remap = false)
public class ReloadKeyMixin {

    @Redirect(method = "onReloadPress", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IGun;useInventoryAmmo(Lnet/minecraft/world/item/ItemStack;)Z", remap = false), remap = false)
    private static boolean allowReloadPress(IGun iGun, ItemStack stack) {
        return false;
    }

    @Inject(method = "autoReload", at = @At("HEAD"), cancellable = true)
    private static void onAutoReload(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (event.phase != TickEvent.Phase.START || event.side != LogicalSide.CLIENT) return;
        if (!KeyConfig.AUTO_RELOAD.get()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || player.tickCount % 5 != 0) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun)) return;

        if (iGun.useInventoryAmmo(stack)) {
            IClientPlayerGunOperator.fromLocalPlayer(player).reload();
            ci.cancel();
            return;
        }

        boolean flag = TimelessAPI.getCommonGunIndex(iGun.getGunId(stack))
                .map(gunIndex -> gunIndex.getGunData().getBolt() != Bolt.OPEN_BOLT)
                .orElse(false);
        int ammoCount = iGun.getCurrentAmmoCount(stack) + (iGun.hasBulletInBarrel(stack) && flag ? 1 : 0);
        if (ammoCount > 0) return;

        IClientPlayerGunOperator.fromLocalPlayer(player).reload();
        ci.cancel();
    }
}
