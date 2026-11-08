package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.feature.vehicle.VehicleNetCache;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 注入超级战争的 VehicleEntity：
 * 实现 INetCachedVehicle——为载具附加 VehicleNetCache（懒加载创建），
 * 并将绑定网络 ID 与弹药清单持久化到实体 NBT，实体移除时解除网络连接。
 */
@Mixin(value = com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity.class, remap = false)
public abstract class VehicleNetMixin implements INetCachedVehicle {

    /** NBT 中网络 ID 的键名 */
    private static final String BCE_NET_ID_KEY = "Net_id";
    /** NBT 中弹药清单的键名 */
    private static final String BCE_AMMO_LIST_KEY = "BCE_AmmoList";

    /** 载具网络缓存（懒加载） */
    @Unique
    private VehicleNetCache beyond$netCache;

    /** 获取（必要时创建）载具的网络缓存 */
    @Override
    public VehicleNetCache getNetCache() {
        VehicleNetCache cache = beyond$netCache;
        if (cache == null) {
            cache = new VehicleNetCache((VehicleEntity) (Object) this);
            beyond$netCache = cache;
        }
        return cache;
    }

    /** 读取实体 NBT 时恢复网络绑定（ID 与弹药清单） */
    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void onReadNbt(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(BCE_NET_ID_KEY)) {
            List<String> ammoList = new ArrayList<>();
            if (tag.contains(BCE_AMMO_LIST_KEY, net.minecraft.nbt.Tag.TAG_LIST)) {
                ListTag listTag = tag.getList(BCE_AMMO_LIST_KEY, net.minecraft.nbt.Tag.TAG_STRING);
                for (int i = 0; i < listTag.size(); i++) {
                    ammoList.add(listTag.getString(i));
                }
            }
            getNetCache().attach(tag.getInt(BCE_NET_ID_KEY), ammoList);
        }
    }

    /** 写入实体 NBT 时保存网络绑定（ID 与弹药清单） */
    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void onWriteNbt(CompoundTag tag, CallbackInfo ci) {
        VehicleNetCache cache = getNetCache();
        int netId = cache.getNetId();
        if (netId >= 0) {
            tag.putInt(BCE_NET_ID_KEY, netId);
            ListTag listTag = new ListTag();
            for (String key : cache.getAmmoList()) {
                listTag.add(StringTag.valueOf(key));
            }
            tag.put(BCE_AMMO_LIST_KEY, listTag);
        }
    }

    /** 实体移除时解除网络缓存（清理订阅与标记） */
    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(RemovalReason reason, CallbackInfo ci) {
        getNetCache().detach();
    }
}
