package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.feeder.FeederThirstMode;
import com.solr98.beyondintegration.feature.feeder.FeederThirstSettings;
import com.wintercogs.beyonddimensions.common.menu.NetFeederMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 喂食器菜单的补水档位同步：随 BD 的 QuickData 双向同步。
 * 仅在 {@code thirst} 已加载时由 {@code MixinPlugin} 应用。
 */
@Mixin(value = NetFeederMenu.class, remap = false)
public class FeederThirstMenuMixin
{
    @Shadow(remap = false) public ItemStack menuStack;

    @Unique private FeederThirstMode beyondintegration$lastMode;
    @Unique private boolean beyondintegration$lastRegen;

    @Inject(method = "shouldSendQuickData", at = @At("RETURN"), cancellable = true)
    private void beyondintegration$trackThirstChanges(CallbackInfoReturnable<Boolean> cir)
    {
        FeederThirstMode mode = FeederThirstSettings.getThirstMode(menuStack);
        boolean regen = FeederThirstSettings.isRegenMode(menuStack);
        if (cir.getReturnValueZ() || mode != beyondintegration$lastMode || regen != beyondintegration$lastRegen)
        {
            beyondintegration$lastMode = mode;
            beyondintegration$lastRegen = regen;
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "writeQuickDataTag", at = @At("TAIL"))
    private void beyondintegration$writeThirst(CompoundTag tag, CallbackInfo ci)
    {
        tag.putString(FeederThirstSettings.KEY_MODE, FeederThirstSettings.getThirstMode(menuStack).name());
        tag.putBoolean(FeederThirstSettings.KEY_REGEN, FeederThirstSettings.isRegenMode(menuStack));
    }

    @Inject(method = "readQuickDataTag", at = @At("TAIL"))
    private void beyondintegration$readThirst(CompoundTag tag, CallbackInfo ci)
    {
        if (tag.contains(FeederThirstSettings.KEY_MODE))
        {
            try
            {
                FeederThirstSettings.setThirstMode(menuStack,
                        FeederThirstMode.valueOf(tag.getString(FeederThirstSettings.KEY_MODE)));
            }
            catch (IllegalArgumentException ignored)
            {
                FeederThirstSettings.setThirstMode(menuStack, FeederThirstMode.NORMAL);
            }
        }
        if (tag.contains(FeederThirstSettings.KEY_REGEN))
        {
            FeederThirstSettings.setRegenMode(menuStack, tag.getBoolean(FeederThirstSettings.KEY_REGEN));
        }
    }
}
