package com.solr98.beyondintegration.command.util;

import com.solr98.beyondintegration.command.CommandLang;
import net.minecraft.commands.CommandSourceStack;

/**
 * 网络权限工具类
 * 提供命令执行者的 OP 权限等级检查与无权限失败提示功能
 */
public final class NetworkPermission {

    /**
     * 私有构造：纯静态工具类，禁止实例化
     */
    private NetworkPermission() {}

    /**
     * 检查命令源是否达到指定 OP 权限等级
     */
    public static boolean isOpLevel(CommandSourceStack source, int level) {
        return source.hasPermission(level);
    }

    /**
     * 检查命令源是否为 OP（等级2）或单机主机
     */
    public static boolean isOpOrHost(CommandSourceStack source) {
        return source.hasPermission(2) || source.getServer().isSingleplayer();
    }

    /**
     * 获取网络命令所需的最低 OP 权限等级
     */
    public static int getRequiredOpLevel() {
        return 2;
    }

    /**
     * 向命令源发送"无权限"失败提示
     */
    public static void sendNoPermission(CommandSourceStack source) {
        source.sendFailure(net.minecraft.network.chat.Component.translatable(
                "commands.beyond_integration.no_permission"));
    }
}
