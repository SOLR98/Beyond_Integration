package com.solr98.beyondintegration.api;

import net.minecraftforge.fml.ModList;

public interface IModIntegration {

    String modId();

    void doRegister();

    default boolean isLoaded() {
        return ModList.get().isLoaded(modId());
    }

    default void register() {
        if (!isLoaded()) return;
        doRegister();
    }
}
