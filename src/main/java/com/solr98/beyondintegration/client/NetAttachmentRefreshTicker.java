package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.BeyondIntegration;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.Map;

/**
 * 网络配件列的刷新触发器（客户端 tick，刷新完全由客户端执行）。
 * <p>改装界面打开期间每 20 tick 检查一次：
 * <ul>
 *   <li>枪上配件变化（服务端安装后物品 NBT 同步到达）→ 自行重建界面并重算属性；</li>
 *   <li>镜像可用：镜像版本变化 → 重建界面（本地聚合，无请求）；</li>
 *   <li>镜像不可用：定时请求（{@link NetAttachmentCache#request} 内部 1s 节流），
 *       服务端回包内容变化时由 {@code NetAttachmentsPacket.handle} 触发重建。</li>
 * </ul>
 * 界面关闭时复位状态。
 */
@Mod.EventBusSubscriber(modid = BeyondIntegration.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NetAttachmentRefreshTicker {

    private static final int CHECK_INTERVAL_TICKS = 20;

    private static int counter = 0;
    private static int lastMirrorVersion = -1;
    /** 上次观察到的枪上配件（类型 → attachmentId），用于检测服务端安装后的 NBT 同步。 */
    private static final Map<AttachmentType, ResourceLocation> lastAttachments = new EnumMap<>(AttachmentType.class);

    private NetAttachmentRefreshTicker() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof GunRefitScreen screen)) {
            counter = 0;
            lastMirrorVersion = -1;
            lastAttachments.clear();
            return;
        }
        AttachmentType type = RefitTransform.getCurrentTransformType();
        if (type == null || type == AttachmentType.NONE) return;

        counter++;
        if (counter % CHECK_INTERVAL_TICKS != 0) return;

        // 1) 枪上配件变化（安装/替换/外部来源）→ 重建界面 + 重算属性
        if (mc.player != null && detectWeaponAttachmentChange(mc.player)) {
            screen.init();
            AttachmentPropertyManager.postChangeEvent(mc.player, mc.player.getMainHandItem());
            return;
        }

        // 2) 网络列表变化：镜像看版本，非镜像走定时请求（回包内容变化时自动重建）
        int mirrorVersion = NetAttachmentCache.mirrorVersion();
        if (mirrorVersion >= 0) {
            if (mirrorVersion != lastMirrorVersion) {
                lastMirrorVersion = mirrorVersion;
                screen.init();
            }
        } else {
            NetAttachmentCache.request(type);
        }
    }

    /** 检测主手枪各槽位配件的 attachmentId 是否变化（首次只建立基线）。 */
    private static boolean detectWeaponAttachmentChange(LocalPlayer player) {
        ItemStack gun = player.getMainHandItem();
        if (gun.isEmpty() || !(gun.getItem() instanceof IGun iGun)) {
            lastAttachments.clear();
            return false;
        }
        boolean changed = false;
        for (AttachmentType t : AttachmentType.values()) {
            if (t == AttachmentType.NONE) continue;
            ItemStack attachment = iGun.getAttachment(gun, t);
            IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
            ResourceLocation id = iAttachment == null ? null : iAttachment.getAttachmentId(attachment);
            ResourceLocation previous = lastAttachments.put(t, id);
            if (!java.util.Objects.equals(previous, id)) {
                changed = true;
            }
        }
        return changed;
    }
}
