package com.solr98.beyondintegration.network;

public enum PacketId {

    NETWORK_ITEM_COUNTS(NetworkItemCountsPacket.class),
    TOGGLE_ENCHANT_SEPARATION(ToggleEnchantSeparationPacket.class),

    SUPERB_AMMO_STATUS_RESPONSE(SuperbAmmoStatusResponsePacket.class, "superbwarfare"),
    REQUEST_SUPERB_AMMO_STATUS(RequestSuperbAmmoStatusPacket.class, "superbwarfare"),
    REQUEST_SUPERB_AMMO_EXTRACT(RequestSuperbAmmoExtractPacket.class, "superbwarfare"),

    REQUEST_NETWORK_ITEMS(RequestNetworkItemsPacket.class, "tacz"),
    TACZ_CRAFT(TaczCraftPacket.class, "tacz"),
    REQUEST_AMMO_COUNT(RequestAmmoCountPacket.class, "tacz"),
    AMMO_COUNT_RESPONSE(AmmoCountResponsePacket.class, "tacz"),

    PROTECT_ITEM(ProtectItemPacket.class),
    YWZJ_VEHICLE_DATA_RESPONSE(YwzjVehicleDataResponsePacket.class);

    private final Class<?> packetClass;
    private final String requiredMod;
    private final int id;

    PacketId(Class<?> packetClass) {
        this(packetClass, null);
    }

    PacketId(Class<?> packetClass, String requiredMod) {
        this.packetClass = packetClass;
        this.requiredMod = requiredMod;
        this.id = ordinal();
    }

    public Class<?> getPacketClass() {
        return packetClass;
    }

    public String getRequiredMod() {
        return requiredMod;
    }

    public int getId() {
        return id;
    }

    public boolean shouldRegister() {
        return requiredMod == null || net.neoforged.fml.ModList.get().isLoaded(requiredMod);
    }
}
