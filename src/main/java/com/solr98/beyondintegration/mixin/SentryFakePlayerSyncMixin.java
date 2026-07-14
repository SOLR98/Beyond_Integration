package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryFakePlayerSyncMixin {

    @Inject(method = "performInstantReload", at = @At("HEAD"))
    private void onBeforeReload(net.minecraftforge.common.util.FakePlayer fakePlayer,
                                 com.tacz.guns.api.item.IGun iGun,
                                 net.minecraft.world.item.ItemStack gunStack,
                                 CallbackInfoReturnable<Boolean> cir) {
        try {
            if (fakePlayer == null) return;

            int netId = -1;
            UUID storedToken = null;
            UUID ownerUuid = null;
            if (((Object) this) instanceof SentryNetIdAccessor accessor) {
                netId = accessor.getSentryNetId();
                storedToken = accessor.getSentryToken();
                ownerUuid = accessor.getSentryOwner();
            }
            if (netId < 0) return;

            // Ensure a token exists for this sentry
            if (storedToken == null && BindingTokenManager.getInstance() != null) {
                UUID owner = ownerUuid != null ? ownerUuid : UUID.randomUUID();
                storedToken = BindingTokenManager.getOrCreateToken(netId, owner);
                if (((Object) this) instanceof SentryNetIdAccessor accessor) {
                    accessor.setSentryToken(storedToken);
                }
            }

            if (CommandConfig.enableTokenSystem() && storedToken != null
                    && BindingTokenManager.getInstance() != null
                    && !BindingTokenManager.isTokenValid(netId, storedToken)) {
                if (((Object) this) instanceof SentryNetIdAccessor accessor) {
                    accessor.clearSentryBinding();
                }
                return;
            }

            // Only need netId on the terminal — the sentry's own token is already validated
            ItemStack terminal = new ItemStack(Items.STONE, 1);
            NetedItem.setNetId(terminal, netId);
            fakePlayer.getInventory().setItem(8, terminal);
        } catch (Exception ignored) {}
    }
}
