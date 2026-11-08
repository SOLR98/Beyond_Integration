package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.client.HudHintState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * HUD 提示包（S2C）：在快捷栏上方短暂显示一条可翻译文本（如网络图腾消耗提示）。
 * 仅传翻译键与两个长期参数，客户端本地构建文本。
 */
public class HudHintPacket {

    private final String translationKey;
    private final long arg1;
    private final long arg2;

    public HudHintPacket(String translationKey, long arg1, long arg2) {
        this.translationKey = translationKey;
        this.arg1 = arg1;
        this.arg2 = arg2;
    }

    public static void encode(HudHintPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.translationKey);
        buf.writeVarLong(msg.arg1);
        buf.writeVarLong(msg.arg2);
    }

    public static HudHintPacket decode(FriendlyByteBuf buf) {
        return new HudHintPacket(buf.readUtf(), buf.readVarLong(), buf.readVarLong());
    }

    public static void handle(HudHintPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> HudHintState.show(msg.translationKey, msg.arg1, msg.arg2)));
        ctx.get().setPacketHandled(true);
    }
}
