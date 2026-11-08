package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.xp.XpRepairSettings;
import com.solr98.beyondintegration.feature.xp.XpRepairTier;
import com.solr98.beyondintegration.feature.xp.XpRepairTiers;
import com.wintercogs.beyonddimensions.common.item.XpExchangeSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C：经验棒自动修复设置（打开界面时 / 设置变更后下发）。
 * <p>设置存在<b>经验棒物品的 NBT</b> 上，客户端手里那份是本地副本、不会自动更新，
 * 所以用本包把权威值推给客户端，供 BdPage 读取显示。
 * <p>档位列表与修复模式属于服务端配置（{@code xp_repair_tiers} / {@code xp_repair_mode} /
 * {@code xp_repair_absolute}），等级上限与给予方式同样（{@code xp_rod_enabled} /
 * {@code max_target_level} / {@code grant_mode}），客户端读不到，必须随包下发。
 */
public record XpRepairStatePacket(boolean repairAllowed, boolean enabled, int tierIndex,
                                  boolean armor, boolean inventory, boolean hands, boolean curios, boolean maid,
                                  boolean absoluteMode, int absolute, List<Tier> tiers,
                                  int targetLevel, int maxTargetLevel, boolean directGrant)
        implements CustomPacketPayload {

    /** 一个档位：名称 + 每次修复最大耐久的百分比 + 执行间隔（tick）。 */
    public record Tier(String name, int percent, int interval) {
    }

    public static final Type<XpRepairStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "xp_repair_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, XpRepairStatePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public XpRepairStatePacket decode(RegistryFriendlyByteBuf buf) {
                    boolean repairAllowed = buf.readBoolean();
                    boolean enabled = buf.readBoolean();
                    int tierIndex = buf.readVarInt();
                    boolean armor = buf.readBoolean();
                    boolean inventory = buf.readBoolean();
                    boolean hands = buf.readBoolean();
                    boolean curios = buf.readBoolean();
                    boolean maid = buf.readBoolean();
                    boolean absoluteMode = buf.readBoolean();
                    int absolute = buf.readVarInt();
                    int count = buf.readVarInt();
                    List<Tier> tiers = new ArrayList<>(Math.max(0, Math.min(count, 64)));
                    for (int i = 0; i < count; i++) {
                        tiers.add(new Tier(buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));
                    }
                    int targetLevel = buf.readVarInt();
                    int maxTargetLevel = buf.readVarInt();
                    boolean directGrant = buf.readBoolean();
                    return new XpRepairStatePacket(repairAllowed, enabled, tierIndex,
                            armor, inventory, hands, curios, maid, absoluteMode, absolute, tiers,
                            targetLevel, maxTargetLevel, directGrant);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, XpRepairStatePacket msg) {
                    buf.writeBoolean(msg.repairAllowed());
                    buf.writeBoolean(msg.enabled());
                    buf.writeVarInt(msg.tierIndex());
                    buf.writeBoolean(msg.armor());
                    buf.writeBoolean(msg.inventory());
                    buf.writeBoolean(msg.hands());
                    buf.writeBoolean(msg.curios());
                    buf.writeBoolean(msg.maid());
                    buf.writeBoolean(msg.absoluteMode());
                    buf.writeVarInt(msg.absolute());
                    buf.writeVarInt(msg.tiers().size());
                    for (Tier tier : msg.tiers()) {
                        buf.writeUtf(tier.name(), 64);
                        buf.writeVarInt(tier.percent());
                        buf.writeVarInt(tier.interval());
                    }
                    buf.writeVarInt(msg.targetLevel());
                    buf.writeVarInt(msg.maxTargetLevel());
                    buf.writeBoolean(msg.directGrant());
                }
            };

    /** 从经验棒物品与服务端配置构造。 */
    public static XpRepairStatePacket of(ItemStack rod) {
        List<Tier> tiers = new ArrayList<>();
        for (XpRepairTier tier : XpRepairTiers.all()) {
            tiers.add(new Tier(tier.name(), tier.percent(), tier.interval()));
        }
        boolean absoluteMode = CommandConfig.xpRepairMode() == CommandConfig.XpRepairAmountMode.ABSOLUTE;
        int max = maxTargetLevelValue();
        int target = Math.max(0, Math.min(XpExchangeSettings.getTargetLevel(rod), max));
        return new XpRepairStatePacket(
                CommandConfig.xpAutoRepairEnabled(),
                XpRepairSettings.isEnabled(rod),
                Math.max(0, XpRepairSettings.getTierIndex(rod)),
                XpRepairSettings.isArmorEnabled(rod),
                XpRepairSettings.isInventoryEnabled(rod),
                XpRepairSettings.isHandsEnabled(rod),
                XpRepairSettings.isCuriosEnabled(rod),
                XpRepairSettings.isMaidEnabled(rod),
                absoluteMode,
                Math.max(0, CommandConfig.xpAutoRepairAbsolute()),
                List.copyOf(tiers),
                target,
                max,
                CommandConfig.xpRodGrantMode() == CommandConfig.XpGrantMode.DIRECT);
    }

    /** 生效的目标等级上限：BI 增强开启时用配置值，否则用 BD 的固定上限。 */
    public static int maxTargetLevelValue() {
        return CommandConfig.xpRodTweaksEnabled()
                ? CommandConfig.xpRodMaxTargetLevel()
                : XpExchangeSettings.MAX_TARGET_LEVEL;
    }

    /** 给指定玩家下发（经验棒为当前打开菜单的那一件）。 */
    public static void sendTo(ServerPlayer player, ItemStack rod) {
        if (rod == null || rod.isEmpty()) return;
        PacketHandler.sendToPlayer(player, of(rod));
    }

    public static void handle(final XpRepairStatePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                com.solr98.beyondintegration.client.XpRepairClientState.set(packet);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
