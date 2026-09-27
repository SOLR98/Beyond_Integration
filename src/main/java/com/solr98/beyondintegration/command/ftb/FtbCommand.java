package com.solr98.beyondintegration.command.ftb;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * FTB 集成命令入口：{@code /bdftb scan} —— 手动触发一次 FTB 任务检测，
 * 把主网络内资源计入检测类任务（非消耗型物品任务）。
 * <p>
 * 独立于 /bdtools 根命令（本分支 /bdtools 要求 OP 2 级，普通玩家无法使用），
 * 本命令所有玩家可用且仅作用于自己。
 */
public final class FtbCommand {

    private FtbCommand() {}

    /** 注册 /bdftb 根命令 */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bdftb").then(scan()));
    }

    /** scan 子命令：手动触发一次任务检测 */
    private static LiteralArgumentBuilder<CommandSourceStack> scan() {
        return Commands.literal("scan").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (!ModList.get().isLoaded("ftbquests")) {
                ctx.getSource().sendFailure(CommandLang.component("ftb.no_ftb"));
                return 0;
            }
            if (!FtbIntegrationHelper.isEnabled()) {
                ctx.getSource().sendFailure(CommandLang.component("ftb.disabled"));
                return 0;
            }
            if (FtbIntegrationHelper.scanTasks(player)) {
                ctx.getSource().sendSuccess(() -> CommandLang.component("ftb.scan_done"), false);
                return 1;
            }
            ctx.getSource().sendFailure(CommandLang.component("ftb.scan_failed"));
            return 0;
        });
    }
}
