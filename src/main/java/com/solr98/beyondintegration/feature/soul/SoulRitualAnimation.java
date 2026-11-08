package com.solr98.beyondintegration.feature.soul;

import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SoulBeamPacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 灵魂源激活仪式的收尾表现：仪式完成后在祭坛处发射一道<b>信标光柱</b>（复用原版
 * {@code BeaconRenderer.renderBeaconBeam}，与末地折跃门同款；由 {@link SoulBeamPacket} 通知客户端渲染），
 * 持续 2 秒，之后才正式激活网络灵魂源（{@link SoulArkActivation#activateByRitual}）。
 * <p>由 {@code SoulSourceRitual.finish} 触发；每 tick 由 {@link #onServerTick} 推进。仅 1.20.1。
 */
public final class SoulRitualAnimation {

    /** 光柱持续时长（tick）= 2 秒。 */
    private static final int DURATION = 40;
    /** 光柱最小高度（格）。 */
    private static final int BEAM_MIN_HEIGHT = 8;
    /** 青蓝色（RGB）：与灵魂主题一致。 */
    private static final float BEAM_R = 0.15F;
    private static final float BEAM_G = 0.75F;
    private static final float BEAM_B = 1.0F;
    /** 光柱内核/外晕半径（对齐原版信标：0.2 / 0.25）。 */
    private static final float BEAM_RADIUS = 0.2F;
    private static final float GLOW_RADIUS = 0.25F;
    /** 发包半径（格）。 */
    private static final double BROADCAST_RANGE = 96.0D;

    private static final List<Pending> PENDING = new ArrayList<>();

    private static final class Pending {
        final ServerLevel level;
        final BlockPos pos;
        final DimensionsNet net;
        final Player player;
        int elapsed = 0;

        Pending(ServerLevel level, BlockPos pos, DimensionsNet net, Player player) {
            this.level = level;
            this.pos = pos;
            this.net = net;
            this.player = player;
        }
    }

    private SoulRitualAnimation() {
    }

    /** 仪式完成时调用：发射信标光柱（2s），结束自动正式激活。 */
    public static void start(ServerLevel level, BlockPos pos, DimensionsNet net, Player player) {
        if (level == null || net == null) {
            SoulArkActivation.activateByRitual(net, player);
            return;
        }
        BlockPos beamPos = pos.immutable();
        broadcastBeam(level, beamPos);
        PENDING.add(new Pending(level, beamPos, net, player));
        level.playSound(null, beamPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
    }

    /** 服务端每 tick 推进计时；到时正式激活。 */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.level.getServer() == null) {
                it.remove();
                continue;
            }
            p.elapsed++;
            if (p.elapsed >= DURATION) {
                it.remove();
                p.level.playSound(null, p.pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.2F);
                SoulArkActivation.activateByRitual(p.net, p.player);
            }
        }
    }

    /** 向附近玩家发送光柱包（同维度、范围内）。光柱通天：高度 = 世界最大建筑高度 − 祭坛 Y。 */
    private static void broadcastBeam(ServerLevel level, BlockPos pos) {
        if (level.getServer() == null) return;
        int height = Math.max(BEAM_MIN_HEIGHT, level.getMaxBuildHeight() - pos.getY());
        SoulBeamPacket packet = new SoulBeamPacket(pos, height, DURATION,
                BEAM_R, BEAM_G, BEAM_B, BEAM_RADIUS, GLOW_RADIUS);
        double rangeSq = BROADCAST_RANGE * BROADCAST_RANGE;
        for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
            if (sp.level() != level) continue;
            if (sp.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= rangeSq) {
                PacketHandler.sendToPlayer(sp, packet);
            }
        }
    }
}
