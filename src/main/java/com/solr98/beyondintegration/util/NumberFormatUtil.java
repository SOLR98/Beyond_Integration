package com.solr98.beyondintegration.util;

import java.util.Locale;

/**
 * 数值显示统一格式化工具：
 * <ul>
 *   <li>{@link #grouped(long)}：千分位（如 1,234,567）；</li>
 *   <li>{@link #compact(long)}：<b>FTB 显示格式</b>（与 FTB Library {@code StringUtils.formatDouble(value, true)}
 *       基本一致，供 FTB 任务 tooltip 等场景使用）——≥1e9 显示 B（最高 B）、≥1e6 显示 M、
 *       ≥10000 显示 K，其余千分位；</li>
 *   <li>{@link #compactLarge(long)}：<b>国际单位制 SI 前缀</b>（K、M、G、T、P、E，最高 E，
 *       供 JEI 数量角标等可显示更大数量的场景使用）——≥1000 起转换。</li>
 * </ul>
 * 整数结果不带小数，否则保留最多两位小数（去尾零），并在舍入进位时自动升一级单位。
 */
public final class NumberFormatUtil {

    /** 10^(3i) 阈值：1、1e3、1e6、1e9、1e12、1e15、1e18 */
    private static final long[] POWERS = {
            1L, 1_000L, 1_000_000L, 1_000_000_000L,
            1_000_000_000_000L, 1_000_000_000_000_000L, 1_000_000_000_000_000_000L};
    /** SI 单位前缀（与 POWERS 对应，索引 0 为无单位） */
    private static final String[] SI_UNITS = {"", "K", "M", "G", "T", "P", "E"};

    private NumberFormatUtil() {}

    /** 千分位格式化：1234567 → "1,234,567" */
    public static String grouped(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    /** FTB 显示格式（最高 B）：9999 → "9,999"、12345 → "12.35K"、1500000000 → "1.5B" */
    public static String compact(long value) {
        if (value >= 1_000_000_000L) return unit(value / 1_000_000_000.0, "B");
        if (value >= 1_000_000L) return unit(value / 1_000_000.0, "M");
        if (value >= 10_000L) return unit(value / 1_000.0, "K");
        return grouped(value);
    }

    /** SI 单位转换（最高 E，JEI 角标等）：12345 → "12.35K"、1.5e9 → "1.5G"、1.5e12 → "1.5T" */
    public static String compactLarge(long value) {
        if (value < 1_000L) {
            return grouped(value);
        }
        int index = siIndex(value);
        return unit(value / (double) POWERS[index], SI_UNITS[index]);
    }

    /** SI 单位转换，数值向下取整（JEI 角标紧凑显示）：12345 → "12K"、1.5e9 → "1G"、999999 → "999K" */
    public static String compactLargeInt(long value) {
        if (value < 1_000L) {
            return grouped(value);
        }
        int index = siIndex(value);
        long scaled = (long) (value / (double) POWERS[index]);
        if (scaled < 1L) scaled = 1L;
        return scaled + SI_UNITS[index];
    }

    /** 选择 SI 单位索引（1=K … 6=E）：考虑两位小数舍入进位（如 999999999 → G） */
    private static int siIndex(long value) {
        int index = 1;
        while (index < 6 && value >= POWERS[index + 1]) {
            index++;
        }
        double scaled = value / (double) POWERS[index];
        if (index < 6 && round2(scaled) >= 1_000.0) {
            index++;
        }
        return index;
    }

    /** 数值 + 单位：整数不带小数，否则最多两位小数并去除尾零 */
    private static String unit(double value, String suffix) {
        if (value == Math.floor(value)) {
            return (long) value + suffix;
        }
        String text = String.format(Locale.US, "%.2f", value);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') end--;
        if (end > 0 && text.charAt(end - 1) == '.') end--;
        return text.substring(0, end) + suffix;
    }

    /** 保留两位小数的四舍五入（用于进位判定） */
    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
