package com.solr98.beyondintegration.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端信标光柱渲染：接收 {@link com.solr98.beyondintegration.network.SoulBeamPacket}，
 * 在关卡渲染阶段用原版 {@link BeaconRenderer#renderBeaconBeam}（末地折跃门同款）渲染光柱。
 * 仅 1.20.1、仅客户端。
 */
@Mod.EventBusSubscriber(modid = BeyondIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SoulBeamClient {

    private record Beam(BlockPos start, int height, long startTime, int durationTicks,
                        float[] colors, float beamRadius, float glowRadius) {
    }

    /** 光柱保持满长的时长（tick）= 1 秒；之后开始向上收缩。 */
    private static final int RETRACT_START_TICKS = 20;

    private static final List<Beam> BEAMS = new ArrayList<>();

    private SoulBeamClient() {
    }

    /** 登记一道光柱（由包处理器调用）。 */
    public static void add(BlockPos start, int height, int durationTicks,
                           float r, float g, float b, float beamRadius, float glowRadius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BEAMS.add(new Beam(start.immutable(), height, mc.level.getGameTime(), durationTicks,
                new float[]{r, g, b}, beamRadius, glowRadius));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BEAMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = mc.level.getGameTime();
        BEAMS.removeIf(beam -> now > beam.startTime() + beam.durationTicks());
        if (BEAMS.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        for (Beam beam : BEAMS) {
            // 满长 1 秒后开始向上收缩：底端快速抬升，光柱整段被“发射”向上直至消失。
            float elapsed = (now - beam.startTime()) + partialTick;
            int offset = 0;
            int length = beam.height();
            int retractSpan = beam.durationTicks() - RETRACT_START_TICKS;
            if (elapsed > RETRACT_START_TICKS && retractSpan > 0) {
                float t = Mth.clamp((elapsed - RETRACT_START_TICKS) / (float) retractSpan, 0.0F, 1.0F);
                offset = Mth.floor(t * beam.height());
                length = beam.height() - offset;
            }
            if (length <= 0) continue;

            pose.pushPose();
            pose.translate(beam.start().getX() - cam.x,
                    beam.start().getY() - cam.y,
                    beam.start().getZ() - cam.z);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION,
                    partialTick, 1.0F, now, offset, length, beam.colors(),
                    beam.beamRadius(), beam.glowRadius());
            pose.popPose();
        }
        buffers.endBatch();
    }
}
