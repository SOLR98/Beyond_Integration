package com.solr98.beyondintegration.mixin;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.bind.BindingTokenManager;
import com.solr98.beyondintegration.feature.bind.NetworkBindingRegistry;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(targets = "com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity", remap = false)
public class NetedBlockEntityMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Unique
    private UUID beyond$bindingToken;

    @Unique
    private UUID beyond$ownerUuid;

    @Unique
    private boolean beyond$loggedInit = false;

    @Inject(method = {"m_142466_", "load"}, at = @At("RETURN"))
    private void beyond$onLoad(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("beyond$tokenMost") && tag.contains("beyond$tokenLeast")) {
            beyond$bindingToken = new UUID(tag.getLong("beyond$tokenMost"), tag.getLong("beyond$tokenLeast"));
        }
        if (tag.contains("beyond$ownerMost") && tag.contains("beyond$ownerLeast")) {
            beyond$ownerUuid = new UUID(tag.getLong("beyond$ownerMost"), tag.getLong("beyond$ownerLeast"));
        }
    }

    @Inject(method = {"m_183515_", "saveAdditional"}, at = @At("RETURN"))
    private void beyond$onSave(CompoundTag tag, CallbackInfo ci) {
        if (beyond$bindingToken != null) {
            tag.putLong("beyond$tokenMost", beyond$bindingToken.getMostSignificantBits());
            tag.putLong("beyond$tokenLeast", beyond$bindingToken.getLeastSignificantBits());
        }
        if (beyond$ownerUuid != null) {
            tag.putLong("beyond$ownerMost", beyond$ownerUuid.getMostSignificantBits());
            tag.putLong("beyond$ownerLeast", beyond$ownerUuid.getLeastSignificantBits());
        }
    }

    @Inject(method = "getNet", at = @At("RETURN"), cancellable = true)
    private void beyond$onGetNet(CallbackInfoReturnable<DimensionsNet> cir) {
        if (!CommandConfig.enableTokenSystem()) return;

        DimensionsNet net = cir.getReturnValue();
        if (net == null) return;
        if (BindingTokenManager.getInstance() == null) return;

        int netId = ((NetedBlockEntity) (Object) this).getNetId();

        if (beyond$bindingToken == null) {
            UUID token = BindingTokenManager.getOrCreateToken(netId, beyond$ownerUuid != null ? beyond$ownerUuid : UUID.randomUUID());
            beyond$bindingToken = token;
            return;
        }

        if (!BindingTokenManager.isTokenValid(netId, beyond$bindingToken)) {
            LOGGER.warn("[BE] Token rejected at {} netId={}, clearing binding", pos(), netId);
            cir.setReturnValue(null);
            ((NetedBlockEntity) (Object) this).setNetId(-1);
        }
    }

    @Inject(method = "setNetId", at = @At("RETURN"))
    private void beyond$onSetNetId(int id, CallbackInfo ci) {
        if (id < 0) {
            beyond$bindingToken = null;
            if (beyond$ownerUuid != null && CommandConfig.enableAuditLog()) {
                NetworkBindingRegistry.removeBlockBind(id, pos());
            }
            return;
        }
    }

    @Unique
    public UUID beyond$getToken() { return beyond$bindingToken; }
    @Unique
    public void beyond$setToken(UUID token) { this.beyond$bindingToken = token; }
    @Unique
    public UUID beyond$getOwner() { return beyond$ownerUuid; }
    @Unique
    public void beyond$setOwner(UUID uuid) { this.beyond$ownerUuid = uuid; }

    @Unique
    public void beyond$clearBinding() {
        beyond$bindingToken = null;
        beyond$ownerUuid = null;
        ((NetedBlockEntity) (Object) this).setNetId(-1);
    }

    @Unique
    private BlockPos pos() {
        try { return ((NetedBlockEntity)(Object)this).getBlockPos(); }
        catch (Exception e) { return BlockPos.ZERO; }
    }
}
