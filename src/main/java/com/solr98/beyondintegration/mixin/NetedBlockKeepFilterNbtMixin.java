package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.netconfig.NetedBlockConfigNbt;
import com.wintercogs.beyonddimensions.common.block.NetedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 维度网络方块被破坏时保留配置 NBT（1.21.1）。
 */
@Mixin(Block.class)
public abstract class NetedBlockKeepFilterNbtMixin {

    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;",
            at = @At("RETURN"), remap = true, require = 0)
    private static void beyond$keepConfigNbt(BlockState state, ServerLevel level, BlockPos pos,
                                             BlockEntity blockEntity,
                                             CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!CommandConfig.netedBlockKeepNbt()) return;
        if (blockEntity == null || !(state.getBlock() instanceof NetedBlock)) return;

        List<ItemStack> drops = cir.getReturnValue();
        if (drops == null || drops.isEmpty()) return;

        CompoundTag data = NetedBlockConfigNbt.build(blockEntity, level.registryAccess());
        if (data.isEmpty()) return;

        for (ItemStack stack : drops) {
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)
                    || stack.getItem() != state.getBlock().asItem()) continue;
            BlockItem.setBlockEntityData(stack, blockEntity.getType(), data.copy());
        }
    }
}
