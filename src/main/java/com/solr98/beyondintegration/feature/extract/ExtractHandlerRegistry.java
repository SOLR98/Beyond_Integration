package com.solr98.beyondintegration.feature.extract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExtractHandlerRegistry {
    private static final List<IExtractHandler> handlers = new ArrayList<>();

    public static void register(IExtractHandler handler) {
        handlers.add(handler);
    }

    public static List<IExtractHandler> getHandlers() {
        return Collections.unmodifiableList(handlers);
    }
}
