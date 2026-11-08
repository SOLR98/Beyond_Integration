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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网络配件客户端缓存（与弹药实现同构）：镜像优先、推送/应答回退，客户端过滤可安装性。
 * <ul>
 *   <li>镜像可用：遍历主网络镜像聚合（含真实显示栈），按镜像版本缓存，不请求服务端；</li>
 *   <li>镜像不可用：服务端下发 id+count（{@code requestId} 严格匹配），显示栈按 ID 本地构造；</li>
 *   <li>可安装性：{@code allowAttachmentType}/{@code allowAttachment} 过滤。</li>
 * </ul>
 */
public final class NetAttachmentCache {

    /** 客户端条目：id + 数量 + 显示栈。 */
    public record Entry(ResourceLocation attachmentId, int count, ItemStack display) {}

    private static final Map<AttachmentType, List<Entry>> PUSH = new EnumMap<>(AttachmentType.class);
    private static final Map<AttachmentType, List<Entry>> MIRROR = new EnumMap<>(AttachmentType.class);
    private static int mirrorVersion = -1;

    private static final Map<AttachmentType, Long> LAST_REQUEST = new EnumMap<>(AttachmentType.class);
    private static final Map<AttachmentType, Long> NEXT_ID = new EnumMap<>(AttachmentType.class);
    private static final Map<AttachmentType, Long> PENDING = new EnumMap<>(AttachmentType.class);
    private static final long REQUEST_INTERVAL_MS = 1000L;

    private static volatile int netId = -1;
    private static volatile boolean hasNetwork = false;
    private static volatile int version = 0;

    private NetAttachmentCache() {}

    private static boolean mirrorAvailable() {
        return SharedNetData.available(NetDataType.ITEM);
    }

    /** 镜像数据版本（未启用镜像返回 -1），供界面定时刷新判断。 */
    public static int mirrorVersion() {
        return mirrorAvailable() ? PrimaryNetClientStorage.INSTANCE.version() : -1;
    }

    /** 显示栈按 ID 本地构造（推送/应答路径无 ItemStack）。 */
    private static ItemStack displayOf(ResourceLocation attachmentId) {
        try {
            return com.tacz.guns.api.item.builder.AttachmentItemBuilder.create()
                    .setId(attachmentId)
                    .build();
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    /** 按镜像版本重建镜像聚合（仅镜像可用时）。 */
    private static void ensureMirror() {
        int ver = PrimaryNetClientStorage.INSTANCE.version();
        if (ver == mirrorVersion) return;
        MIRROR.clear();
        if (mirrorAvailable()) {
            Map<String, Summary> dedup = new LinkedHashMap<>();
            for (KeyAmount ka : PrimaryNetClientStorage.INSTANCE.getStorage()) {
                if (!(ka.key() instanceof ItemStackKey ik)) continue;
                ItemStack stack = ik.getReadOnlyStack();
                IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
                if (attachment == null) continue;
                AttachmentType type = attachment.getType(stack);
                if (type == null || type == AttachmentType.NONE) continue;
                ResourceLocation id = attachment.getAttachmentId(stack);
                if (id == null) continue;
                long amount = ka.amount();
                if (amount <= 0L) continue;
                String key = type.name() + "|" + id;
                Summary previous = dedup.get(key);
                long sum = (previous == null ? 0L : (long) previous.count()) + amount;
                dedup.put(key, previous == null
                        ? new Summary(type, id, (int) Math.min(sum, Integer.MAX_VALUE), stack)
                        : new Summary(type, id, (int) Math.min(sum, Integer.MAX_VALUE), previous.display()));
            }
            for (Summary summary : dedup.values()) {
                MIRROR.computeIfAbsent(summary.type(), k -> new ArrayList<>())
                        .add(new Entry(summary.id(), summary.count(), summary.display()));
            }
            MIRROR.replaceAll((k, v) -> List.copyOf(v));
        }
        mirrorVersion = ver;
    }

    private record Summary(AttachmentType type, ResourceLocation id, int count, ItemStack display) {}

    /** 获取某类型当前应显示的配件（镜像优先；客户端过滤可安装性）。 */
    public static List<Entry> get(AttachmentType type, ItemStack gun) {
        if (type == null) return List.of();
        List<Entry> source;
        if (mirrorAvailable()) {
            ensureMirror();
            source = MIRROR.getOrDefault(type, List.of());
        } else {
            source = PUSH.getOrDefault(type, List.of());
        }
        if (source.isEmpty()) return source;
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || !iGun.allowAttachmentType(gun, type)) return List.of();
        List<Entry> visible = new ArrayList<>(source.size());
        for (Entry entry : source) {
            if (iGun.allowAttachment(gun, entry.display())) {
                visible.add(entry);
            }
        }
        return visible;
    }

    /** 接受服务端列表（请求响应需匹配在途 requestId；主动推送直接接受）。返回内容是否变化。 */
    public static boolean accept(int netId, boolean hasNetwork, AttachmentType type, long requestId,
                                 List<NetAttachmentsPacket.Entry> entries) {
        if (type == null) return false;
        if (requestId >= 0L) {
            Long pending = PENDING.get(type);
            if (pending == null || requestId != pending) return false;
            PENDING.remove(type);
        }
        List<Entry> converted = new ArrayList<>(entries.size());
        for (NetAttachmentsPacket.Entry entry : entries) {
            converted.add(new Entry(entry.attachmentId(), entry.count(), displayOf(entry.attachmentId())));
        }
        List<Entry> old = PUSH.get(type);
        boolean changed = !sameEntries(old, converted);
        PUSH.put(type, List.copyOf(converted));
        NetAttachmentCache.netId = netId;
        NetAttachmentCache.hasNetwork = hasNetwork;
        version++;
        return changed;
    }

    private static boolean sameEntries(List<Entry> a, List<Entry> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Entry x = a.get(i);
            Entry y = b.get(i);
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
