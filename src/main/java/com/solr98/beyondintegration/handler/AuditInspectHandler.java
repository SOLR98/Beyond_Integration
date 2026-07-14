package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.AuditFormat;
import com.solr98.beyondintegration.feature.bind.AuditEntry;
import com.solr98.beyondintegration.feature.bind.BindingAuditLog;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public class AuditInspectHandler {

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getSide().isClient()) return;
        handleClick(event.getEntity(), event.getLevel(), event.getPos(), "BLOCK");
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide().isClient()) return;
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) return;
        handleClick(player, event.getLevel(), event.getPos(), "INTERACT");
    }

    private void handleClick(Player player, Level level, net.minecraft.core.BlockPos pos, String mode) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (!PlayerInspectData.isInspectMode(sp.getUUID())) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof NetedBlockEntity nbe)) return;

        int netId = nbe.getNetId();
        if (netId < 0) {
            sp.sendSystemMessage(Component.literal("§7" + CommandLang.get("inspect.no_network")));
            return;
        }

        BindingAuditLog auditLog = BindingAuditLog.getInstance();
        if (auditLog == null) {
            sp.sendSystemMessage(Component.literal("§c" + CommandLang.get("audit.log_not_available")));
            return;
        }

        List<AuditEntry> entries = auditLog.queryByNet(netId, 0, 5);
        int total = auditLog.countByNet(netId);
        sp.sendSystemMessage(Component.literal("§6=== " + CommandLang.get("inspect.title", String.valueOf(netId), pos.toShortString(), String.valueOf(total)) + " ==="));

        if (entries.isEmpty()) {
            sp.sendSystemMessage(Component.literal("§7" + CommandLang.get("inspect.no_records")));
            return;
        }

        for (AuditEntry e : entries) {
            String time = AuditFormat.time(e.timestamp());
            String status = AuditFormat.status(e.success());
            String tgt = AuditFormat.target(e.targetType(), e.targetInfo());
            sp.sendSystemMessage(Component.literal(
                    String.format("§7[%s] %s §e%s§r %s%s", time, status, AuditFormat.action(e.action()), AuditFormat.by(e.playerName()), tgt)));
        }

        String cmd = "/bdtools audit list " + netId;
        sp.sendSystemMessage(Component.literal("§7[§a" + CommandLang.get("inspect.click_here") + "§7] " + CommandLang.get("inspect.view_full"))
                .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))));
    }
}
