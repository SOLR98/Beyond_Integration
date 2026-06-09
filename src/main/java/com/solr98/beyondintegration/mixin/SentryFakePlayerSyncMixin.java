package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryFakePlayerSyncMixin {

    @Inject(method = "fireGun", at = @At(value = "INVOKE",
            target = "Leuphy/upo/sentrymechanicalarm/util/SentryFakePlayer;sync(Lnet/minecraftforge/common/util/FakePlayer;Leuphy/upo/sentrymechanicalarm/content/SentryArmBlockEntity;FFLnet/minecraft/world/item/ItemStack;)V",
            shift = At.Shift.AFTER), remap = false)
    private void onAfterSync(CallbackInfo ci) {
        try {
            BlockEntity be = (BlockEntity) (Object) this;
            if (be.getLevel() == null || be.getLevel().isClientSide) return;

            int netId = -1;
            if (be instanceof SentryNetIdAccessor accessor) {
                netId = accessor.getSentryNetId();
            }
            if (netId < 0) return;

            Method getFake = be.getClass().getClassLoader()
                    .loadClass("euphy.upo.sentrymechanicalarm.util.SentryFakePlayer")
                    .getMethod("get", be.getClass());
            FakePlayer fp = (FakePlayer) getFake.invoke(null, be);
            if (fp == null) return;

            ItemStack terminal = new ItemStack(Items.STONE, 1);
            NetedItem.setNetId(terminal, netId);
            fp.getInventory().setItem(8, terminal);
        } catch (Exception ignored) {}
    }
}
