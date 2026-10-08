package com.solr98.beyondintegration.feature.magnet;

import com.solr98.beyondintegration.CommandConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络磁铁的档位列表：<b>物品</b>与<b>流体</b>各一套（固定 6 档，对应 BD 六种距离模式）。
 * <p>物品来源 {@code magnet.item_range_tiers}，流体来源 {@code magnet.fluid_range_tiers}，
 * 格式均为 {@code 名称:半径:间隔}（半径 {@code -1} = 整区块）。配置无效/不足时以默认补齐、超出截断。
 */
public final class MagnetTiers {

    /** 固定档位数量。 */
    public static final int COUNT = 6;

    /** 默认物品档位（半径:间隔）。 */
    private static final List<MagnetTier> ITEM_DEFAULTS = List.of(
            new MagnetTier("lowest", 2, 0),
            new MagnetTier("low", 3, 0),
            new MagnetTier("mid", 5, 2),
            new MagnetTier("high", 7, 5),
            new MagnetTier("highest", 10, 10),
            new MagnetTier("chunk", -1, 1200)
    );

    /** 默认流体档位（半径:间隔）。 */
    private static final List<MagnetTier> FLUID_DEFAULTS = List.of(
            new MagnetTier("lowest", 2, 0),
            new MagnetTier("low", 3, 0),
            new MagnetTier("mid", 5, 10),
            new MagnetTier("high", 7, 20),
            new MagnetTier("highest", 10, 50),
            new MagnetTier("chunk", -1, 1200)
    );

    private MagnetTiers() {}

    public static List<MagnetTier> items() {
        return resolve(CommandConfig.magnetItemRangeTiers(), ITEM_DEFAULTS);
    }

    public static List<MagnetTier> fluids() {
        return resolve(CommandConfig.magnetFluidRangeTiers(), FLUID_DEFAULTS);
    }

    public static MagnetTier itemByIndex(int index) {
        return pick(items(), index);
    }

    public static MagnetTier fluidByIndex(int index) {
        return pick(fluids(), index);
    }

    private static MagnetTier pick(List<MagnetTier> list, int index) {
        int i = Math.max(0, Math.min(index, COUNT - 1));
        return list.get(i);
    }

    private static List<MagnetTier> resolve(List<? extends String> raw, List<MagnetTier> defaults) {
        if (!CommandConfig.magnetCustomTiersEnabled()) {
            return defaults;
        }
        List<MagnetTier> parsed = parse(raw);
        if (parsed.isEmpty()) {
            return defaults;
        }
        List<MagnetTier> fixed = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            fixed.add(i < parsed.size() ? parsed.get(i) : defaults.get(i));
        }
        return fixed;
    }

    /** 解析 {@code 名称:半径:间隔}。 */
    private static List<MagnetTier> parse(List<? extends String> raw) {
        List<MagnetTier> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String[] p = s.split(":");
            if (p.length != 3) {
                continue;
            }
            try {
                out.add(new MagnetTier(
                        p[0].trim(),
                        Integer.parseInt(p[1].trim()),
                        Math.max(0, Integer.parseInt(p[2].trim()))));
            } catch (NumberFormatException ignored) {
                // 跳过格式错误的条目
            }
        }
        return out;
    }
}
