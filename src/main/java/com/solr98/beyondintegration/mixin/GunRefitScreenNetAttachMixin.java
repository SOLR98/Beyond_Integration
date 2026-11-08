package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.NetAttachmentCache;
import com.solr98.beyondintegration.client.gui.refit.NetAttachmentSlot;
import com.solr98.beyondintegration.network.NetRefitAttachmentPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * 在 tacz 改装界面（{@link GunRefitScreen}）里追加一列「网络配件」（独立列，不改动原背包列表）。
 * <p>只 {@code @Inject} {@code init} 的 TAIL，不 Redirect/Overwrite 任何 tacz/taczaddon 调用点。
 * <p>位置：初始为 1.2.0 适配器/变体偏移之前的兼容位置（背包列左侧 width-54）；
 * 按"实际存在的列数"逐列避让——适配器列用 {@code SlotAdapterHelper} 反射探测，
 * 变体列用「枪上该类型配件是否有变体」判断（都只在对应列真会绘制时才让位）。
 */
@Mixin(value = GunRefitScreen.class, remap = false)
public abstract class GunRefitScreenNetAttachMixin extends Screen {

    @Unique
    private static final int beyond$PAGE_SIZE = 8;

    /** 反射缓存的 SlotAdapterHelper#getAvailableSlotAdapters（1.2.0 才有；旧版为 null）。 */
    @Unique
    private static final java.lang.reflect.Method beyond$SLOT_ADAPTER_LOOKUP = beyond$findSlotAdapterLookup();

    /** 反射缓存的 TimelessAPI#getAllCommonAttachmentVariantIndex（旧编译期不存在）。 */
    @Unique
    private static final java.lang.reflect.Method beyond$VARIANT_LOOKUP = beyond$findVariantLookup();

    @Unique
    private AttachmentType beyond$lastType = null;
    @Unique
    private int beyond$netPage = 0;

    protected GunRefitScreenNetAttachMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void beyond$addNetAttachmentColumn(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        AttachmentType type = RefitTransform.getCurrentTransformType();
        if (type == null || type == AttachmentType.NONE) return;

        if (type != beyond$lastType) {
            beyond$lastType = type;
            beyond$netPage = 0;
        }
        NetAttachmentCache.request(type);

        ItemStack gun = mc.player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || !iGun.allowAttachmentType(gun, type)) return;
        var entries = NetAttachmentCache.get(type, gun);

        // 初始位置：1.2.0 适配器/变体偏移之前的兼容位置（背包列左侧 width-54）。
        // 按"实际存在的列数"逐列避让（适配器列 / 变体列各自独立与否）。
        boolean hasAdapters = beyond$hasSlotAdapters(gun, type);
        boolean hasVariants = beyond$hasVariants(mc, gun, type);
        int startX;
        if (hasAdapters) {
            startX = hasVariants
                    ? (this.width - 30 - 78 - 4) - 78 - 4 - 24   // 适配器列 + 变体列
                    : (this.width - 30 - 78 - 4) - 24;           // 仅适配器列
        } else if (hasVariants) {
            startX = (this.width - 30) + 18 - 78 - 24;           // 仅变体列
        } else {
            startX = this.width - 30 - 24;                       // 两列都没有
        }
        startX = Math.max(startX, 10);
        int startY = 50;
        int totalPage = entries.isEmpty() ? 0 : (entries.size() - 1) / beyond$PAGE_SIZE;
        if (beyond$netPage > totalPage) {
            beyond$netPage = totalPage;
        }
        int from = beyond$netPage * beyond$PAGE_SIZE;
        int to = Math.min(from + beyond$PAGE_SIZE, entries.size());
        for (int i = from; i < to; i++) {
            var entry = entries.get(i);
            int y = startY + (i - from) * 18;
            ItemStack display = entry.display();
            this.addRenderableWidget(new NetAttachmentSlot(startX, y, display, entry.count(), b -> {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    SoundPlayManager.playerRefitSound(display, player, SoundManager.INSTALL_SOUND);
                }
                PacketHandler.sendToServer(new NetRefitAttachmentPacket(type, entry.attachmentId()));
            }));
        }

        if (beyond$netPage > 0) {
            this.addRenderableWidget(new RefitTurnPageButton(startX, startY - 10, true, b -> {
                beyond$netPage--;
                this.init();
            }));
        }
        if (beyond$netPage < totalPage) {
            this.addRenderableWidget(new RefitTurnPageButton(startX, startY + beyond$PAGE_SIZE * 18 + 2, false, b -> {
                beyond$netPage++;
                this.init();
            }));
        }
    }

    /** 反射查找 SlotAdapterHelper#getAvailableSlotAdapters（1.2.0+）；不存在返回 null。 */
    @Unique
    private static java.lang.reflect.Method beyond$findSlotAdapterLookup() {
        try {
            Class<?> helper = Class.forName("com.tacz.guns.util.SlotAdapterHelper");
            return helper.getMethod("getAvailableSlotAdapters", ItemStack.class, AttachmentType.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 当前枪 + 槽位类型是否有可用的适配器列。 */
    @Unique
    private static boolean beyond$hasSlotAdapters(ItemStack gun, AttachmentType type) {
        java.lang.reflect.Method method = beyond$SLOT_ADAPTER_LOOKUP;
        if (method == null) return false;
        try {
            Object result = method.invoke(null, gun, type);
            return result instanceof Collection<?> collection && !collection.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 反射查找 TimelessAPI#getAllCommonAttachmentVariantIndex；不存在返回 null。 */
    @Unique
    private static java.lang.reflect.Method beyond$findVariantLookup() {
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            return api.getMethod("getAllCommonAttachmentVariantIndex");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 枪上该类型配件是否有变体（决定 tacz 是否绘制变体列，用于让位判断）。 */
    @Unique
    private static boolean beyond$hasVariants(Minecraft mc, ItemStack gun, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || mc.level == null) return false;
        ItemStack attachment = iGun.getAttachment(mc.level.registryAccess(), gun, type);
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
        if (iAttachment == null) return false;
        ResourceLocation id = iAttachment.getAttachmentId(attachment);
        if (id == null) return false;
        java.lang.reflect.Method lookup = beyond$VARIANT_LOOKUP;
        if (lookup == null) return false;
        try {
            Object variants = lookup.invoke(null);
            if (!(variants instanceof Collection<?> entries)) return false;
            for (Object entryObj : entries) {
                if (!(entryObj instanceof java.util.Map.Entry<?, ?> entry)) continue;
                Object value = entry.getValue();
                if (value == null) continue;
                Object applies = value.getClass()
                        .getMethod("appliesTo", ResourceLocation.class)
                        .invoke(value, id);
                if (Boolean.TRUE.equals(applies)) return true;
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
