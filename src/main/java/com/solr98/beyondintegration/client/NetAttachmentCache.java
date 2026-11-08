package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.client.mirror.SharedNetData;
import com.solr98.beyondintegration.core.sync.NetDataType;
import com.solr98.beyondintegration.network.NetAttachmentsPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestNetAttachmentsPacket;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网络配件客户端缓存：按槽位类型保存列表，并「镜像优先、推送/应答回退」（与弹药实现同构）。
 * <ul>
 *   <li>镜像可用（{@code SharedNetData.available(ITEM)}）时，直接遍历主网络镜像聚合配件列表，
 *       不请求服务端、不等推送；按镜像版本缓存。</li>
 *   <li>镜像不可用时，使用服务端下发的列表（{@code requestId} 严格匹配，丢弃过期响应）。</li>
 *   <li>是否可安装由客户端按 tacz 原版口径过滤（{@code allowAttachmentType}/{@code allowAttachment}）。</li>
 * </ul>
 */
public final class NetAttachmentCache {

    private static final Map<AttachmentType, List<NetAttachmentsPacket.Entry>> PUSH = new EnumMap<>(AttachmentType.class);
    private static final Map<AttachmentType, List<NetAttachmentsPacket.Entry>> MIRROR = new EnumMap<>(AttachmentType.class);
    private static int mirrorVersion = -1;

    private static final Map<AttachmentType, Long> LAST_REQUEST = new EnumMap<>(AttachmentType.class);
    private static final Map<AttachmentType, Long> NEXT_ID = new EnumMap<>(AttachmentType.class);
    private static final Map<AttachmentType, Long> PENDING = new EnumMap<>(AttachmentType.class);
    private static final long REQUEST_INTERVAL_MS = 1000L;

    private static volatile int netId = -1;
    private static volatile boolean hasNetwork = false;
    private static volatile int version = 0;

    private NetAttachmentCache() {}

    /** 主网络镜像是否覆盖物品键（配件来源）。 */
    private static boolean mirrorAvailable() {
        return SharedNetData.available(NetDataType.ITEM);
    }

    /** 镜像数据版本（未启用镜像时返回 -1），供界面定时刷新判断是否需要重绘。 */
    public static int mirrorVersion() {
        return mirrorAvailable() ? PrimaryNetClientStorage.INSTANCE.version() : -1;
    }

    /** 按镜像版本重建镜像聚合（仅镜像可用时）。 */
    private static void ensureMirror() {
        int ver = PrimaryNetClientStorage.INSTANCE.version();
        if (ver == mirrorVersion) return;
        MIRROR.clear();
        Map<String, Summary> dedup = new LinkedHashMap<>();
        if (mirrorAvailable()) {
            for (KeyAmount ka : PrimaryNetClientStorage.INSTANCE.getStorage()) {
                if (!(ka.key() instanceof ItemStackKey ik)) continue;
                ItemStack stack = ik.getReadOnlyStack();
                IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
                if (attachment == null) continue;
                AttachmentType type = attachment.getType(stack);
                if (type == null || type == AttachmentType.NONE) continue;
                var id = attachment.getAttachmentId(stack);
                if (id == null) continue;
                long amount = ka.amount();
                if (amount <= 0L) continue;
                String key = type.name() + "|" + id;
                Summary previous = dedup.get(key);
                long sum = (previous == null ? 0L : (long) previous.count()) + amount;
                int count = (int) Math.min(sum, Integer.MAX_VALUE);
                dedup.put(key, previous == null
                        ? new Summary(type, id, count, stack)
                        : new Summary(type, id, count, previous.display()));
            }
            for (Summary summary : dedup.values()) {
                MIRROR.computeIfAbsent(summary.type(), k -> new ArrayList<>())
                        .add(new NetAttachmentsPacket.Entry(summary.id(), summary.count(), summary.display()));
            }
            MIRROR.replaceAll((k, v) -> List.copyOf(v));
        }
        mirrorVersion = ver;
    }

    /** 镜像去重中间结构 */
    private record Summary(AttachmentType type, net.minecraft.resources.ResourceLocation id,
                           int count, ItemStack display) {}

    /**
     * 获取某类型当前应显示的配件：镜像优先（客户端过滤可安装性），否则用服务端下发的列表。
     *
     * @param gun 当前主手枪（用于客户端过滤；可为空）
     */
    public static List<NetAttachmentsPacket.Entry> get(AttachmentType type, ItemStack gun) {
        if (type == null) return List.of();
        List<NetAttachmentsPacket.Entry> source;
        if (mirrorAvailable()) {
            ensureMirror();
            source = MIRROR.getOrDefault(type, List.of());
        } else {
            source = PUSH.getOrDefault(type, List.of());
        }
        if (source.isEmpty()) return source;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || !iGun.allowAttachmentType(gun, type)) return List.of();
        List<NetAttachmentsPacket.Entry> visible = new ArrayList<>(source.size());
        for (NetAttachmentsPacket.Entry entry : source) {
            if (iGun.allowAttachment(gun, entry.display())) {
                visible.add(entry);
            }
        }
        return visible;
    }

    /**
     * 接受服务端列表：请求响应必须匹配在途 requestId（丢弃过期/重复响应），主动推送（requestId &lt; 0）直接接受。
     *
     * @return true = 列表内容发生变化（显示层需要重建）
     */
    public static boolean accept(int netId, boolean hasNetwork, AttachmentType type, long requestId,
                                 List<NetAttachmentsPacket.Entry> entries) {
        if (type == null) return false;
        if (requestId >= 0L) {
            Long pending = PENDING.get(type);
            if (pending == null || requestId != pending) return false;
            PENDING.remove(type);
        }
        List<NetAttachmentsPacket.Entry> old = PUSH.get(type);
        boolean changed = !sameEntries(old, entries);
        PUSH.put(type, List.copyOf(entries));
        NetAttachmentCache.netId = netId;
        NetAttachmentCache.hasNetwork = hasNetwork;
        version++;
        return changed;
    }

    /** 列表内容比较（attachmentId + 数量序列），避免无变化时重建界面。 */
    private static boolean sameEntries(List<NetAttachmentsPacket.Entry> a, List<NetAttachmentsPacket.Entry> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            NetAttachmentsPacket.Entry x = a.get(i);
            NetAttachmentsPacket.Entry y = b.get(i);
            if (!x.attachmentId().equals(y.attachmentId()) || x.count() != y.count()) return false;
        }
        return true;
    }

    /** 请求某类型列表（镜像可用时不请求；同类型 1s 内不重复请求）。 */
    public static void request(AttachmentType type) {
        if (type == null || type == AttachmentType.NONE) return;
        if (mirrorAvailable()) return;
        long now = System.currentTimeMillis();
        Long last = LAST_REQUEST.get(type);
        if (last != null && now - last < REQUEST_INTERVAL_MS) return;
        LAST_REQUEST.put(type, now);
        long id = NEXT_ID.merge(type, 1L, Long::sum);
        PENDING.put(type, id);
        PacketHandler.sendToServer(new RequestNetAttachmentsPacket(type, id));
    }

    public static boolean hasNetwork() {
        return hasNetwork;
    }

    public static int netId() {
        return netId;
    }

    public static int version() {
        return version;
    }

    public static void clear() {
        PUSH.clear();
        MIRROR.clear();
        mirrorVersion = -1;
        LAST_REQUEST.clear();
        NEXT_ID.clear();
        PENDING.clear();
        netId = -1;
        hasNetwork = false;
        version++;
    }
}
