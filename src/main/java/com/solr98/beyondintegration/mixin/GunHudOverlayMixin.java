package com.solr98.beyondintegration.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.handler.TaczAmmoExtractor;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

/**
 * 注入目标：TacZ 的 {@code GunHudOverlay}（客户端枪械 HUD 覆盖层）。
 * 目的：在原弹药计数基础上，将各维度网络中的弹药数量合并进 HUD 显示，
 * 并展示网络名称与总弹药数量（无限网络显示 infinity）。
 *
 * 数据刷新只依赖 {@code render(GuiGraphics, DeltaTracker)} 这一个稳定入口：
 * 不同 TacZ 构建的内部统计方法差异很大（handleInventoryAmmo 在官方测试构建中已被移除，
 * handleCacheCount 存在 4 参 / 5 参两种签名），而 render 签名在官方测试构建与
 * 移植版（1.1.8-hotfix-r1 起）中一致，因此不再注入内部统计方法。
 */
@Mixin(targets = "com.tacz.guns.client.gui.overlay.GunHudOverlay", remap = false)
public class GunHudOverlayMixin {
    // 当前枪械弹药在各网络中的数量缓存（网络ID -> 数量），以及对应的弹药 ID
    @Unique private static Map<Integer, Integer> beyond$networkAmmoMap = Map.of();
    @Unique private static ResourceLocation beyond$lastAmmoId = null;

    // 在原 HUD 渲染后刷新网络弹药缓存并追加绘制网络名称与网络弹药总数
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"), remap = false)
    private void beyond$onRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun)) {
            beyond$networkAmmoMap = Map.of();
            beyond$lastAmmoId = null;
            return;
        }

        // 每帧按主手枪刷新网络弹药缓存（缓存读取与请求均有内部节流）
        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(stack);
        if (ammoId == null) {
            beyond$networkAmmoMap = Map.of();
            beyond$lastAmmoId = null;
            return;
        }
        beyond$networkAmmoMap = TaczAmmoCache.getAllNetworkCounts(ammoId);
        beyond$lastAmmoId = ammoId;
        TaczAmmoCache.requestQuick(ammoId);

        if (beyond$networkAmmoMap.isEmpty()) return;

        int netId = -1;
        long displayCount = 0;
        boolean infinite = false;
        for (var entry : beyond$networkAmmoMap.entrySet()) {
            int raw = entry.getValue();
            if (entry.getKey() >= 0 && raw > 0) {
                netId = entry.getKey();
                if (raw == Integer.MAX_VALUE) { infinite = true; displayCount = 0; }
                else displayCount += raw;
                break;
            }
        }
        if (netId < 0) return;

        Font font = mc.font;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        float s = 0.8f;
        pose.scale(s, s, 1);

        float colonX = (mc.getWindow().getGuiScaledWidth() - 45) / s;
        float y = (mc.getWindow().getGuiScaledHeight() - 53) / s;

        Component netNameComp = TaczAmmoCache.getNetworkDisplayName(netId, beyond$lastAmmoId);
        String countStr = infinite ? "infinity" : String.valueOf((int) Math.min(displayCount, Integer.MAX_VALUE));

        String nameStr = netNameComp.getString();
        String colonStr = ": ";

        int nameEndX = (int) colonX;
        int colonDrawX = (int) colonX;
        int countStartX = (int) colonX + font.width(colonStr);

        graphics.drawString(font, nameStr, nameEndX - font.width(nameStr), (int) y, 0x55FFFF, false);
        graphics.drawString(font, colonStr, colonDrawX, (int) y, 0x55FFFF, false);
        graphics.drawString(font, countStr, countStartX, (int) y, 0x55FFFF, false);
        pose.popPose();
    }
}
