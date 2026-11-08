package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.feature.enchant.EnchantMergeService;

import java.util.List;

/**
 * 「附魔合并」页的客户端缓存（由 {@code EnchantMergeStatePacket} 更新）。
 * <p>服务端扫描出的候选与网络 XP 余额；页面 snapshot 读这里，提交后服务端会再推一份。
 */
public final class EnchantMergeClientState {

    private static volatile List<EnchantMergeService.Candidate> candidates = List.of();
    private static volatile long xp = 0L;

    private EnchantMergeClientState() {
    }

    public static void set(List<EnchantMergeService.Candidate> list, long networkXp) {
        candidates = List.copyOf(list == null ? List.of() : list);
        xp = networkXp;
    }

    public static List<EnchantMergeService.Candidate> candidates() {
        return candidates;
    }

    public static long xp() {
        return xp;
    }

    public static void reset() {
        candidates = List.of();
        xp = 0L;
    }
}
