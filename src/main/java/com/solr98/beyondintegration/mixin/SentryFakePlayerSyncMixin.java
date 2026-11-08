package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.feature.sentry.SentryFakePlayerNetMarker;
import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入哨戒机械臂的 SentryArmBlockEntity：
 * 在 performInstantReload 前把哨戒绑定的网络 ID 记录到 {@link SentryFakePlayerNetMarker}
 * （按 fake player 弱引用映射），供 {@code AbstractGunItemMixin} 的扣弹路径直接解析网络。
 * <p>旧实现往 fake player 物品栏塞 {@code Items.STONE + NetedItem} 伪装终端，
 * 但 TACZ 侧（{@code NetTerminalHelper.isTerminal}）只认真正的网络终端物品，该伪装始终无效。
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryFakePlayerSyncMixin {

    /** 换弹前将网络 ID 标记到 FakePlayer（仅在服务端生效） */
    @Inject(method = "performInstantReload", at = @At("HEAD"))
    private void onBeforeReload(net.minecraftforge.common.util.FakePlayer fakePlayer,
                                 com.tacz.guns.api.item.IGun iGun,
                                 net.minecraft.world.item.ItemStack gunStack,
                                 CallbackInfoReturnable<Boolean> cir) {
        try {
            if (fakePlayer == null) return;

            int netId = -1;
            if (((Object) this) instanceof SentryNetIdAccessor accessor) {
                netId = accessor.getSentryNetId();
            }
            if (netId < 0) return;

            SentryFakePlayerNetMarker.mark(fakePlayer, netId);
        } catch (Exception ignored) {}
    }
}
