package com.solr98.beyondintegration.feature.xp;

/**
 * 经验棒修复档位：名称 + 每次修复的最大耐久百分比 + 执行间隔（tick）。
 * <p>来源服务端配置 {@code xp_repair_tiers}，格式 {@code 名称:百分比:间隔}；
 * 固定值模式（ABSOLUTE）下百分比字段不参与，使用 {@code xp_repair_absolute}。
 */
public record XpRepairTier(String name, int percent, int interval) {
}
