package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryArmNetMixin implements SentryNetIdAccessor {

    @Unique
    private int beyond$netId = -1;

    @Override
    public int getSentryNetId() {
        return beyond$netId;
    }

    @Override
    public void setSentryNetId(int netId) {
        this.beyond$netId = netId;
    }
}
