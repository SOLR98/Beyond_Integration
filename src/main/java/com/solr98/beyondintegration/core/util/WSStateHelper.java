package com.solr98.beyondintegration.core.util;

public class WSStateHelper {
    public static boolean pendingRestore = false;
    public static double mouseX, mouseY;
    public static int lineData;

    public static void clear() {
        pendingRestore = false;
        mouseX = 0;
        mouseY = 0;
        lineData = 0;
    }
}
