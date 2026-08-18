package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入哨戒机械臂的 SentryArmBlockEntity：
 * 在 performInstantReload 前把哨戒绑定的网络 ID 写入 FakePlayer 快捷栏第 9 格
 * （伪装成 NetedItem），供 TACZ 枪械 API 感知网络弹药，实现哨戒从网络补弹。
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryFakePlayerSyncMixin {

    /** 换弹前将网络 ID 同步进 FakePlayer 物品栏（仅在服务端生效） */
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

            // Only need netId on the terminal — the sentry's own binding is already validated
            ItemStack terminal = new ItemStack(Items.STONE, 1);
            NetedItem.setNetId(terminal, netId);
            fakePlayer.getInventory().setItem(8, terminal);
        } catch (Exception ignored) {}
    }
}
