package com.solr98.beyondintegration.jade;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.view.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Jade 流体显示覆盖提供器：以分页轮播方式展示方块流体存储内容。
 * 服务端收集各流体槽位数据，客户端每页最多显示 FLUIDS_PER_PAGE 种流体，
 * 超过时按 ROTATION_INTERVAL_MS 定时轮换页面（支持弱引用状态回收）。
 */
public enum BDFluidOverrideProvider implements IServerExtensionProvider<Object, CompoundTag>,
        IClientExtensionProvider<CompoundTag, FluidView> {

    INSTANCE;

    /** 本提供器的唯一标识。 */
    private static final ResourceLocation UID = ResourceLocation.tryParse("beyond_integration:network_fluids");
    /** 每页最多显示的流体种类数。 */
    private static final int FLUIDS_PER_PAGE = 4;
    /** 分页轮播间隔（毫秒）。 */
    private static final long ROTATION_INTERVAL_MS = 3000;

    // 弱键：方块卸载后条目自动回收，避免无界增长
    /** 各方块位置的流体分页轮播状态（键为方块坐标）。 */
    private static final Map<BlockPos, RotationState> rotationStates = Collections.synchronizedMap(new WeakHashMap<>());

    /** 服务端：读取方块流体能力，将非空槽位序列化到 ViewGroup 列表中，无流体时返回 null。 */
    @Override
    public List<ViewGroup<CompoundTag>> getGroups(ServerPlayer player, ServerLevel world, Object target, boolean showDetails) {
        if (!(target instanceof BlockEntity be)) return null;

        IFluidHandler handler = be.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
        if (handler == null) return null;

        List<CompoundTag> fluids = new ArrayList<>();
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack fs = handler.getFluidInTank(i);
            if (fs.isEmpty()) continue;
            int capacity = handler.getTankCapacity(i);
            if (capacity <= 0) continue;

            CompoundTag tag = new CompoundTag();
            tag.putString("fluid", ForgeRegistries.FLUIDS.getKey(fs.getFluid()).toString());
            tag.putLong("amount", fs.getAmount());
            tag.putLong("capacity", capacity);
            if (fs.getTag() != null) {
                tag.put("tag", fs.getTag().copy());
            }
            fluids.add(tag);
        }
        if (fluids.isEmpty()) return null;
        return List.of(new ViewGroup<>(fluids));
    }

    /** 客户端：按方块位置维护分页状态，定时轮换并构造当前页的 FluidView 列表。 */
    @Override
    public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
        if (groups == null || groups.isEmpty() || groups.get(0).views.isEmpty()) return List.of();

        List<CompoundTag> allTags = groups.get(0).views;
        int total = allTags.size();
        int totalPages = (total + FLUIDS_PER_PAGE - 1) / FLUIDS_PER_PAGE;

        BlockPos pos;
        if (accessor instanceof BlockAccessor ba) {
            pos = ba.getPosition();
        } else {
            return defaultPage(allTags);
        }

        long now = System.currentTimeMillis();
        RotationState state = rotationStates.computeIfAbsent(pos, k -> new RotationState());

        if (state.totalFluids != total) {
            state.page = 0;
            state.totalFluids = total;
            state.lastRotation = now;
        }

        if (totalPages > 1 && now - state.lastRotation >= ROTATION_INTERVAL_MS) {
            state.page = (state.page + 1) % totalPages;
            state.lastRotation = now;
        }

        int start = state.page * FLUIDS_PER_PAGE;
        int end = Math.min(start + FLUIDS_PER_PAGE, total);

        List<FluidView> views = new ArrayList<>();
        for (int i = start; i < end; i++) {
            FluidView fv = FluidView.readDefault(allTags.get(i));
            if (fv != null) views.add(fv);
        }

        return List.of(new ClientViewGroup<>(views));
    }

    /** 无法定位方块位置时的兜底：仅展示第一页流体。 */
    private static List<ClientViewGroup<FluidView>> defaultPage(List<CompoundTag> allTags) {
        List<FluidView> views = new ArrayList<>();
        int end = Math.min(FLUIDS_PER_PAGE, allTags.size());
        for (int i = 0; i < end; i++) {
            FluidView fv = FluidView.readDefault(allTags.get(i));
            if (fv != null) views.add(fv);
        }
        return List.of(new ClientViewGroup<>(views));
    }

    /** 返回本提供器的 UID。 */
    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /** 单个方块的流体分页轮播状态：当前页码、上次轮播时间及流体总数。 */
    private static class RotationState {
        int page = 0;
        long lastRotation = System.currentTimeMillis();
        int totalFluids = -1;
    }
}
