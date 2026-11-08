package com.solr98.beyondintegration.handler;

/**
 * 哨戒炮网络绑定访问器接口。
 * 由 Mixin 注入到哨戒炮（Sentry）实体，用于绑定/解绑其所属的 BD 维度网络。
 */
public interface SentryNetIdAccessor {
    /** 获取绑定的 BD 网络 ID（-1 表示未绑定）。 */
    int getSentryNetId();
    /** 设置绑定的 BD 网络 ID。 */
    void setSentryNetId(int netId);

    /** 默认实现：清除哨戒炮的网络绑定（重置为 -1）。 */
    default void clearSentryBinding() {
        setSentryNetId(-1);
    }
}
