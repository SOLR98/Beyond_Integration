package com.solr98.beyondintegration.feature.terminal;

import java.util.ArrayList;
import java.util.List;

/**
 * 终端绑定分发中心：按顺序把绑定上下文分发给已注册的目标（方块 / 实体各一组）。
 * <p>规则：<b>方块=左键</b>（{@code LeftClickBlock}）、<b>实体=右键</b>（{@code EntityInteract}）。
 */
public final class TerminalBindingHub {

    private static final List<TerminalBindTarget> BLOCK_TARGETS = new ArrayList<>();
    private static final List<TerminalBindTarget> ENTITY_TARGETS = new ArrayList<>();

    private TerminalBindingHub() {}

    /** 注册方块绑定目标（左键）。 */
    public static void registerBlock(TerminalBindTarget target) {
        if (target != null) BLOCK_TARGETS.add(target);
    }

    /** 注册实体绑定目标（右键）。 */
    public static void registerEntity(TerminalBindTarget target) {
        if (target != null) ENTITY_TARGETS.add(target);
    }

    /** 分发方块目标；返回是否已消费（调用方据此取消左键事件）。 */
    public static boolean dispatchBlock(TerminalBindContext ctx) {
        return dispatch(BLOCK_TARGETS, ctx);
    }

    /** 分发实体目标；返回是否已消费（调用方据此取消右键事件）。 */
    public static boolean dispatchEntity(TerminalBindContext ctx) {
        return dispatch(ENTITY_TARGETS, ctx);
    }

    private static boolean dispatch(List<TerminalBindTarget> targets, TerminalBindContext ctx) {
        for (TerminalBindTarget target : targets) {
            try {
                if (target.supports(ctx) && target.bind(ctx)) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}
