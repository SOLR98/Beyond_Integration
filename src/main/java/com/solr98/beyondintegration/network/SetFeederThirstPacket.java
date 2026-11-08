package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.feeder.FeederThirstSettings;
import com.wintercogs.beyonddimensions.common.menu.NetFeederMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：设置当前打开的网络喂食器的四项阈值
 * （口渴 / 水饱和 / 饱食度 / 饥饿饱和度）。
 * <p>服务端校验玩家当前菜单确实是 {@link NetFeederMenu}，写入手持物品 NBT 后
 * 通过 {@link FeederThirstStatePacket} 回推权威值（客户端不做乐观更新）。
 */
public class SetFeederThirstPacket {

    private final int thirst;
    private final int quenched;
    private final int food;
    private final int saturation;

    public SetFeederThirstPacket(int thirst, int quenched, int food, int saturation) {
        this.thirst = thirst;
        this.quenched = quenched;
        this.food = food;
        this.saturation = saturation;
    }

    public static void encode(SetFeederThirstPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.thirst);
        buf.writeVarInt(msg.quenched);
        buf.writeVarInt(msg.food);
        buf.writeVarInt(msg.saturation);
    }

    public static SetFeederThirstPacket decode(FriendlyByteBuf buf) {
        return new SetFeederThirstPacket(
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SetFeederThirstPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // 只允许在喂食器界面里改（避免任意玩家改远程/别人物品）
            if (!(player.containerMenu instanceof NetFeederMenu menu)) return;
            ItemStack stack = menu.menuStack;
            if (stack == null || stack.isEmpty()) return;

            // 「饱和维持模式」判定：后两项任一 > 0 时，前两项强制为上限（补给触发即补满，
            // 口渴/饱食度本就一直贴上限，允许调小只会造成误解），服务端做权威兜底。
            boolean holdMode = msg.quenched > 0 || msg.saturation > 0;
            int thirst = holdMode ? FeederThirstSettings.MAX : msg.thirst;
            int food = holdMode ? FeederThirstSettings.MAX : msg.food;

            FeederThirstSettings.setThirstThreshold(stack, thirst);
            FeederThirstSettings.setQuenchedThreshold(stack, msg.quenched);
            FeederThirstSettings.setFoodThreshold(stack, food);
            FeederThirstSettings.setSaturationThreshold(stack, msg.saturation);
            menu.broadcastChanges();
            // 回推权威值（客户端据此刷新页面）
            FeederThirstStatePacket.sendTo(player, stack);
        });
        ctx.get().setPacketHandled(true);
    }
}
