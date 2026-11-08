package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.crafting.TaczCraftManager;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S：客户端请求在"主网络"中执行 TACZ 枪械工作台配方合成，
 * 服务端经 TaczCraftManager 校验材料与数量后合成，产物可入背包或直接存入网络。
 */
public class TaczCraftPacket {

    /** 要合成的配方 ID */
    private final ResourceLocation recipeId;
    /** 合成次数 */
    private final int count;
    /** true=产物直接存入网络，false=放入玩家背包 */
    private final boolean toNetwork;

    /** 便捷构造：默认合成 1 次、产物入背包 */
    public TaczCraftPacket(ResourceLocation recipeId) {
        this(recipeId, 1, false);
    }

    /** 便捷构造：指定合成次数，产物入背包 */
    public TaczCraftPacket(ResourceLocation recipeId, int count) {
        this(recipeId, count, false);
    }

    /** 完整构造：指定配方、次数与产物去向 */
    public TaczCraftPacket(ResourceLocation recipeId, int count, boolean toNetwork) {
        this.recipeId = recipeId;
        this.count = count;
        this.toNetwork = toNetwork;
    }

    ResourceLocation getRecipeId() { return recipeId; }
    int getCount() { return count; }
    boolean isToNetwork() { return toNetwork; }

    /** 编码：写入配方 ID、次数与产物去向标记 */
    public static void encode(TaczCraftPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.recipeId);
        buf.writeVarInt(msg.count);
        buf.writeBoolean(msg.toNetwork);
    }

    /** 解码：按编码顺序还原配方 ID、次数与去向 */
    public static TaczCraftPacket decode(FriendlyByteBuf buf) {
        return new TaczCraftPacket(buf.readResourceLocation(), buf.readVarInt(), buf.readBoolean());
    }

    /** 服务端处理：校验主网络存在后交由 TaczCraftManager 执行合成 */
    public static void handle(TaczCraftPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) {
                player.sendSystemMessage(Component.translatable("message.beyond_integration.no_network"));
                return;
            }

            TaczCraftManager.get().executeCraft(player, net, msg.recipeId, msg.count, msg.toNetwork);
        });
        ctx.get().setPacketHandled(true);
    }
}
