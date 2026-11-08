package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.feature.feeder.FeederThirstSettings;

/**
 * 网络喂食器「口渴/饱食阈值」的客户端缓存（由 {@code FeederThirstStatePacket} 更新）。
 * <p>喂食器设置存在手持物品 NBT 上，客户端持有的本地副本不会自动跟随服务端变化，
 * 因此由服务端推权威值过来；BdPage 的 snapshot 读这里的缓存。
 */
public final class FeederThirstClientState {

    private static volatile int thirst = FeederThirstSettings.DEFAULT_THIRST;
    private static volatile int quenched = FeederThirstSettings.DEFAULT_QUENCHED;
    private static volatile int food = FeederThirstSettings.DEFAULT_FOOD;
    private static volatile int saturation = FeederThirstSettings.DEFAULT_SATURATION;

    private FeederThirstClientState() {
    }

    public static void set(int thirstThreshold, int quenchedThreshold, int foodThreshold, int saturationThreshold) {
        thirst = clamp(thirstThreshold);
        quenched = clamp(quenchedThreshold);
        food = clamp(foodThreshold);
        saturation = clamp(saturationThreshold);
    }

    public static int thirst() {
        return thirst;
    }

    public static int quenched() {
        return quenched;
    }

    public static int food() {
        return food;
    }

    public static int saturation() {
        return saturation;
    }

    /** 打开喂食器界面时重置为默认，等待服务端下发（避免显示上一个喂食器的旧值）。 */
    public static void reset() {
        thirst = FeederThirstSettings.DEFAULT_THIRST;
        quenched = FeederThirstSettings.DEFAULT_QUENCHED;
        food = FeederThirstSettings.DEFAULT_FOOD;
        saturation = FeederThirstSettings.DEFAULT_SATURATION;
    }

    private static int clamp(int value) {
        return Math.max(FeederThirstSettings.MIN, Math.min(FeederThirstSettings.MAX, value));
    }
}
