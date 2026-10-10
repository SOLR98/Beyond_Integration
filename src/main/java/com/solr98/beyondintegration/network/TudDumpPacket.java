package com.solr98.beyondintegration.network;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.compat.tud.TudAmmoCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Supplier;

/**
 * S2C：请求客户端把<b>本端</b>TUD 弹药映射列表打印到日志。
 * <p>由 {@code /bdtools tud dump} 触发，使一条命令即可同时得到服务端（命令输出 + 日志）
 * 与客户端（日志）的解析结果，便于对比并反馈 TUD 作者。
 */
public final class TudDumpPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    public TudDumpPacket() {}

    public static void encode(TudDumpPacket msg, FriendlyByteBuf buf) {}

    public static TudDumpPacket decode(FriendlyByteBuf buf) {
        return new TudDumpPacket();
    }

    public static void handle(TudDumpPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            List<String> lines = TudAmmoCompat.dumpAmmoMapping();
            LOGGER.info("[BI-TUD-Dump][client] {} entries", lines.size());
            for (String line : lines) {
                LOGGER.info("[BI-TUD-Dump][client] {}", line);
            }
        }));
        ctx.get().setPacketHandled(true);
    }
}
