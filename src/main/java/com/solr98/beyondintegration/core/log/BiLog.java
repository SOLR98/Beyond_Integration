package com.solr98.beyondintegration.core.log;

import com.solr98.beyondintegration.CommandConfig;

/**
 * 日志文本的语言选择：与命令输出、配置注释用同一套判定。
 * <p>
 * 取当前配置的 {@code language.command_language}；配置尚未加载（客户端、启动早期）时
 * 回退到由系统语言信息决定的默认值（{@link CommandConfig#systemDefaultLanguage()}），
 * 因此中文系统上的日志默认中文，其余系统英文。
 * <p>
 * 用法：{@code LOGGER.info(BiLog.t("已注册 {} 项", "registered {} entries"), count);}
 * —— 文本在调用时选定，占位符仍由日志框架按 {@code {}} 填充（两种语言都要用 {@code {}}）。
 */
public final class BiLog {

    private BiLog() {
    }

    /** 当前日志语言是否为中文。 */
    public static boolean chinese() {
        try {
            return CommandConfig.getCommandLanguage() == CommandConfig.Language.ZH_CN;
        } catch (Throwable ignored) {
            // 配置尚未就绪（静态初始化早期）：按系统语言判定，且绝不因为打日志而抛异常
            return CommandConfig.systemDefaultLanguage() == CommandConfig.Language.ZH_CN;
        }
    }

    /** 按当前语言选择日志文本：中文环境取 {@code zh}，否则取 {@code en}。 */
    public static String t(String zh, String en) {
        return chinese() ? zh : en;
    }
}
