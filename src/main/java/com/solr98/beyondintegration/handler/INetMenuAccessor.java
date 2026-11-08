package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

/**
 * 网络菜单访问器接口。
 * 由 Mixin 注入到 BD 的菜单（Menu）类，用于获取菜单当前绑定的维度网络。
 */
public interface INetMenuAccessor {
    /** 获取菜单绑定的 DimensionsNet。 */
    DimensionsNet getBoundNet();
}
