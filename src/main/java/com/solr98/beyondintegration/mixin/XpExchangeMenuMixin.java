package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.xp.XpRepairSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 经验棒菜单 QuickData 扩展：随 BD 的快速数据包同步“自动修复”开关/模式/档位。
 * <p>客户端在界面按钮点击后修改 {@code menuStack} NBT 并调用 {@code writeAndSendQuickData}，
 * 服务端 {@code readQuickDataTag} 应用回菜单物品。
 */
@Mixin(value = com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu.class, remap = false)
public class XpExchangeMenuMixin {

    @Shadow
    @Final
    public ItemStack menuStack;

    /** 写入 BI 修复设置（从菜单物品 NBT 读取）。 */
    @Inject(method = "writeQuickDataTag", at = @At("TAIL"))
    private void beyondintegration$writeRepairSettings(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("beyond_integration:xp_repair_enabled", XpRepairSettings.isEnabled(menuStack));
        tag.putString("beyond_integration:xp_repair_mode", XpRepairSettings.getMode(menuStack).name());
        tag.putString("beyond_integration:xp_repair_tier", XpRepairSettings.getTier(menuStack).name());
    }

    /** 读取 BI 修复设置（写回菜单物品 NBT）。 */
    @Inject(method = "readQuickDataTag", at = @At("TAIL"))
    private void beyondintegration$readRepairSettings(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("beyond_integration:xp_repair_enabled")) {
            XpRepairSettings.setEnabled(menuStack, tag.getBoolean("beyond_integration:xp_repair_enabled"));
        }
        if (tag.contains("beyond_integration:xp_repair_mode")) {
            try {
                XpRepairSettings.setMode(menuStack, com.solr98.beyondintegration.CommandConfig.XpRepairAmountMode
                        .valueOf(tag.getString("beyond_integration:xp_repair_mode")));
            } catch (Throwable ignored) {
            }
        }
        if (tag.contains("beyond_integration:xp_repair_tier")) {
            try {
                XpRepairSettings.setTier(menuStack, com.solr98.beyondintegration.CommandConfig.XpRepairTier
                        .valueOf(tag.getString("beyond_integration:xp_repair_tier")));
            } catch (Throwable ignored) {
            }
        }
    }
}
