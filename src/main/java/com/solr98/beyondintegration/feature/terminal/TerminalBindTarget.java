package com.solr98.beyondintegration.feature.terminal;

/**
 * 终端绑定目标：把"手持终端所绑定的网络"绑定到某个方块或实体。
 * <p>由 {@link TerminalBindingHub} 注册与分发；新增可绑定目标只需实现本接口并注册。
 */
public interface TerminalBindTarget {

    /** 本目标是否处理该上下文（方块或实体）。需双端安全。 */
    boolean supports(TerminalBindContext ctx);

    /**
     * 执行绑定。
     *
     * @return {@code true} 表示已消费（调用方将取消原事件，阻止挖掘/方块交互）。
     * 客户端应返回 {@code true}（仅用于取消），真正绑定在服务端执行。
     */
    boolean bind(TerminalBindContext ctx);
}
