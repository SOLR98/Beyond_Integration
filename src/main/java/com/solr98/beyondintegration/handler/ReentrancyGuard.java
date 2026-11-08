package com.solr98.beyondintegration.handler;

import java.util.HashMap;
import java.util.Map;

/**
 * 插入拦截器的<b>线程局部重入计数</b>。
 * <p>BD 的 before-insert 拦截器若在自身逻辑里再次 {@code UnifiedStorage.insert(...)}，
 * 会再次进入整条拦截链（含自身），存在重入/递归风险。用本工具限制重入深度：
 * 达到上限即让该处理器放行，避免过深或环形递归。
 * <p>用法：
 * <pre>{@code
 * if (!ReentrancyGuard.enter("bucket", CommandConfig.handlerMaxReentrancy())) return pass(k);
 * try { ... } finally { ReentrancyGuard.exit("bucket"); }
 * }</pre>
 */
public final class ReentrancyGuard {

    private static final ThreadLocal<Map<String, Integer>> DEPTH =
            ThreadLocal.withInitial(HashMap::new);

    private ReentrancyGuard() {
    }

    /** 尝试进入：未达 {@code max} 则计数 +1 并返回 true；已达则返回 false（应放行）。 */
    public static boolean enter(String key, int max) {
        Map<String, Integer> m = DEPTH.get();
        int d = m.getOrDefault(key, 0);
        if (d >= max) return false;
        m.put(key, d + 1);
        return true;
    }

    /** 退出：计数 -1（与 {@link #enter} 成对，置于 finally）。 */
    public static void exit(String key) {
        Map<String, Integer> m = DEPTH.get();
        int d = m.getOrDefault(key, 0);
        if (d <= 1) {
            m.remove(key);
        } else {
            m.put(key, d - 1);
        }
    }
}
