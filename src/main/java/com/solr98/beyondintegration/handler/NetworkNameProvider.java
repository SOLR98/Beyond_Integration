package com.solr98.beyondintegration.handler;

/**
 * 规避编译时对 BD 0.7.16+ getCustomName() 的依赖。
 * 0.7.9 中 DimensionsNet 没有此方法，由本接口提供空默认实现；
 * 0.7.16+ 中类本身有该方法，覆盖接口默认方法。
 * 使用时：((NetworkNameProvider) net).getCustomName()
 */
public interface NetworkNameProvider {
    default String getCustomName() {
        return "";
    }
}
