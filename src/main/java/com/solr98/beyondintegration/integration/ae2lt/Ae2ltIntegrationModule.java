package com.solr98.beyondintegration.integration.ae2lt;

import com.moakiee.ae2lt.api.AE2LTCapabilities;
import com.solr98.beyondintegration.integration.ae2lt.storage.LightningHandlerWrapper;
import com.solr98.beyondintegration.integration.ae2lt.storage.LightningStackKey;
import com.solr98.beyondintegration.integration.ae2lt.storage.LightningStackTypedHandler;
import com.solr98.beyondintegration.integration.ae2lt.storage.LightningUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.api.capability.helper.CapabilityHelper;
import com.wintercogs.beyonddimensions.api.capability.helper.wrapper.StackHandlerWrapperHelper;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import com.wintercogs.beyonddimensions.integration.BDIntegrationModule;
import com.wintercogs.beyonddimensions.integration.IIntegrationModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * AE2 Lightning Tech（ae2lt）与维度网络的集成模块（Forge 1.20.1）。
 * <p>
 * 由 Beyond Dimensions 的集成框架自动发现（扫描 {@link BDIntegrationModule} 注解），仅在 ae2lt 加载时实例化。
 * <p>
 * 本模块<b>不注册任何方块或物品</b>：仅把「高压 / 超高压闪电」登记为维度网络原生存储类型，
 * 并通过 {@link CapabilityHelper} 声明 AE2LT 的闪电能量能力。注册后，
 * BD 既有的「维度网络通道」（net_pathway）方块会自动向其他模组暴露 {@code ILightningEnergyHandler}，
 * 该能力由网络统一存储直接支撑（{@link LightningUnifiedStorageHandler}）。
 */
@BDIntegrationModule(modId = "ae2lt")
public class Ae2ltIntegrationModule implements IIntegrationModule {

    @Override
    public String modId() {
        return Ae2ltIds.MODID;
    }

    @Override
    public void onBootstrap(IEventBus modBus, IEventBus gameBus) {
        // 无需注册方块/物品。
    }

    @Override
    public void onCommonSetup(FMLCommonSetupEvent event) {
        // 登记闪电存储键类型（高压/超高压共用同一类型 ID）。
        StackKeyRegistry.registerType(LightningStackKey.HIGH_VOLTAGE);
        // 声明：闪电能量能力属于维度网络可操作能力（网络通道 / 接口据此暴露与识别）。
        CapabilityHelper.BlockCapabilityMap.put(LightningStackKey.ID, AE2LTCapabilities.LIGHTNING_ENERGY_BLOCK);
        // 网络统一存储 -> ILightningEnergyHandler（维度网络通道直接读写网络中的闪电）。
        CapabilityHelper.registerUSHandler(LightningStackKey.HIGH_VOLTAGE, LightningUnifiedStorageHandler::new);
        // 有序存储 -> ILightningEnergyHandler（网络接口内部槽位）。
        CapabilityHelper.registerStackTypedHandler(LightningStackKey.HIGH_VOLTAGE, LightningStackTypedHandler::new);
        // 能力包装：供网络接口向相邻 AE2LT 机器输出/抽出闪电。
        StackHandlerWrapperHelper.stackWrappers.put(LightningStackKey.ID, LightningHandlerWrapper::new);
        // 「维度 AE 存储磁盘」闪电桥接（仅 AE2 加载时，AEHelper 可用）。
        if (net.neoforged.fml.ModList.get().isLoaded("ae2")) {
            com.solr98.beyondintegration.integration.ae2lt.compat.Ae2ltAeStorageBridge.register();
        }
    }
}
