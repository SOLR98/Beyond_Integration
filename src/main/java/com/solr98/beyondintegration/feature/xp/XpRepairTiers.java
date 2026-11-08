package com.solr98.beyondintegration.feature.xp;

import com.solr98.beyondintegration.CommandConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 经验棒修复档位列表（参考网络磁铁 {@code item_range_tiers} 的配置方式）。
 * <p>来源 {@code xp_repair_tiers}，格式 {@code 名称:百分比:间隔}；配置无效/为空时使用默认四档。
 * 档位数量不固定，界面按钮与修复逻辑按列表索引循环。
 */
public final class XpRepairTiers {

    /** 默认档位（与 BI 早期 LOW/NORMAL/HIGH/MAX 行为一致）。 */
    private static final List<XpRepairTier> DEFAULTS = List.of(
            new XpRepairTier("low", 5, 20),
            new XpRepairTier("normal", 10, 10),
            new XpRepairTier("high", 20, 5),
            new XpRepairTier("max", 50, 1)
    );

    private XpRepairTiers() {}

    /** 当前生效的档位列表（解析失败时回退默认）。 */
    public static List<XpRepairTier> all() {
        List<XpRepairTier> parsed = parse(CommandConfig.xpRepairTiers());
        return parsed.isEmpty() ? DEFAULTS : parsed;
    }

    /** 档位数量。 */
    public static int count() {
        return all().size();
    }

    /** 默认档位索引（normal，尽量取第 2 档）。 */
    public static int defaultIndex() {
        return Math.min(1, count() - 1);
    }

    /** 按索引取档位（越界收敛到有效范围）。 */
    public static XpRepairTier byIndex(int index) {
        List<XpRepairTier> list = all();
        int i = Math.max(0, Math.min(index, list.size() - 1));
        return list.get(i);
    }

    /** 解析 {@code 名称:百分比:间隔}。 */
    private static List<XpRepairTier> parse(List<? extends String> raw) {
        List<XpRepairTier> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            if (s == null) continue;
            String[] p = s.split(":");
            if (p.length != 3) continue;
            try {
                out.add(new XpRepairTier(
                        p[0].trim(),
                        Math.max(0, Math.min(100, Integer.parseInt(p[1].trim()))),
                        Math.max(1, Integer.parseInt(p[2].trim()))));
            } catch (NumberFormatException ignored) {
                // 跳过格式错误的条目
            }
        }
        return out;
    }
}
