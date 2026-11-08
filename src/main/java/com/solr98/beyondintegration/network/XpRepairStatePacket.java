package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.feature.xp.XpRepairSettings;
import com.solr98.beyondintegration.feature.xp.XpRepairTier;
import com.solr98.beyondintegration.feature.xp.XpRepairTiers;
import com.wintercogs.beyonddimensions.common.item.XpExchangeSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：经验棒自动修复设置（打开界面时 / 设置变更后下发）。
 * <p>设置存在<b>经验棒物品的 NBT</b> 上，客户端手里那份是本地副本、不会自动更新，
 * 所以用本包把权威值推给客户端，供 BdPage 读取显示。
 * <p>档位列表与修复模式属于服务端配置（{@code xp_repair_tiers} / {@code xp_repair_mode} /
 * {@code xp_repair_absolute}），等级上限与给予方式同样（{@code xp_rod_enabled} /
 * {@code max_target_level} / {@code grant_mode}），客户端读不到，必须随包下发。
 */
public class XpRepairStatePacket {

    /** 一个档位：名称 + 每次修复最大耐久的百分比 + 执行间隔（tick）。 */
    public record Tier(String name, int percent, int interval) {
    }

    /** 服务端配置是否允许使用自动修复（关闭时界面只读）。 */
    private final boolean repairAllowed;
    /** 该经验棒是否启用自动修复。 */
    private final boolean enabled;
    /** 档位索引（对应下发列表）。 */
    private final int tierIndex;
    private final boolean armor;
    private final boolean inventory;
    private final boolean hands;
    private final boolean curios;
    private final boolean maid;
    /** 修复量模式：{@code true}=固定值，{@code false}=最大耐久百分比。 */
    private final boolean absoluteMode;
    /** 固定值模式下的每次修复耐久。 */
    private final int absolute;
    /** 当前生效的档位列表（服务端配置解析结果）。 */
    private final List<Tier> tiers;
    /** 目标等级（该经验棒 keep 模式要维持的等级）。 */
    private final int targetLevel;
    /** 目标等级上限：BI 配置开启时用 {@code max_target_level}，否则为 BD 的 21863。 */
    private final int maxTargetLevel;
    /** 经验给予方式：{@code true}=直设等级（BI DIRECT），{@code false}=分批给予（BD BATCH）。 */
    private final boolean directGrant;

    public XpRepairStatePacket(boolean repairAllowed, boolean enabled, int tierIndex,
                               boolean armor, boolean inventory, boolean hands, boolean curios, boolean maid,
                               boolean absoluteMode, int absolute, List<Tier> tiers,
                               int targetLevel, int maxTargetLevel, boolean directGrant) {
        this.repairAllowed = repairAllowed;
        this.enabled = enabled;
        this.tierIndex = Math.max(0, tierIndex);
        this.armor = armor;
        this.inventory = inventory;
        this.hands = hands;
        this.curios = curios;
        this.maid = maid;
        this.absoluteMode = absoluteMode;
        this.absolute = Math.max(0, absolute);
        this.tiers = List.copyOf(tiers == null ? List.of() : tiers);
        this.maxTargetLevel = Math.max(0, maxTargetLevel);
        this.targetLevel = Math.max(0, Math.min(targetLevel, this.maxTargetLevel));
        this.directGrant = directGrant;
    }

    /** 从经验棒物品与服务端配置构造。 */
    public static XpRepairStatePacket of(ItemStack rod) {
        List<Tier> tiers = new ArrayList<>();
        for (XpRepairTier tier : XpRepairTiers.all()) {
            tiers.add(new Tier(tier.name(), tier.percent(), tier.interval()));
        }
        boolean absoluteMode = CommandConfig.xpRepairMode() == CommandConfig.XpRepairAmountMode.ABSOLUTE;
        return new XpRepairStatePacket(
                CommandConfig.xpAutoRepairEnabled(),
                XpRepairSettings.isEnabled(rod),
                XpRepairSettings.getTierIndex(rod),
                XpRepairSettings.isArmorEnabled(rod),
                XpRepairSettings.isInventoryEnabled(rod),
                XpRepairSettings.isHandsEnabled(rod),
                XpRepairSettings.isCuriosEnabled(rod),
                XpRepairSettings.isMaidEnabled(rod),
                absoluteMode,
                CommandConfig.xpAutoRepairAbsolute(),
                tiers,
                XpExchangeSettings.getTargetLevel(rod),
                maxTargetLevel(),
                CommandConfig.xpRodGrantMode() == CommandConfig.XpGrantMode.DIRECT);
    }

    /** 生效的目标等级上限：BI 增强开启时用配置值，否则用 BD 的固定上限。 */
    public static int maxTargetLevel() {
        return CommandConfig.xpRodTweaksEnabled()
                ? CommandConfig.xpRodMaxTargetLevel()
                : XpExchangeSettings.MAX_TARGET_LEVEL;
    }

    /** 给指定玩家下发（经验棒为当前打开菜单的那一件）。 */
    public static void sendTo(ServerPlayer player, ItemStack rod) {
        if (rod == null || rod.isEmpty()) return;
        PacketHandler.sendToPlayer(player, of(rod));
    }

    public boolean repairAllowed() {
        return repairAllowed;
    }

    public boolean enabled() {
        return enabled;
    }

    public int tierIndex() {
        return tierIndex;
    }

    public boolean armor() {
        return armor;
    }

    public boolean inventory() {
        return inventory;
    }

    public boolean hands() {
        return hands;
    }

    public boolean curios() {
        return curios;
    }

    public boolean maid() {
        return maid;
    }

    public boolean absoluteMode() {
        return absoluteMode;
    }

    public int absolute() {
        return absolute;
    }

    public List<Tier> tiers() {
        return tiers;
    }

    public int targetLevel() {
        return targetLevel;
    }

    public int maxTargetLevelValue() {
        return maxTargetLevel;
    }

    public boolean directGrant() {
        return directGrant;
    }

    public static void encode(XpRepairStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.repairAllowed);
        buf.writeBoolean(msg.enabled);
        buf.writeVarInt(msg.tierIndex);
        buf.writeBoolean(msg.armor);
        buf.writeBoolean(msg.inventory);
        buf.writeBoolean(msg.hands);
        buf.writeBoolean(msg.curios);
        buf.writeBoolean(msg.maid);
        buf.writeBoolean(msg.absoluteMode);
        buf.writeVarInt(msg.absolute);
        buf.writeVarInt(msg.tiers.size());
        for (Tier tier : msg.tiers) {
            buf.writeUtf(tier.name(), 64);
            buf.writeVarInt(tier.percent());
            buf.writeVarInt(tier.interval());
        }
        buf.writeVarInt(msg.targetLevel);
        buf.writeVarInt(msg.maxTargetLevel);
        buf.writeBoolean(msg.directGrant);
    }

    public static XpRepairStatePacket decode(FriendlyByteBuf buf) {
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

    public static void handle(XpRepairStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                com.solr98.beyondintegration.client.XpRepairClientState.set(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
