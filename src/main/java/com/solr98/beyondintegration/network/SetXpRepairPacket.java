package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.xp.XpRepairSettings;
import com.wintercogs.beyonddimensions.common.item.XpExchangeSettings;
import com.wintercogs.beyonddimensions.common.menu.XpExchangeMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：设置当前打开的经验棒（自动修复开关 / 档位 / 五个修复范围 / 目标等级）。
 * <p>服务端校验玩家当前菜单确实是 {@link XpExchangeMenu}，写入经验棒物品 NBT 后
 * 通过 {@link XpRepairStatePacket} 回推权威值（客户端不做乐观更新，只做即时显示）。
 * <p>目标等级由 BI 直接写 {@link XpExchangeSettings#setTargetLevel}（内部经 BI 覆盖过的
 * {@code sanitizeTargetLevel} 收敛到配置上限），因此不受 BD 菜单里 21863 的固定校验限制
 * ——「自定义等级上限」配置在本路径生效。
 */
public record SetXpRepairPacket(boolean enabled, int tierIndex,
                                boolean armor, boolean inventory, boolean hands, boolean curios, boolean maid,
                                int targetLevel) implements CustomPacketPayload {

    public static final Type<SetXpRepairPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "set_xp_repair"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetXpRepairPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SetXpRepairPacket decode(RegistryFriendlyByteBuf buf) {
                    return new SetXpRepairPacket(buf.readBoolean(), buf.readVarInt(),
                            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                            buf.readBoolean(), buf.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SetXpRepairPacket msg) {
                    buf.writeBoolean(msg.enabled());
                    buf.writeVarInt(msg.tierIndex());
                    buf.writeBoolean(msg.armor());
                    buf.writeBoolean(msg.inventory());
                    buf.writeBoolean(msg.hands());
                    buf.writeBoolean(msg.curios());
                    buf.writeBoolean(msg.maid());
                    buf.writeVarInt(Math.max(0, msg.targetLevel()));
                }
            };

    public static void handle(final SetXpRepairPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // 只允许在经验棒界面里改（避免任意玩家改远程/别人物品）
            if (!(player.containerMenu instanceof XpExchangeMenu menu)) return;
            ItemStack rod = menu.menuStack;
            if (rod == null || rod.isEmpty()) return;

            XpRepairSettings.setEnabled(rod, packet.enabled());
            // setTierIndex 内部按当前档位列表收敛（越界忽略）
            XpRepairSettings.setTierIndex(rod, packet.tierIndex());
            XpRepairSettings.setArmorEnabled(rod, packet.armor());
            XpRepairSettings.setInventoryEnabled(rod, packet.inventory());
            XpRepairSettings.setHandsEnabled(rod, packet.hands());
            XpRepairSettings.setCuriosEnabled(rod, packet.curios());
            XpRepairSettings.setMaidEnabled(rod, packet.maid());
            // 目标等级：setTargetLevel 内部走 BI 覆盖的 sanitizeTargetLevel（收敛到配置上限）
            XpExchangeSettings.setTargetLevel(rod, packet.targetLevel());

            // 物品 NBT 变了：让客户端背包同步到新 NBT，并回推权威值
            player.getInventory().setChanged();
            menu.broadcastChanges();
            PacketDistributor.sendToPlayer(player, XpRepairStatePacket.of(rod));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
