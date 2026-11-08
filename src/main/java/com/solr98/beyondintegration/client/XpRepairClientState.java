package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.network.XpRepairStatePacket;

import java.util.List;

/**
 * 经验棒「自动修复」设置的客户端缓存（由 {@link XpRepairStatePacket} 更新）。
 * <p>设置存在经验棒物品 NBT 上，客户端持有的本地副本不会自动跟随服务端变化，
 * 因此由服务端推权威值过来；BdPage 的 snapshot 读这里的缓存。
 * <p>档位列表与修复模式来自服务端配置，只能由包下发。
 */
public final class XpRepairClientState {

    private static volatile boolean repairAllowed = true;
    private static volatile boolean enabled = true;
    private static volatile int tierIndex = 0;
    private static volatile boolean armor = true;
    private static volatile boolean inventory = true;
    private static volatile boolean hands = true;
    private static volatile boolean curios = true;
    private static volatile boolean maid = true;
    private static volatile boolean absoluteMode = false;
    private static volatile int absolute = 0;
    private static volatile List<XpRepairStatePacket.Tier> tiers = List.of();
    private static volatile int targetLevel = 0;
    private static volatile int maxTargetLevel = 21863;
    private static volatile boolean directGrant = false;

    private XpRepairClientState() {
    }

    public static void set(XpRepairStatePacket msg) {
        repairAllowed = msg.repairAllowed();
        enabled = msg.enabled();
        tierIndex = msg.tierIndex();
        armor = msg.armor();
        inventory = msg.inventory();
        hands = msg.hands();
        curios = msg.curios();
        maid = msg.maid();
        absoluteMode = msg.absoluteMode();
        absolute = msg.absolute();
        tiers = msg.tiers();
        targetLevel = msg.targetLevel();
        maxTargetLevel = msg.maxTargetLevel();
        directGrant = msg.directGrant();
    }

    public static boolean repairAllowed() {
        return repairAllowed;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static int tierIndex() {
        return tierIndex;
    }

    public static boolean armor() {
        return armor;
    }

    public static boolean inventory() {
        return inventory;
    }

    public static boolean hands() {
        return hands;
    }

    public static boolean curios() {
        return curios;
    }

    public static boolean maid() {
        return maid;
    }

    public static boolean absoluteMode() {
        return absoluteMode;
    }

    public static int absolute() {
        return absolute;
    }

    public static List<XpRepairStatePacket.Tier> tiers() {
        return tiers;
    }

    public static int targetLevel() {
        return targetLevel;
    }

    public static int maxTargetLevel() {
        return maxTargetLevel;
    }

    public static boolean directGrant() {
        return directGrant;
    }

    /** 打开经验棒界面时重置为默认，等待服务端下发（避免显示上一根经验棒的旧值）。 */
    public static void reset() {
        repairAllowed = true;
        enabled = true;
        tierIndex = 0;
        armor = true;
        inventory = true;
        hands = true;
        curios = true;
        maid = true;
        absoluteMode = false;
        absolute = 0;
        tiers = List.of();
        targetLevel = 0;
        maxTargetLevel = 21863;
        directGrant = false;
    }
}
