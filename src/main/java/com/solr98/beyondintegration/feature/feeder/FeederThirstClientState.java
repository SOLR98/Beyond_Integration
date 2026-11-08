package com.solr98.beyondintegration.feature.feeder;

/** 客户端缓存的网络喂食器口渴/饱食阈值（服务端权威值，界面打开或设置变更时下发）。 */
public final class FeederThirstClientState {

    private static volatile int thirst = FeederThirstSettings.DEFAULT_THIRST;
    private static volatile int quenched = FeederThirstSettings.DEFAULT_QUENCHED;
    private static volatile int food = FeederThirstSettings.DEFAULT_FOOD;
    private static volatile int saturation = FeederThirstSettings.DEFAULT_SATURATION;

    private FeederThirstClientState() {
    }

    public static void set(int thirst, int quenched, int food, int saturation) {
        FeederThirstClientState.thirst = thirst;
        FeederThirstClientState.quenched = quenched;
        FeederThirstClientState.food = food;
        FeederThirstClientState.saturation = saturation;
    }

    public static int thirst() { return thirst; }
    public static int quenched() { return quenched; }
    public static int food() { return food; }
    public static int saturation() { return saturation; }

    public static void reset() {
        thirst = FeederThirstSettings.DEFAULT_THIRST;
        quenched = FeederThirstSettings.DEFAULT_QUENCHED;
        food = FeederThirstSettings.DEFAULT_FOOD;
        saturation = FeederThirstSettings.DEFAULT_SATURATION;
    }
}
