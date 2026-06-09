package com.solr98.beyondintegration.network;
import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.HashMap;

public record RequestSuperbAmmoExtractPacket(String ammoType, long amount) implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Type<RequestSuperbAmmoExtractPacket> TYPE = new Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":request_superb_ammo_extract"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSuperbAmmoExtractPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public @NotNull RequestSuperbAmmoExtractPacket decode(RegistryFriendlyByteBuf buf) {
            return new RequestSuperbAmmoExtractPacket(buf.readUtf(), buf.readLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, RequestSuperbAmmoExtractPacket p) {
            buf.writeUtf(p.ammoType); buf.writeLong(p.amount);
        }
    };

    @Nullable
    private static DimensionsNet findCurrentNet(ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net != null) return net;
        net = getNetFromOpenMenu(player);
        if (net != null) return net;
        for (var hand : InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            Integer id = stack.get(BDDataComponents.NET_ID_DATA.get());
            if (id != null && id >= 0) {
                net = DimensionsNet.getNetFromId(id);
                if (net != null) return net;
            }
        }
        return null;
    }

    @Nullable
    private static DimensionsNet getNetFromOpenMenu(ServerPlayer player) {
        var menu = player.containerMenu;
        if (menu == null) return null;
        String className = menu.getClass().getName();
        if (!className.contains("DimensionsNetMenu") && !className.contains("DimensionsCraftMenu")
                && !className.contains("NetControlMenu")) return null;
        try {
            var posField = menu.getClass().getDeclaredField("entityPos");
            posField.setAccessible(true);
            var pos = (net.minecraft.core.BlockPos) posField.get(menu);
            if (pos == null) return null;
            var be = player.level().getBlockEntity(pos);
            if (be == null) return null;
            var getNetMethod = be.getClass().getMethod("getNet");
            return (DimensionsNet) getNetMethod.invoke(be);
        } catch (Exception e) {
            LOGGER.warn("Failed to get DimensionsNet from open menu via reflection", e);
        }
        return null;
    }

    public static void handle(final RequestSuperbAmmoExtractPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var net = findCurrentNet(player);
            if (net == null || !(net instanceof SuperbAmmoAccessor acc)) return;
            var map = acc.getSuperbAmmo();
            boolean infinite = map.getOrDefault("__infinite__", 0L) > 0;
            long current = infinite ? Long.MAX_VALUE : map.getOrDefault(packet.ammoType, 0L);
            if (current <= 0) return;

            long take = Math.min(packet.amount, current);
            Ammo ammo = Ammo.getType(packet.ammoType);
            if (ammo == null) return;

            if (!infinite) {
                long remaining = current - take;
                if (remaining <= 0) map.remove(packet.ammoType);
                else map.put(packet.ammoType, remaining);
                net.setDirty();
            }

            long giveCount = take;
            var ammoItem = ammo.getItem();
            if (ammoItem == null) return;
            while (giveCount > 0) {
                int stackSize = (int) Math.min(giveCount, 64);
                ItemStack ammoStack = new ItemStack(ammoItem, stackSize);
                if (!player.getInventory().add(ammoStack))
                    player.drop(ammoStack, false);
                giveCount -= stackSize;
            }

            long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
            boolean enchantSep = !(net instanceof EnchantSeparationAccessor ea) || ea.beyond$isEnchantSeparationEnabled();
            PacketDistributor.sendToPlayer(player, new SuperbAmmoStatusResponsePacket(
                    net.getId(), new HashMap<>(map), energy, 0, net.getCustomName(), enchantSep));
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
