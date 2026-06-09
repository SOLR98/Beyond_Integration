package com.solr98.beyondintegration.handler;

import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.item.ammo.AmmoBoxItem;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class AmmoWithdrawHandler {

    private static final long COOLDOWN_MS = 500;
    private static final Map<UUID, Long> lastWithdrawTime = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long WITHDRAW_LIMIT_PLAYER_AMMO = 576;

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown()) return;
        handleBlock(event, event.getLevel(), event.getPos(), event.getEntity(), event.getSide().isClient());
    }

    private static void handleBlock(PlayerInteractEvent event, Level level, BlockPos pos, Player player, boolean client) {
        if (!ModList.get().isLoaded("superbwarfare")) return;
        if (client) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        BlockState state = level.getBlockState(pos);
        var blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null) return;
        if (!"beyonddimensions".equals(blockId.getNamespace())) return;
        String path = blockId.getPath();
        if (!"net_terminal_block".equals(path) && !"net_interface".equals(path)) return;

        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long lastTime = lastWithdrawTime.get(uuid);
        if (lastTime != null && now - lastTime < COOLDOWN_MS) return;
        lastWithdrawTime.put(uuid, now);

        ItemStack gunStack = findGun(player);
        if (gunStack.isEmpty()) return;

        DimensionsNet net = getNetworkFromBlock(level, pos);
        if (net == null) {
            net = BDNetworkHelper.findNetwork(serverPlayer);
        }
        if (net == null) return;

        GunData data = GunData.from(gunStack);
        AmmoConsumer consumer = data.selectedAmmoConsumer();
        if (consumer == null) return;

        boolean withdrew;

        if (consumer.getType() == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
            Ammo ammoType = consumer.getPlayerAmmoType();
            withdrew = ammoType != null && withdrawPlayerAmmo(serverPlayer, net, ammoType);
        } else if (consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM && !consumer.stack().isEmpty()) {
            withdrew = withdrawItemAmmo(serverPlayer, net, consumer.stack());
        } else {
            return;
        }

        if (withdrew) {
            player.displayClientMessage(
                    Component.translatable("message.beyond_integration.ammo_withdrawn"), true);
            event.setCanceled(true);
        }
    }

    private static DimensionsNet getNetworkFromBlock(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof NetedBlockEntity neted) {
            return neted.getNet();
        }
        return null;
    }

    private static ItemStack findGun(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GunItem && !(main.getItem() instanceof AmmoBoxItem)) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof GunItem && !(off.getItem() instanceof AmmoBoxItem)) return off;
        return ItemStack.EMPTY;
    }

    private static boolean withdrawPlayerAmmo(ServerPlayer player, DimensionsNet net, Ammo ammoType) {
        if (!(net instanceof SuperbAmmoAccessor acc)) return false;
        Map<String, Long> map = acc.getSuperbAmmo();
        String key = ammoType.serializationName;
        long available = map.getOrDefault(key, 0L);

        if (available <= 0) {
            if (map.getOrDefault("__infinite__", 0L) > 0) {
                player.getInventory().add(new ItemStack(ammoType.getItem(), 64));
                pushUpdate(player, net);
                return true;
            }
            return false;
        }

        long take = Math.min(available, WITHDRAW_LIMIT_PLAYER_AMMO);
        map.put(key, available - take);
        net.setDirty();

        int remaining = (int) take;
        int maxStack = new ItemStack(ammoType.getItem()).getMaxStackSize();
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            if (!player.getInventory().add(new ItemStack(ammoType.getItem(), stackSize))) {
                map.put(key, map.getOrDefault(key, 0L) + remaining);
                break;
            }
            remaining -= stackSize;
        }

        pushUpdate(player, net);
        return true;
    }

    private static boolean withdrawItemAmmo(ServerPlayer player, DimensionsNet net, ItemStack ammoStack) {
        ItemStackKey itemKey = new ItemStackKey(ammoStack);
        KeyAmount available = net.getUnifiedStorage().getStackByKey(itemKey);
        if (available.amount() <= 0) return false;

        long take = Math.min(available.amount(), 64);
        KeyAmount extracted = net.getUnifiedStorage().extract(itemKey, take, false, true);
        if (extracted.amount() <= 0) return false;

        net.setDirty();
        int remaining = (int) extracted.amount();
        while (remaining > 0) {
            int stackSize = Math.min(remaining, ammoStack.getMaxStackSize());
            if (!player.getInventory().add(ammoStack.copyWithCount(stackSize))) {
                net.getUnifiedStorage().insert(itemKey, remaining, false);
                break;
            }
            remaining -= stackSize;
        }
        return true;
    }

    private static void pushUpdate(ServerPlayer player, DimensionsNet net) {
        if (!(net instanceof SuperbAmmoAccessor acc)) return;
        PacketHandler.sendToPlayer(player,
                SuperbAmmoStatusResponsePacket.fromNet(net, new HashMap<>(acc.getSuperbAmmo()),
                        net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount(), 0,
                        ((NetworkNameProvider) net).getCustomName()));
    }
}
