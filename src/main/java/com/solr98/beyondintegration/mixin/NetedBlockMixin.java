package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.solr98.beyondintegration.feature.bind.NetworkBindingRegistry;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.block.NetedBlock", remap = false)
public class NetedBlockMixin {

    private static final ThreadLocal<UUID> beyond$pendingPlayer = new ThreadLocal<>();

    @Inject(method = "setPlacedBy", at = @At("HEAD"))
    private void beyond$onSetPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (placer instanceof Player player) {
            beyond$pendingPlayer.set(player.getUUID());
        }
    }

    @Inject(method = "setPlacedBy", at = @At("RETURN"))
    private void beyond$afterSetPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        UUID playerUuid = beyond$pendingPlayer.get();
        if (playerUuid != null && level.getBlockEntity(pos) instanceof NetedBlockEntity be) {
            int netId = be.getNetId();
            if (netId >= 0 && BindingTokenManager.getInstance() != null) {
                BindingTokenManager.getOrCreateToken(netId, playerUuid);
                if (CommandConfig.enableAuditLog()) {
                    String bName = state.getBlock().getName().getString();
                    NetworkBindingRegistry.recordBlockBind(netId, pos, placer.getName().getString(), playerUuid, bName);
                }
            }
        }
        beyond$pendingPlayer.remove();
    }

    @Inject(method = "use", at = @At("HEAD"))
    private void beyond$onUse(BlockState state, Level level, BlockPos pos, Player player,
                              InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (!level.isClientSide() && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            beyond$pendingPlayer.set(player.getUUID());
        }
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void beyond$afterUse(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        UUID playerUuid = beyond$pendingPlayer.get();
        if (playerUuid != null && level.getBlockEntity(pos) instanceof NetedBlockEntity be) {
            int netId = be.getNetId();
            if (netId >= 0 && BindingTokenManager.getInstance() != null) {
                BindingTokenManager.getOrCreateToken(netId, playerUuid);
                if (CommandConfig.enableAuditLog()) {
                    String bName = state.getBlock().getName().getString();
                    NetworkBindingRegistry.recordBlockBind(netId, pos, player.getName().getString(), playerUuid, bName);
                }
            } else if (netId < 0 && CommandConfig.enableAuditLog()) {
                NetworkBindingRegistry.removeBlockBind(netId, pos);
            }
        }
        beyond$pendingPlayer.remove();
    }
}
