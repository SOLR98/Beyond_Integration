package com.solr98.beyondintegration.init;

import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 净化水系列流体（4 档纯度），仅在注册表注册流体本体，供 BD 网络存储/显示使用，
 * 供网络喂食器按 mB 换算给玩家补水（见 {@code FeederThirstHandler}）。
 * <p><b>不注册方块、不注册桶</b>：世界中不可放置、无对应桶物品；因此也无法靠烧炼桶来净化。
 * <p>纯度对应 Thirst 的 0=dirty / 1=slightly_dirty / 2=acceptable / 3=purified。
 * 原版 {@code minecraft:water} 不入此表，其纯度由配置 {@code feeder_thirst_vanilla_water_purity} 决定（默认 1 微脏）。
 * <p>贴图直接复用原版水纹理并乘以 tint，避免新增美术资源。
 */
public class ModFluids
{
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, BeyondIntegration.MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, BeyondIntegration.MODID);

    public static final List<FluidEntry> ALL = new ArrayList<>();

    public static final FluidEntry DIRTY_WATER = register("dirty_water", 0, 0xFF6E6E46);
    public static final FluidEntry SLIGHTLY_DIRTY_WATER = register("slightly_dirty_water", 1, 0xFF8FA86B);
    public static final FluidEntry ACCEPTABLE_WATER = register("acceptable_water", 2, 0xFF5FB0C8);
    public static final FluidEntry PURIFIED_WATER = register("purified_water", 3, 0xFF4F9BE0);

    public static FluidEntry register(String name, int purity, int argbTint)
    {
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name, () ->
                new FluidType(FluidType.Properties.create().density(1000).viscosity(1000)));

        final BaseFlowingFluid.Properties[] propsRef = new BaseFlowingFluid.Properties[1];

        DeferredHolder<Fluid, BaseFlowingFluid.Source> source =
                FLUIDS.register(name, () -> new BaseFlowingFluid.Source(propsRef[0]));
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing =
                FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(propsRef[0]));

        // 映射为原版水桶 / 水方块（世界中放置即普通水；网络里仍存本模组流体本体）
        propsRef[0] = new BaseFlowingFluid.Properties(type, source, flowing)
                .bucket(() -> Items.WATER_BUCKET)
                .block(() -> (net.minecraft.world.level.block.LiquidBlock) Blocks.WATER);

        FluidEntry entry = new FluidEntry(name, purity, type, source, flowing, argbTint);
        ALL.add(entry);
        return entry;
    }

    /** 该流体的纯度；非本模组水返回 -1（原版水的纯度由配置决定，不在此判断） */
    public static int purityOf(Fluid fluid)
    {
        for (FluidEntry e : ALL)
        {
            if (e.source().get() == fluid) return e.purity();
        }
        return -1;
    }

    public static boolean isOurWater(Fluid fluid)
    {
        return purityOf(fluid) >= 0;
    }

    /** 纯度（0 脏 / 1 微脏 / 2 可接受 / 3 纯净）对应的本模组流体；越界返回 null */
    public static Fluid tierFluid(int purity)
    {
        return switch (purity)
        {
            case 0 -> DIRTY_WATER.source().get();
            case 1 -> SLIGHTLY_DIRTY_WATER.source().get();
            case 2 -> ACCEPTABLE_WATER.source().get();
            case 3 -> PURIFIED_WATER.source().get();
            default -> null;
        };
    }

    public static void register(IEventBus modBus)
    {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
    }

    public record FluidEntry(
            String name,
            int purity,
            DeferredHolder<FluidType, FluidType> type,
            DeferredHolder<Fluid, ? extends Fluid> source,
            DeferredHolder<Fluid, ? extends Fluid> flowing,
            int argbTint)
    {
    }

    @EventBusSubscriber(modid = BeyondIntegration.MODID, value = Dist.CLIENT)
    public static final class ClientOnly
    {
        @SubscribeEvent
        public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event)
        {
            for (FluidEntry e : ALL)
            {
                final int tint = e.argbTint();
                event.registerFluidType(new IClientFluidTypeExtensions()
                {
                    @Override
                    public ResourceLocation getStillTexture()
                    {
                        return ResourceLocation.tryBuild("minecraft", "block/water_still");
                    }

                    @Override
                    public ResourceLocation getFlowingTexture()
                    {
                        return ResourceLocation.tryBuild("minecraft", "block/water_flow");
                    }

                    @Override
                    public int getTintColor()
                    {
                        return tint;
                    }
                }, e.type().get());
            }
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent evt)
        {
            evt.enqueueWork(() ->
            {
                for (FluidEntry e : ALL)
                {
                    net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(e.source().get(), net.minecraft.client.renderer.RenderType.translucent());
                    net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(e.flowing().get(), net.minecraft.client.renderer.RenderType.translucent());
                }
            });
        }
    }
}
