package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.client.TaczAmmoCache;
import com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoExtractor;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 注入目标：TacZ 1.2.0 的 {@code LocalPlayerShoot.doShoot} 里对
 * {@code ClientAmmoCount.get(player, gun, iGun)} 的调用点。
 * <p>1.2.0 用该返回值决定连发次数 {@code maxCount}：背包直读枪在客户端背包里没有弹药时返回 0
 * → {@code maxCount = 0} → 不发开火包（即使服务端网络里还有弹药）。
 * <p><b>只替换开火路径的这一处调用</b>（HUD 的备弹显示走
 * {@code ClientAmmoCount.getCached} → 仍是原版背包数量，不会被本注入影响，
 * 避免灰色备弹数字与 BI 的网络弹药行重复/错乱）。
 * <p>{@code ClientAmmoCount} 是 1.2.0 新增类（旧编译依赖没有），所以 target 用字符串，
 * 原方法用反射懒加载调用。
 */
@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerShoot", remap = false)
public abstract class LocalPlayerShootNetAmmoMixin {

    /** 与 1.2.0 的 ClientAmmoCount.MAX_DISPLAY_COUNT 一致。 */
    private static final int MAX_DISPLAY = 9999;

    private static volatile java.lang.reflect.Method clientAmmoGet;
    private static volatile boolean clientAmmoGetResolved;

    private static java.lang.reflect.Method clientAmmoGet() {
        if (clientAmmoGetResolved) return clientAmmoGet;
        synchronized (LocalPlayerShootNetAmmoMixin.class) {
            if (!clientAmmoGetResolved) {
                try {
                    Class<?> type = Class.forName("com.tacz.guns.client.gameplay.ClientAmmoCount");
                    clientAmmoGet = type.getMethod("get", LocalPlayer.class, ItemStack.class, IGun.class);
                } catch (Throwable ignored) {
                    clientAmmoGet = null;
                }
                clientAmmoGetResolved = true;
            }
        }
        return clientAmmoGet;
    }

    @Redirect(method = "doShoot", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/client/gameplay/ClientAmmoCount;get(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/api/item/IGun;)I"))
    private static int beyond$networkAmmoCount(LocalPlayer player, ItemStack gun, IGun iGun) {
        int current = 0;
        java.lang.reflect.Method method = clientAmmoGet();
        if (method != null) {
            try {
                Object value = method.invoke(null, player, gun, iGun);
                if (value instanceof Integer amount) current = amount;
            } catch (Throwable ignored) {
            }
        }
        if (current >= MAX_DISPLAY) return current;
        ResourceLocation ammoId = TaczAmmoExtractor.getAmmoIdClient(gun);
        if (ammoId == null) return current;
        int network = TaczAmmoCache.getCount(ammoId);
        if (network <= 0) return current;
        if (network == Integer.MAX_VALUE) return MAX_DISPLAY;
        return (int) Math.min((long) current + network, MAX_DISPLAY);
    }
}
