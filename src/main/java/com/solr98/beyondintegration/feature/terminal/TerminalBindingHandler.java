package com.solr98.beyondintegration.feature.terminal;

import com.solr98.beyondintegration.BeyondIntegration;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import com.wintercogs.beyonddimensions.common.item.NetTerminalItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 终端绑定事件入口：
 * <ul>
 *   <li><b>方块=左键</b>：{@link PlayerInteractEvent.LeftClickBlock}，手持终端左键方块 → 分发方块目标（并取消挖掘）；</li>
 *   <li><b>实体=右键</b>：{@link PlayerInteractEvent.EntityInteract}，手持终端右键实体 → 分发实体目标。</li>
 * </ul>
 * 判定：主手 + 手持携带 BD 网络 ID 的 {@link NetedItem}（终端/网络物品）。真正绑定在服务端执行。
 */
@Mod.EventBusSubscriber(modid = BeyondIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerminalBindingHandler {

    private TerminalBindingHandler() {}

    /** 方块：左键。 */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof NetTerminalItem)) return; // 仅便携网络终端（可代表维度网络）
        int netId = NetedItem.getNetId(stack);
        if (netId < 0) return;

        TerminalBindContext ctx = new TerminalBindContext(
                event.getEntity(), event.getLevel(), stack, netId, event.getPos(), null);
        if (TerminalBindingHub.dispatchBlock(ctx)) {
            event.setCanceled(true); // 阻止挖掘（双端）
        }
    }

    /** 实体：右键。 */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof NetTerminalItem)) return; // 仅便携网络终端（可代表维度网络）
        int netId = NetedItem.getNetId(stack);
        if (netId < 0) return;

        TerminalBindContext ctx = new TerminalBindContext(
                event.getEntity(), event.getLevel(), stack, netId, null, event.getTarget());
        if (TerminalBindingHub.dispatchEntity(ctx)) {
            event.setCanceled(true);
        }
    }
}
