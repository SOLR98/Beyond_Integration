package com.solr98.beyondintegration.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册与发送中心（NeoForge PayloadRegistrar）。
 * 统一注册全部 C2S/S2C payload 及其编解码器、处理器，并提供便捷发送入口。
 */
@EventBusSubscriber(modid = "beyond_integration", bus = EventBusSubscriber.Bus.MOD)
public class PacketHandler {

    /** 注册所有自定义 payload 的收发方向与编解码器（版本 "1"） */
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // ── 通用 ──
        registrar.playToServer(ProtectItemPacket.TYPE, ProtectItemPacket.STREAM_CODEC, ProtectItemPacket::handle);
        registrar.playToClient(NetworkItemCountsPacket.TYPE, NetworkItemCountsPacket.STREAM_CODEC, NetworkItemCountsPacket::handle);
        registrar.playToClient(PrimaryNetSyncPacket.TYPE, PrimaryNetSyncPacket.STREAM_CODEC, PrimaryNetSyncPacket::handle);
        registrar.playToClient(HudHintPacket.TYPE, HudHintPacket.STREAM_CODEC, HudHintPacket::handle);
        registrar.playToClient(SoulEnergySyncPacket.TYPE, SoulEnergySyncPacket.STREAM_CODEC, SoulEnergySyncPacket::handle);
        registrar.playToClient(SoulBeamPacket.TYPE, SoulBeamPacket.STREAM_CODEC, SoulBeamPacket::handle);
        registrar.playToClient(ManaSyncPacket.TYPE, ManaSyncPacket.STREAM_CODEC, ManaSyncPacket::handle);

        // ── 附魔分离 ──
        registrar.playToServer(ToggleEnchantSeparationPacket.TYPE, ToggleEnchantSeparationPacket.STREAM_CODEC, ToggleEnchantSeparationPacket::handle);
        registrar.playToClient(EnchantSeparationSyncPacket.TYPE, EnchantSeparationSyncPacket.STREAM_CODEC, EnchantSeparationSyncPacket::handle);
        registrar.playToServer(RequestEnchantSeparationPacket.TYPE, RequestEnchantSeparationPacket.STREAM_CODEC, RequestEnchantSeparationPacket::handle);

        // ── 自动充电 ──
        registrar.playToServer(ToggleEnergyChargePacket.TYPE, ToggleEnergyChargePacket.STREAM_CODEC, ToggleEnergyChargePacket::handle);
        registrar.playToClient(EnergyChargeSyncPacket.TYPE, EnergyChargeSyncPacket.STREAM_CODEC, EnergyChargeSyncPacket::handle);
        registrar.playToServer(RequestEnergyChargePacket.TYPE, RequestEnergyChargePacket.STREAM_CODEC, RequestEnergyChargePacket::handle);

        // ── 药水护符 ──
        registrar.playToServer(CyclePotionCharmModePacket.TYPE, CyclePotionCharmModePacket.STREAM_CODEC, CyclePotionCharmModePacket::handle);
        registrar.playToClient(PotionCharmSyncPacket.TYPE, PotionCharmSyncPacket.STREAM_CODEC, PotionCharmSyncPacket::handle);
        registrar.playToServer(RequestPotionCharmPacket.TYPE, RequestPotionCharmPacket.STREAM_CODEC, RequestPotionCharmPacket::handle);
        registrar.playToServer(SacrificeMendingPacket.TYPE, SacrificeMendingPacket.STREAM_CODEC, SacrificeMendingPacket::handle);

        // ── 网络取物 / 弹药 ──
        registrar.playToServer(ExtractNetworkItemPacket.TYPE, ExtractNetworkItemPacket.STREAM_CODEC, ExtractNetworkItemPacket::handle);
        registrar.playToClient(SuperbAmmoStatusResponsePacket.TYPE, SuperbAmmoStatusResponsePacket.STREAM_CODEC, SuperbAmmoStatusResponsePacket::handle);
        registrar.playToServer(RequestSuperbAmmoStatusPacket.TYPE, RequestSuperbAmmoStatusPacket.STREAM_CODEC, RequestSuperbAmmoStatusPacket::handle);
        registrar.playToClient(SuperbAmmoDeltaS2CPacket.TYPE, SuperbAmmoDeltaS2CPacket.STREAM_CODEC, SuperbAmmoDeltaS2CPacket::handle);
        registrar.playToServer(RequestItemAmmoPacket.TYPE, RequestItemAmmoPacket.STREAM_CODEC, RequestItemAmmoPacket::handle);
        registrar.playToClient(ItemAmmoResponsePacket.TYPE, ItemAmmoResponsePacket.STREAM_CODEC, ItemAmmoResponsePacket::handle);
        registrar.playToServer(RequestNetworkItemsPacket.TYPE, RequestNetworkItemsPacket.STREAM_CODEC, RequestNetworkItemsPacket::handle);
        registrar.playToServer(RequestAmmoCountPacket.TYPE, RequestAmmoCountPacket.STREAM_CODEC, RequestAmmoCountPacket::handle);
        registrar.playToClient(AmmoCountResponsePacket.TYPE, AmmoCountResponsePacket.STREAM_CODEC, AmmoCountResponsePacket::handle);
        registrar.playToClient(TaczAmmoPushS2CPacket.TYPE, TaczAmmoPushS2CPacket.STREAM_CODEC, TaczAmmoPushS2CPacket::handle);
        registrar.playToServer(TaczCraftPacket.TYPE, TaczCraftPacket.STREAM_CODEC, TaczCraftPacket::handle);

        // ── 主网络配置同步 ──
        registrar.playToClient(SyncConfigSyncPacket.TYPE, SyncConfigSyncPacket.STREAM_CODEC, SyncConfigSyncPacket::handle);
        registrar.playToServer(SyncResyncRequestPacket.TYPE, SyncResyncRequestPacket.STREAM_CODEC, SyncResyncRequestPacket::handle);

        // ── 神化附魔台预览 ──
        registrar.playToServer(RequestApothPreviewPacket.TYPE, RequestApothPreviewPacket.STREAM_CODEC, RequestApothPreviewPacket::handle);
        registrar.playToClient(ApothPreviewPacket.TYPE, ApothPreviewPacket.STREAM_CODEC, ApothPreviewPacket::handle);
        registrar.playToServer(RequestSacrificePreviewPacket.TYPE, RequestSacrificePreviewPacket.STREAM_CODEC, RequestSacrificePreviewPacket::handle);
        registrar.playToClient(SacrificePreviewPacket.TYPE, SacrificePreviewPacket.STREAM_CODEC, SacrificePreviewPacket::handle);

        // ── 网络喂食器口渴设置（BD 0.8 界面无此项，BI 以 BdPage 页签注入）──
        registrar.playToServer(RequestFeederThirstPacket.TYPE, RequestFeederThirstPacket.STREAM_CODEC, RequestFeederThirstPacket::handle);
        registrar.playToServer(SetFeederThirstPacket.TYPE, SetFeederThirstPacket.STREAM_CODEC, SetFeederThirstPacket::handle);
        registrar.playToClient(FeederThirstStatePacket.TYPE, FeederThirstStatePacket.STREAM_CODEC, FeederThirstStatePacket::handle);

        // ── 经验棒自动修复设置（同上，BI 以 BdPage 页签注入经验棒界面）──
        registrar.playToServer(RequestXpRepairPacket.TYPE, RequestXpRepairPacket.STREAM_CODEC, RequestXpRepairPacket::handle);
        registrar.playToServer(SetXpRepairPacket.TYPE, SetXpRepairPacket.STREAM_CODEC, SetXpRepairPacket::handle);
        registrar.playToClient(XpRepairStatePacket.TYPE, XpRepairStatePacket.STREAM_CODEC, XpRepairStatePacket::handle);

        // ── 附魔合并（BdPage：装备来自网络，服务端扫描附魔书并执行合并）──
        registrar.playToServer(RequestEnchantMergePacket.TYPE, RequestEnchantMergePacket.STREAM_CODEC, RequestEnchantMergePacket::handle);
        registrar.playToServer(SubmitEnchantMergePacket.TYPE, SubmitEnchantMergePacket.STREAM_CODEC, SubmitEnchantMergePacket::handle);
        registrar.playToClient(EnchantMergeStatePacket.TYPE, EnchantMergeStatePacket.STREAM_CODEC, EnchantMergeStatePacket::handle);

        // ── 网络配件（独立列路线，tacz 改装界面）──
        registrar.playToServer(RequestNetAttachmentsPacket.TYPE, RequestNetAttachmentsPacket.STREAM_CODEC, RequestNetAttachmentsPacket::handle);
        registrar.playToClient(NetAttachmentsPacket.TYPE, NetAttachmentsPacket.STREAM_CODEC, NetAttachmentsPacket::handle);
        registrar.playToServer(NetRefitAttachmentPacket.TYPE, NetRefitAttachmentPacket.STREAM_CODEC, NetRefitAttachmentPacket::handle);
    }

    /** 客户端 → 服务端发送 */
    public static void sendToServer(CustomPacketPayload msg) {
        PacketDistributor.sendToServer(msg);
    }

    /** 服务端 → 指定玩家发送（假玩家 / 未连接玩家：静默跳过，避免 connection 为 null 崩溃） */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload msg) {
        if (player == null || player.connection == null) return;
        PacketDistributor.sendToPlayer(player, msg);
    }
}
