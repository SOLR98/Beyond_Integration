package com.solr98.beyondintegration.feature.netpathway;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.netconfig.NetedBlockConfigNbt;
import com.wintercogs.beyonddimensions.common.block.NetedBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 维度网络方块交互：手持与该方块对应的物品 <b>Shift+左键</b> → 把方块配置复制到手上物品（并取消挖掘）。
 * （网络通道右键打开过滤界面已改由 {@code NetPathwayBlockUseMixin} 在方块层消费。）
 */
@Mod.EventBusSubscriber(modid = BeyondIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NetPathwayInteractionHandler {

    private NetPathwayInteractionHandler() {}

    /** Shift+左键复制配置到手上对应物品；取消事件以阻止挖掘。 */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getEntity().isShiftKeyDown()) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        Block block = state.getBlock();
        if (!(block instanceof NetedBlock)) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty() || held.getItem() != block.asItem()) return;

        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (blockEntity == null) return;

        // 阻止挖掘（双端取消，避免破坏方块）
        event.setCanceled(true);
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        NetedBlockConfigNbt.applyToStack(held, blockEntity);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
