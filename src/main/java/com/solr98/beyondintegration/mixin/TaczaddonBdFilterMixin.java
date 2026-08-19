package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.CommandConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 禁止 taczaddon 将 BD（beyonddimensions）网络方块识别为合成材料源。
 *
 * taczaddon 的 ContainerItemSource.detectBackend 用
 * getCapability(ITEM_HANDLER) 识别附近容器——BD 方块若暴露该 capability，
 * 整个网络会被当作"容器源"接入工作台（全量槽遍历 + 最大流分配，性能风险）。
 * 本 Mixin 在 detectBackend 的 getBlockEntity 处拦截：配置 blockBdContainerReader
 * 开启时，BD 方块返回 null（后续判定为无后端，源被跳过）。
 *
 * 未安装 taczaddon 时注入点不存在（MixinPlugin 条件过滤）。
 */
@Mixin(targets = "com.mafuyu404.taczaddon.init.crafting.ContainerItemSource", remap = false)
public abstract class TaczaddonBdFilterMixin {

    @Redirect(method = "detectBackend",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                       remap = true))
    private static BlockEntity beyond$blockBdSources(Level level, BlockPos pos) {
        if (CommandConfig.blockBdContainerReader()) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
            if (id != null && "beyonddimensions".equals(id.getNamespace())) {
                return null;
            }
        }
        return level.getBlockEntity(pos);
    }
}
