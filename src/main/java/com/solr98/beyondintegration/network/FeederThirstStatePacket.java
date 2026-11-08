package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.feeder.FeederThirstSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：当前网络喂食器的口渴/饱食阈值（打开界面时 / 设置变更后下发）。
 * <p>设置存在<b>手持物品的 NBT</b> 上，客户端手里那份是本地副本、不会自动更新，
 * 所以用本包把权威值推给客户端，供 BdPage 读取显示。
 */
public class FeederThirstStatePacket {

    /** 口渴阈值（口渴值 ≤ 该值补水） */
    private final int thirst;
    /** 水饱和阈值（水饱和 ≤ 该值补水） */
    private final int quenched;
    /** 饱食度阈值（饱食度 ≤ 该值喂食） */
    private final int food;
    /** 饥饿饱和度阈值（饱和度 ≤ 该值喂食） */
    private final int saturation;

    public FeederThirstStatePacket(int thirst, int quenched, int food, int saturation) {
        this.thirst = clamp(thirst);
        this.quenched = clamp(quenched);
        this.food = clamp(food);
        this.saturation = clamp(saturation);
    }

    /** 从喂食物品读取当前值构造。 */
    public static FeederThirstStatePacket of(ItemStack stack) {
        return new FeederThirstStatePacket(
                FeederThirstSettings.thirstThreshold(stack),
                FeederThirstSettings.quenchedThreshold(stack),
                FeederThirstSettings.foodThreshold(stack),
                FeederThirstSettings.saturationThreshold(stack));
    }

    public static void encode(FeederThirstStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.thirst);
        buf.writeVarInt(msg.quenched);
        buf.writeVarInt(msg.food);
        buf.writeVarInt(msg.saturation);
    }

    public static FeederThirstStatePacket decode(FriendlyByteBuf buf) {
        return new FeederThirstStatePacket(
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(FeederThirstStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                com.solr98.beyondintegration.client.FeederThirstClientState
                        .set(msg.thirst, msg.quenched, msg.food, msg.saturation);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public int thirst() {
        return thirst;
    }

    public int quenched() {
        return quenched;
    }

    public int food() {
        return food;
    }

    public int saturation() {
        return saturation;
    }

    /** 便捷：向某玩家推送其手持喂食器的当前设置。 */
    public static void sendTo(ServerPlayer player, ItemStack stack) {
        PacketHandler.sendToPlayer(player, of(stack));
    }

    private static int clamp(int value) {
        return Math.max(FeederThirstSettings.MIN, Math.min(FeederThirstSettings.MAX, value));
    }
}
