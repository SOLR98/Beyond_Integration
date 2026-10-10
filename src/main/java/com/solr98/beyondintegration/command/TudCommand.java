package com.solr98.beyondintegration.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.command.util.PermissionChecker;
import com.solr98.beyondintegration.compat.tud.TudAmmoCompat;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.TudDumpPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.List;

/**
 * {@code /bdtools tud ...}：TUD（Tacz-Unidict）兼容诊断（OP）。
 * <ul>
 *   <li>{@code dump}：<b>一条命令双方触发</b>——
 *       服务端打印本端 TUD 弹药映射（聊天预览 + 全量日志 {@code [BI-TUD-Dump][server]}），
 *       同时向执行者发 {@link TudDumpPacket} 让客户端打印本端映射到日志
 *       {@code [BI-TUD-Dump][client]}。便于对比双端并反馈作者。</li>
 * </ul>
 */
public final class TudCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHAT_PREVIEW = 100;

    private TudCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("tud")
                .then(Commands.literal("dump")
                        .executes(ctx -> execDump(ctx.getSource())));
    }

    private static int execDump(CommandSourceStack source) {
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        if (!TudAmmoCompat.isLoaded()) {
            source.sendFailure(Component.literal("TUD (tacz_unidict) not loaded"));
            return 0;
        }

        // 一条命令双方触发：通知客户端 dump 本端映射
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            PacketHandler.sendToPlayer(player, new TudDumpPacket());
        }

        List<String> lines = TudAmmoCompat.dumpAmmoMapping();
        source.sendSuccess(() -> Component.literal(
                "[BI-TUD] server-side mapping (" + lines.size()
                        + " entries); client dump sent to log"), false);
        int shown = 0;
        for (String line : lines) {
            LOGGER.info("[BI-TUD-Dump][server] {}", line);
            if (shown < CHAT_PREVIEW) {
                source.sendSuccess(() -> Component.literal(line), false);
                shown++;
            }
        }
        if (lines.size() > shown) {
            final int remaining = lines.size() - shown;
            source.sendSuccess(() -> Component.literal(
                    "... " + remaining + " more written to log [BI-TUD-Dump][server]"), false);
        }
        return lines.size();
    }
}
