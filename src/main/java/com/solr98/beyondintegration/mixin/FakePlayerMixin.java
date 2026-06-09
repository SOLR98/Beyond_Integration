package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.IFakePlayerNetId;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(FakePlayer.class)
public class FakePlayerMixin implements IFakePlayerNetId {
    @Unique private int beyond$netId = -1;

    @Override
    public int beyond$getNetId() { return beyond$netId; }

    @Override
    public void beyond$setNetId(int netId) { beyond$netId = netId; }
}
