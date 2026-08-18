package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端初始化入口（MOD 事件总线，仅客户端加载）。
 * 监听 FMLClientSetupEvent 以执行客户端启动阶段的初始化逻辑。
 */
@Mod.EventBusSubscriber(modid = BeyondIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    /** 客户端启动回调（当前为预留钩子，暂无初始化任务） */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Client initialization if needed
    }
}
