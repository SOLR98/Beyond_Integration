package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.xp.XpRepairSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 经验棒菜单 QuickData 扩展：随 BD 的快速数据包同步“自动修复”开关/模式/档位/修复范围。
 * <p>客户端在界面按钮点击后修改 {@code menuStack} NBT 并调用 {@code writeAndSendQuickData}，
 * 服务端 {@code readQuickDataTag} 应用回菜单物品。
 * <p>同时参与 {@code shouldSendQuickData} 检测（与喂食器/磁铁一致），
 * 任何未手动触发发送的修改也会被 BD 的周期检查发出去。
 */
@Mixin(value = com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu.class, remap = false)
public class XpExchangeMenuMixin {

    @Shadow
    @Final
    public ItemStack menuStack;

    @Unique
    private int beyondintegration$lastRepairSignature = Integer.MIN_VALUE;

    /** 写入 BI 修复设置（从菜单物品 NBT 读取）。 */
    @Inject(method = "writeQuickDataTag", at = @At("TAIL"))
    private void beyondintegration$writeRepairSettings(CompoundTag tag, CallbackInfo ci) {
        XpRepairSettings.writeToTag(menuStack, tag);
    }

    /** 读取 BI 修复设置（写回菜单物品 NBT）。 */
    @Inject(method = "readQuickDataTag", at = @At("TAIL"))
    private void beyondintegration$readRepairSettings(CompoundTag tag, CallbackInfo ci) {
        XpRepairSettings.readFromTag(menuStack, tag);
    }

    /** 修复设置变化时也触发一次快速数据发送。 */
    @Inject(method = "shouldSendQuickData", at = @At("RETURN"), cancellable = true)
    private void beyondintegration$trackRepairChanges(CallbackInfoReturnable<Boolean> cir) {
        int signature = beyondintegration$repairSignature();
        if (cir.getReturnValueZ() || signature != beyondintegration$lastRepairSignature) {
            beyondintegration$lastRepairSignature = signature;
            cir.setReturnValue(true);
        }
    }

    @Unique
    private int beyondintegration$repairSignature() {
        return java.util.Objects.hash(
                XpRepairSettings.isEnabled(menuStack),
                XpRepairSettings.getTierIndex(menuStack),
                XpRepairSettings.isArmorEnabled(menuStack),
                XpRepairSettings.isInventoryEnabled(menuStack),
                XpRepairSettings.isHandsEnabled(menuStack),
                XpRepairSettings.isCuriosEnabled(menuStack),
                XpRepairSettings.isMaidEnabled(menuStack));
    }
}
