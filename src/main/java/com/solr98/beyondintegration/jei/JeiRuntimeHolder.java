package com.solr98.beyondintegration.jei;

import mezz.jei.api.runtime.IJeiRuntime;

/**
 * JEI 运行时实例持有器：由 {@code BDJeiPlugin.onRuntimeAvailable} 写入，
 * 供客户端通过公开 API（物品列表 / 书签覆盖层）查询鼠标下物品，避免注入 JEI 内部类。
 */
public final class JeiRuntimeHolder {

    private static volatile IJeiRuntime runtime;

    private JeiRuntimeHolder() {}

    /** 保存/清空 JEI 运行时（插件生命周期回调） */
    public static void set(IJeiRuntime value) {
        runtime = value;
    }

    /** 当前 JEI 运行时（未就绪返回 null） */
    public static IJeiRuntime get() {
        return runtime;
    }
}
