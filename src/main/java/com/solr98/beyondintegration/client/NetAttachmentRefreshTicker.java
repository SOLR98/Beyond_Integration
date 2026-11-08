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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.EnumMap;
import java.util.Map;

/**
 * 网络配件列的刷新触发器（客户端 tick，刷新完全由客户端执行）。
 * <p>改装界面打开期间每 20 tick：
 * <ul>
 *   <li>枪上配件变化（服务端安装后 NBT 同步）→ 重建界面 + 重算属性；</li>
 *   <li>镜像可用：镜像版本变化 → 重建（本地聚合，无请求）；</li>
 *   <li>镜像不可用：定时请求（1s 节流），回包内容变化时自动重建。</li>
 * </ul>
 */
public final class NetAttachmentRefreshTicker {

    /** 由 ClientSetup 手动注册（modLoader=kotlinforforge 下 @EventBusSubscriber 扫描不可靠）。 */
    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(NetAttachmentRefreshTicker.class);
    }

    private static final int CHECK_INTERVAL_TICKS = 20;

    private static int counter = 0;
    private static int lastMirrorVersion = -1;
    private static final Map<AttachmentType, ResourceLocation> lastAttachments = new EnumMap<>(AttachmentType.class);

    private NetAttachmentRefreshTicker() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
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

        if (mc.player != null && detectWeaponAttachmentChange(mc.player)) {
            screen.init();
            if (mc.level != null) {
                AttachmentPropertyManager.postChangeEvent(mc.player, mc.player.getMainHandItem());
            }
            return;
        }

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
        Minecraft mc = Minecraft.getInstance();
        if (gun.isEmpty() || !(gun.getItem() instanceof IGun iGun) || mc.level == null) {
            lastAttachments.clear();
            return false;
        }
        boolean changed = false;
        for (AttachmentType t : AttachmentType.values()) {
            if (t == AttachmentType.NONE) continue;
            ItemStack attachment = iGun.getAttachment(mc.level.registryAccess(), gun, t);
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
