package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.core.util.ItemTokenUtil;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryArmNetMixin implements SentryNetIdAccessor {

    @Unique
    private int beyond$netId = -1;
    @Unique
    private UUID beyond$token;
    @Unique
    private UUID beyond$owner;

    @Unique
    private static final java.lang.reflect.Field beyond$ammoBoxesField = beyond$initAmmoField();

    @Unique
    private static java.lang.reflect.Field beyond$initAmmoField() {
        try {
            Class<?> clazz = Class.forName("euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity");
            java.lang.reflect.Field f = clazz.getField("attachedAmmoBoxes");
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void beyond$onWrite(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        if (beyond$netId >= 0) compound.putInt("beyond$netId", beyond$netId);
        if (beyond$token != null) {
            compound.putLong("beyond$tokenMost", beyond$token.getMostSignificantBits());
            compound.putLong("beyond$tokenLeast", beyond$token.getLeastSignificantBits());
        }
        if (beyond$owner != null) {
            compound.putLong("beyond$ownerMost", beyond$owner.getMostSignificantBits());
            compound.putLong("beyond$ownerLeast", beyond$owner.getLeastSignificantBits());
        }
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void beyond$onRead(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        if (compound.contains("beyond$netId")) beyond$netId = compound.getInt("beyond$netId");
        if (compound.contains("beyond$tokenMost") && compound.contains("beyond$tokenLeast")) {
            beyond$token = new UUID(compound.getLong("beyond$tokenMost"), compound.getLong("beyond$tokenLeast"));
        }
        if (compound.contains("beyond$ownerMost") && compound.contains("beyond$ownerLeast")) {
            beyond$owner = new UUID(compound.getLong("beyond$ownerMost"), compound.getLong("beyond$ownerLeast"));
        }
        if (CommandConfig.enableTokenSystem() && beyond$netId >= 0 && beyond$token != null
                && !BindingTokenManager.isTokenValid(beyond$netId, beyond$token)) {
            beyond$clearAmmoTerminals();
            clearSentryBinding();
            return;
        }
        if (beyond$netId >= 0 && beyond$token == null) {
            beyond$token = BindingTokenManager.getOrCreateToken(beyond$netId, beyond$owner != null ? beyond$owner : UUID.randomUUID());
        }
    }

    @Unique
    private void beyond$clearAmmoTerminals() {
        try {
            if (beyond$ammoBoxesField == null) return;
            Object boxes = beyond$ammoBoxesField.get(this);
            if (!(boxes instanceof net.minecraft.core.NonNullList)) return;
            var list = (java.util.List<ItemStack>) boxes;
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = list.get(i);
                if (!stack.isEmpty() && NetedItem.getNetId(stack) >= 0) {
                    ItemTokenUtil.clearBinding(stack);
                    list.set(i, stack);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override public int getSentryNetId() { return beyond$netId; }

    @Override
    public void setSentryNetId(int netId) {
        this.beyond$netId = netId;
        if (netId >= 0) beyond$token = BindingTokenManager.getOrCreateToken(netId, beyond$owner != null ? beyond$owner : UUID.randomUUID());
        else beyond$token = null;
    }

    @Override public UUID getSentryToken() { return beyond$token; }
    @Override public void setSentryToken(UUID token) { this.beyond$token = token; }
    @Override public UUID getSentryOwner() { return beyond$owner; }
    @Override public void setSentryOwner(UUID uuid) { this.beyond$owner = uuid; }
}
