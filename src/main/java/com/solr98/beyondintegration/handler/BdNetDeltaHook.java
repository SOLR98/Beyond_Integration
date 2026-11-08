package com.solr98.beyondintegration.handler;

/**
 * 维度网络存储增量钩子（由 {@code DimensionsNetMixin} 实现）。
 * <p>
 * 仅用于「存档加载结束后补建存储增量订阅」这一延迟时机：加载期间订阅不安全，需等
 * {@code DimensionsNet.load} 最外层返回后由 {@link #beyond$ensureDeltaHook()} 重试。
 * <p>
 * SW 弹药本身已改为存散装弹药物品，本接口不再承载任何弹药数据。
 */
public interface BdNetDeltaHook {

    /** 延迟补建存储增量订阅（加载结束后安全时机；默认无操作）。 */
    default void beyond$ensureDeltaHook() {}
}
