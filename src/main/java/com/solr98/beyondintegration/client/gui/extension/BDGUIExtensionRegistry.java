package com.solr98.beyondintegration.client.gui.extension;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BDGUIExtensionRegistry {

    private static final List<IDimensionsNetGUIExtension> EXTENSIONS = new ArrayList<>();
    private static boolean registered;

    public static void ensureRegistered() {
        if (registered) return;
        registered = true;
        register(new EnchantSeparationExtension());
        register(new AmmoPanelExtension());
        register(new ItemProtectExtension());
    }

    public static void register(IDimensionsNetGUIExtension ext) {
        EXTENSIONS.add(ext);
        EXTENSIONS.sort(Comparator.comparingInt(IDimensionsNetGUIExtension::priority));
    }

    public static List<IDimensionsNetGUIExtension> getExtensions() {
        return EXTENSIONS;
    }
}
