package com.solr98.beyondintegration.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S2C：下发给客户端的 TUD 弹药映射表（服务端权威）。
 * <p>TUD（tacz_unidict）的映射数据（{@code config/tacz_unidict/*.json}、TOML 类型映射）只存在于
 * 双端各自的本地配置，TUD 自身不做网络同步（网络包只有 {@code SwitchAmmoPacket}）。
 * 因此由 BI 把服务端解析好的结果下发，使客户端能复现"枪 → 当前弹药"的解析，
 * 从而重新启用主网络镜像（{@link com.solr98.beyondintegration.client.TaczAmmoCache}）。
 * <p>表结构：gunId → 弹药列表（顺序即 {@code GunData.availableAmmo} 顺序，客户端按枪 NBT
 * {@code TudCurrentAmmo} 索引取值；TOML 类型映射命中的枪为长度 1 的列表）。
 */
public class TudMappingSyncPacket {

    /** 单条弹药：真实弹药 ID + 是否为 {@code $} 物品弹药 */
    public record Ammo(ResourceLocation ammoId, boolean item) {}

    /** 单把枪的映射条目 */
    public record Entry(ResourceLocation gunId, List<Ammo> ammo) {}

    /** 映射表版本（内容哈希，客户端据此整表替换） */
    private final long version;
    /** 是否全量（true = 整表覆盖；false = 合并，配合 removed） */
    private final boolean full;
    private final List<Entry> entries;
    private final List<ResourceLocation> removed;

    public TudMappingSyncPacket(long version, boolean full, List<Entry> entries, List<ResourceLocation> removed) {
        this.version = version;
        this.full = full;
        this.entries = entries;
        this.removed = removed;
    }

    public static void encode(TudMappingSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarLong(msg.version);
        buf.writeBoolean(msg.full);
        buf.writeVarInt(msg.entries.size());
        for (Entry entry : msg.entries) {
            buf.writeResourceLocation(entry.gunId());
            buf.writeVarInt(entry.ammo().size());
            for (Ammo ammo : entry.ammo()) {
                buf.writeResourceLocation(ammo.ammoId());
                buf.writeBoolean(ammo.item());
            }
        }
        buf.writeVarInt(msg.removed.size());
        for (ResourceLocation id : msg.removed) {
            buf.writeResourceLocation(id);
        }
    }

    public static TudMappingSyncPacket decode(FriendlyByteBuf buf) {
        long version = buf.readVarLong();
        boolean full = buf.readBoolean();
        int entryCount = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            ResourceLocation gunId = buf.readResourceLocation();
            int ammoCount = buf.readVarInt();
            List<Ammo> ammo = new ArrayList<>(ammoCount);
            for (int j = 0; j < ammoCount; j++) {
                ammo.add(new Ammo(buf.readResourceLocation(), buf.readBoolean()));
            }
            entries.add(new Entry(gunId, ammo));
        }
        int removedCount = buf.readVarInt();
        List<ResourceLocation> removed = new ArrayList<>(removedCount);
        for (int i = 0; i < removedCount; i++) {
            removed.add(buf.readResourceLocation());
        }
        return new TudMappingSyncPacket(version, full, entries, removed);
    }

    public static void handle(TudMappingSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.solr98.beyondintegration.client.compat.TudMappingClient.apply(
                        msg.version, msg.full, msg.entries, msg.removed)));
        ctx.get().setPacketHandled(true);
    }

    /** 由服务端构建的条目列表生成内容哈希（顺序稳定：按 gunId 排序后计算）。 */
    public static long hashOf(List<Entry> entries) {
        long hash = 1125899906842597L;
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort((a, b) -> a.gunId().compareTo(b.gunId()));
        for (Entry entry : sorted) {
            hash = hash * 31L + entry.gunId().hashCode();
            for (Ammo ammo : entry.ammo()) {
                hash = hash * 31L + ammo.ammoId().hashCode();
                hash = hash * 31L + (ammo.item() ? 1 : 0);
            }
        }
        return hash;
    }
}
