package com.solr98.beyondintegration.handler;

/**
 * 假玩家网络 ID 访问器接口。
 * 由 Mixin 注入到 BD 的假玩家（FakePlayer）类，
 * 用于记录假玩家执行指令时关联的 BD 维度网络 ID。
 */
public interface IFakePlayerNetId {
    /** 获取假玩家关联的 BD 网络 ID。 */
    int beyond$getNetId();
    /** 设置假玩家关联的 BD 网络 ID。 */
    void beyond$setNetId(int netId);
}
