package com.solr98.beyondintegration.mixin;
import com.atsuishio.superbwarfare.client.overlay.RenderContext;
import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.solr98.beyondintegration.client.SuperbAmmoCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.text.NumberFormat;

@Mixin(targets = "com.atsuishio.superbwarfare.client.overlay.AmmoBarOverlay", remap = false)
public class AmmoBarOverlayMixin {
    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void onRender(RenderContext ctx, CallbackInfo ci) {
        var player = ctx.getPlayer();
        if (player == null || player.getVehicle() != null) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem)) return;
        if (!SuperbAmmoCache.INSTANCE.hasData()) return;

        Font font = ctx.getMc().font;
        GuiGraphics gg = ctx.getGuiGraphics();
        int right = ctx.getScreenWidth() - 12;
        int y = ctx.getScreenHeight() - 60;

        int netId = SuperbAmmoCache.INSTANCE.getNetId();
        String netName = SuperbAmmoCache.INSTANCE.getNetworkName();
        String nl = !netName.isEmpty() ? netName + " (Net#" + netId + ")" : "(Net#" + netId + ")";
        gg.drawString(font, nl, right - font.width(nl), y - 29, 0x55FFFF, true);

        long en = SuperbAmmoCache.INSTANCE.getNetworkEnergy();
        if (en >= 0) { String es = NumberFormat.getIntegerInstance().format(en) + " FE"; gg.drawString(font, es, right - font.width(es), y - 19, 0xFFAA00, true); }

        GunData data = GunData.from(stack);
        if (data == null) return;
        var consumer = data.selectedAmmoConsumer();
        if (consumer == null) return;
        var ct = consumer.getType();
        String as = "";
        if (ct == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
            Ammo at = consumer.getPlayerAmmoType();
            if (at != null) {
                long c = SuperbAmmoCache.INSTANCE.getCount(at.serializationName);
                boolean inf = SuperbAmmoCache.INSTANCE.hasInfinite();
                if (c > 0 || inf) {
                    String cs = inf ? "INF" : NumberFormat.getIntegerInstance().format(c);
                    as = cs + " : " + Component.translatable(at.translationKey).getString();
                }
            }
        } else if (ct == AmmoConsumer.AmmoConsumeType.ITEM) {
            var ammoStack = consumer.stack();
            if (ammoStack == null || ammoStack.isEmpty()) return;
            String raw = BuiltInRegistries.ITEM.getKey(ammoStack.getItem()).toString();
            if (raw != null) {
                long c = SuperbAmmoCache.INSTANCE.getCount("ITEM:" + raw);
                if (c > 0) {
                    var rl = ResourceLocation.tryParse(raw);
                    if (rl == null) return;
                    var item = BuiltInRegistries.ITEM.get(rl);
                    if (item != null && item != Items.AIR) as = NumberFormat.getIntegerInstance().format(c) + " : " + item.getName(ItemStack.EMPTY).getString();
                }
            }
        }
        if (!as.isEmpty()) gg.drawString(font, as, right - font.width(as), y - 9, 0x55FFFF, true);
    }
}
