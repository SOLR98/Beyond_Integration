package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.compat.EmiBdCompat;
import com.solr98.beyondintegration.compat.bd.BdTerminalActions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * BD 终端批量操作请求包（客户端 → 服务端）。
 *
 * <p>承载 EmiLink 移植过来的四类 BD 终端操作，实际执行全部交给 {@link BdTerminalActions}。</p>
 *
 * <p><b>刻意不含</b> "从物品管理器取物"：EMI 物品管理器侧的取物不在本次同步范围内。</p>
 */
public class EmiBdActionPacket {

    // ── 动作码 ──
    /** 背包主区（9..35）中与 {@code stack} 相同的物品批量存入网络。 */
    public static final int ACTION_TRANSFER_MAIN_TO_NET = 0;
    /** 清空合成网格并优先返还网络。 */
    public static final int ACTION_CLEAN_CRAFT_TO_NETWORK = 1;
    /** 从网络提取一组 {@code stack} 到玩家背包（BD 网络槽 Shift+点击）。 */
    public static final int ACTION_EXTRACT_ONE = 2;
    /** 整个背包（0..35）中与 {@code stack} 相同的物品批量存入网络。 */
    public static final int ACTION_TRANSFER_ALL_TO_NET = 3;
    /** 连续合成结果槽（BD 合成界面 Space+点击结果槽）。 */
    public static final int ACTION_BATCH_CRAFT = 4;
    /** 从网络提取所有与 {@code stack} 相同的物品到玩家背包（BD 网络槽 Space+点击）。 */
    public static final int ACTION_TRANSFER_NET_TO_INV = 5;
    /** 快捷栏（0..8）中与 {@code stack} 相同的物品批量存入网络。 */
    public static final int ACTION_TRANSFER_HOTBAR_TO_NET = 6;

    private final ItemStack stack;
    private final int action;

    public EmiBdActionPacket(ItemStack stack, int action) {
        this.stack = stack == null ? ItemStack.EMPTY : stack;
        this.action = action;
    }

    public static void encode(EmiBdActionPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
        buf.writeVarInt(msg.action);
    }

    public static EmiBdActionPacket decode(FriendlyByteBuf buf) {
        return new EmiBdActionPacket(buf.readItem(), buf.readVarInt());
    }

    public static void handle(EmiBdActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 冲突门控：存在 EmiLink 时本模组的 BD 终端操作整体让路，服务端同样拒绝执行。
            // 注意这里只能用 "是否冲突" 判定，不能用 EmiBdCompat.enabled()：
            // EMI 是纯客户端模组，专用服务端上 ModList 里根本没有 emi，
            // 用 enabled() 会让专用服务端把全部 BD 终端操作静默丢弃。
            if (EmiBdCompat.conflictDetected()) return;
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack target = msg.stack;
            switch (msg.action) {
                // 1) 从网络提取物品
                case ACTION_EXTRACT_ONE -> {
                    if (!target.isEmpty()) BdTerminalActions.extractOneStack(player, target);
                }
                case ACTION_TRANSFER_NET_TO_INV -> {
                    if (!target.isEmpty()) BdTerminalActions.extractAllMatching(player, target);
                }
                // 2) 终端 ↔ 背包批量转移
                case ACTION_TRANSFER_MAIN_TO_NET -> {
                    if (!target.isEmpty()) BdTerminalActions.depositMatching(player, target, BdTerminalActions.MODE_MAIN);
                }
                case ACTION_TRANSFER_HOTBAR_TO_NET -> {
                    if (!target.isEmpty()) BdTerminalActions.depositMatching(player, target, BdTerminalActions.MODE_HOTBAR);
                }
                case ACTION_TRANSFER_ALL_TO_NET -> {
                    if (!target.isEmpty()) BdTerminalActions.depositMatching(player, target, BdTerminalActions.MODE_ALL);
                }
                // 3) 合成结果批量合成
                case ACTION_BATCH_CRAFT -> BdTerminalActions.batchCraft(player, BdTerminalActions.MAX_BATCH_CRAFT);
                // 4) 合成网格清理并返还网络
                case ACTION_CLEAN_CRAFT_TO_NETWORK -> BdTerminalActions.cleanCraftGridToNetwork(player);
                default -> { }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
