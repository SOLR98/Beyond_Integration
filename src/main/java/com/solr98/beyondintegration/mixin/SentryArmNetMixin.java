package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入哨戒机械臂的 SentryArmBlockEntity：
 * 为该方块实体添加网络 ID 字段（beyond$netId）并随 NBT 读写持久化，
 * 实现 SentryNetIdAccessor 接口供其他逻辑获取/设置哨戒绑定的维度网络。
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryArmNetMixin implements SentryNetIdAccessor {

    /** 哨戒绑定的维度网络 ID，-1 表示未绑定 */
    @Unique
    private int beyond$netId = -1;

    /** 保存 NBT 时写入网络 ID */
    @Inject(method = "write", at = @At("RETURN"))
    private void beyond$onWrite(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        if (beyond$netId >= 0) compound.putInt("beyond$netId", beyond$netId);
    }

    /** 读取 NBT 时恢复网络 ID */
    @Inject(method = "read", at = @At("RETURN"))
    private void beyond$onRead(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        if (compound.contains("beyond$netId")) beyond$netId = compound.getInt("beyond$netId");
    }

    @Override public int getSentryNetId() { return beyond$netId; }

    @Override
    public void setSentryNetId(int netId) {
        this.beyond$netId = netId;
    }
}
