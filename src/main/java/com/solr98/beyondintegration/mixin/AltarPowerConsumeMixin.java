package com.solr98.beyondintegration.mixin;

import com.github.tartaricacid.touhoulittlemaid.capability.PowerCapability;
import com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe;
import com.github.tartaricacid.touhoulittlemaid.inventory.AltarRecipeInventory;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.maidpower.MaidPowerAccess;
import com.solr98.beyondintegration.handler.NetMenuResolver;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 祭坛合成直接消耗网络 P 点。
 * <p>TLM 祭坛在 {@code spawnResultEntity} 里先判断 {@code power.get() >= cost} 再扣本地 P 点；
 * 本地不足时即使网络有余额也会直接提示不足。本 Mixin 在该判断之前，
 * 把差额从网络取出并临时计入本地，使原逻辑的判断与扣除都成立——
 * 净效果是「本地够则只扣本地，本地不足的差额由网络承担」，P 点不会额外停留在玩家身上。
 * <p>网络余额也不足时归还已取部分并放行原逻辑（由 TLM 照常提示不足），不丢资源。
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.block.BlockAltar", remap = false)
public abstract class AltarPowerConsumeMixin {

    @Inject(method = "spawnResultEntity", at = @At("HEAD"), remap = false)
    private void beyond$payFromNetwork(Level level, Player player, PowerCapability power,
                                       AltarRecipe recipe, AltarRecipeInventory inventory,
                                       TileEntityAltar altar, CallbackInfo ci) {
        if (level.isClientSide) return;
        if (!CommandConfig.maidPowerEnabled()) return;
        if (recipe == null) return;

        float cost = recipe.getPowerCost();
        float local = power.get();
        if (local >= cost) return;

        if (!(player instanceof ServerPlayer serverPlayer)) return;
        DimensionsNet net = NetMenuResolver.resolve(serverPlayer);
        if (net == null) return;

        long need = MaidPowerAccess.toUnits(cost - local);
        if (need <= 0L) return;

        long got = MaidPowerAccess.extract(net, need);
        if (got <= 0L) return;
        if (got < need) {
            // 网络也不够：归还已取部分，交回原逻辑提示不足
            MaidPowerAccess.insert(net, got);
            return;
        }

        power.add(MaidPowerAccess.toPoints(got));
        net.setDirty();
    }
}
