package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.world.entity.Entity;

/**
 * 载具能量系统接口。
 * 由外部模组实现，将载具的能量系统与维度网络能源对接：
 * 支持查询/补充能量，以及获取载具实体与其绑定的网络。
 */
public interface IVehicleEnergySystem {

    /** 载具的最大能量值。 */
    float getMaxEnergy();

    /** 载具当前能量值。 */
    float getCurrentEnergy();

    /** 向载具补充能量，返回实际补充量。 */
    float addEnergy(float amount);

    /** 获取该能量系统所属的载具实体。 */
    Entity getVehicleEntity();

    /** 获取绑定的网络 ID（-1 表示未绑定）。 */
    int getBoundNetId();

    /** 根据绑定的网络 ID 获取维度网络，未绑定或已删除时返回 null。 */
    default DimensionsNet getBoundNetwork() {
        int id = getBoundNetId();
        return id >= 0 ? DimensionsNet.getNetFromId(id) : null;
    }
}
