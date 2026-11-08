package com.solr98.beyondintegration.mixin;

import com.Polarice3.Goety.common.blocks.entities.RitualBlockEntity;
import com.Polarice3.Goety.common.ritual.RitualRequirements;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.handler.RitualEnvSoulCharge;
import com.wintercogs.beyonddimensions.common.init.BDBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Goety 原版仪式「环境绕过」：当祭坛附近满足触发方块时，跳过环境类检测
 * （夜晚/维度/群系/高度/天气），保留结构方块要求。
 * <ul>
 *   <li>触发方块：半径内 {@code beyonddimensions:net_pathway} ≥ 配置数（默认 1）且
 *       {@code beyonddimensions:dimensional_connect_block} ≥ 配置数（默认 4）；</li>
 *   <li>拦截 {@code RitualRequirements.getProperStructure}：仅在<b>原版返回 false</b>
 *       （环境未满足）时重判，命中触发则返回 {@code getStructures(...)}（只忽略环境、保留结构）。</li>
 * </ul>
 * <p><b>双版本兼容</b>：Goety ≥ 2.5.40.0 的 {@code getProperStructure} 带 {@code Player} 参数
 * （5 参），&lt; 2.5.40.0 无（4 参）。两处注入均 {@code require = 0}（目标缺失即静默跳过，
 * 不致整类 Mixin 失败）；结构判定统一改用<b>两版都存在</b>的 3 参
 * {@code getStructures(String, BlockPos, Level)}。
 * <p>{@code MixinPlugin} 按 {@code goety} 门控；仅 1.20.1。
 */
@Mixin(targets = "com.Polarice3.Goety.common.ritual.RitualRequirements", remap = false)
public abstract class RitualRequirementsMixin {

    /** 新版（Goety ≥ 2.5.40.0）：带 Player 的 5 参重载。 */
    @Inject(method = "getProperStructure(Ljava/lang/String;Lnet/minecraft/world/entity/player/Player;"
            + "Lcom/Polarice3/Goety/common/blocks/entities/RitualBlockEntity;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;)Z",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void beyond$envBypass5(String craftType, Player player, RitualBlockEntity tileEntity,
                                          BlockPos pos, Level level, CallbackInfoReturnable<Boolean> cir) {
        beyond$apply(craftType, player, tileEntity, pos, level, cir);
    }

    /** 旧版（Goety < 2.5.40.0）：无 Player 的 4 参重载。 */
    @Inject(method = "getProperStructure(Ljava/lang/String;"
            + "Lcom/Polarice3/Goety/common/blocks/entities/RitualBlockEntity;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;)Z",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void beyond$envBypass4(String craftType, RitualBlockEntity tileEntity,
                                          BlockPos pos, Level level, CallbackInfoReturnable<Boolean> cir) {
        beyond$apply(craftType, null, tileEntity, pos, level, cir);
    }

    // ── 结构方块的网络来源：在整个结构检查前后记录/清理施法者上下文 ──

    @Inject(method = "getStructures(Ljava/lang/String;Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;)Z",
            at = @At("HEAD"), require = 0, remap = false)
    private static void beyond$structureBegin(String craftType, Player player, BlockPos pos, Level level,
                                              CallbackInfoReturnable<Boolean> cir) {
        com.solr98.beyondintegration.handler.RitualNetworkStructure.begin(player);
    }

    @Inject(method = "getStructures(Ljava/lang/String;Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;)Z",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void beyond$structureEnd(String craftType, Player player, BlockPos pos, Level level,
                                            CallbackInfoReturnable<Boolean> cir) {
        boolean ok = Boolean.TRUE.equals(cir.getReturnValue());
        // 本次检查过程中若有条件靠「假装满足」跳过（网络也没补上），整体必须判失败，
        // 并在 end() 里把完整缺少清单输出给玩家。
        if (ok && com.solr98.beyondintegration.handler.RitualNetworkStructure.hasMissing()) {
            ok = false;
            cir.setReturnValue(false);
        }
        com.solr98.beyondintegration.handler.RitualNetworkStructure.end(ok);
    }

    @Unique
    private static void beyond$apply(String craftType, Player player, RitualBlockEntity tileEntity,
                                     BlockPos pos, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return; // 原版已通过，无需绕过
        if (!CommandConfig.ritualEnvBypass()) return;

        // 前置条件（与「结构方块由网络提供」共用同一判定）
        if (!com.solr98.beyondintegration.handler.RitualTrigger.met(level, pos)) return;

        // 环境绕过额外魂消耗：按次计算，仅仪式起始阶段扣一次，无论仪式最终成败。
        // 魂不足则阻止绕过（保持原版环境检测结果）。
        long soulCost = CommandConfig.ritualEnvBypassSoulCost();
        if (soulCost > 0L && tileEntity instanceof RitualEnvSoulCharge charger
                && !charger.beyond$chargeEnvBypassSoul(soulCost)) {
            return;
        }

        // 触发：跳过环境检测，保留结构方块要求。
        // 拿不到施法者就跳过（结构方块的网络来源需要施法者）。
        if (player == null) return;
        boolean ok = RitualRequirements.getStructures(craftType, player, pos, level);
        // 环境绕过生效：给玩家一条网络提示（与结构补块共用节流）
        if (ok) com.solr98.beyondintegration.handler.RitualNetworkStructure.notifyEnvBypass(player);
        cir.setReturnValue(ok);
    }
}
