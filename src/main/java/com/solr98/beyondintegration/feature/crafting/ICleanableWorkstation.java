package com.solr98.beyondintegration.feature.crafting;

// 工作站清空接口：输入/结果槽按方向归还（toStorage=true→网络优先，false→背包优先）
/**
 * 可清理工作站接口：工作台菜单（铁砧/合成/切石/砂轮/锻造）实现，
 * 供 GUI 关闭/清空时按方向归还输入槽物品。
 */
public interface ICleanableWorkstation {
    // 按方向清空工作站槽位：true=网络优先，false=背包优先
    void cleanSlots(boolean toStorage);
}
