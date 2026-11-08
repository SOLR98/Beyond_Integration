package com.solr98.beyondintegration.feature.mana;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.solr98.beyondintegration.BeyondIntegration;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.LongStackKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * 网络"魔力"存储键（纯数值型）。
 * <p>类型 ID：{@code beyond_integration:stack_type/iron_mana}；旧 ID
 * {@code beyond_integration:stack_type/mana} 仍注册，用于旧存档反序列化与迁移。
 */
public class ManaStackKey extends LongStackKey<ManaType> {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(BeyondIntegration.MODID, "stack_type/iron_mana");

    /** 旧版魔力键 ID（仅用于反序列化与迁移）。 */
    public static final ResourceLocation LEGACY_ID =
            ResourceLocation.fromNamespaceAndPath(BeyondIntegration.MODID, "stack_type/mana");

    public static final ManaStackKey INSTANCE = new ManaStackKey(ID);
    public static final ManaStackKey LEGACY = new ManaStackKey(LEGACY_ID);

    /** 无字段的新格式。 */
    public static final MapCodec<ManaStackKey> TYPE_CODEC = new MapCodec<>() {
        @Override
        public <T> DataResult<ManaStackKey> decode(DynamicOps<T> ops, MapLike<T> input) {
            return DataResult.success(ManaStackKey.INSTANCE);
        }

        @Override
        public <T> RecordBuilder<T> encode(ManaStackKey value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return prefix;
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }
    };

    private final ResourceLocation id;

    private ManaStackKey(ResourceLocation id) {
        this.id = id;
        this.stack = new ManaType(0);
    }

    @Override
    public MapCodec<ManaStackKey> codec() {
        return TYPE_CODEC;
    }

    @Override
    public @Nullable KeyAmount fromStackObject(Object stack) {
        if (stack instanceof ManaType type) {
            return new KeyAmount(this, type.getStackCount());
        }
        return null;
    }

    @Override
    public ResourceLocation getTypeID() {
        return id;
    }

    @Override
    public long getVanillaMaxStackSize() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String getModId() {
        return BeyondIntegration.MODID;
    }

    @Override
    public @Nullable ManaStackKey fromSourceObject(Object key, DataComponentPatch ignored) {
        if (key instanceof ManaType || key instanceof Number) {
            return this;
        }
        return null;
    }

    @Override
    public @NotNull ManaType getSource() {
        return this.stack;
    }

    @Override
    public ManaStackKey getEmpty() {
        return this;
    }

    @Override
    public ManaType getEmptyStack() {
        return new ManaType(0);
    }

    @Override
    public boolean hasTag(TagKey<?> tagKey) {
        return false;
    }

    @Override
    public Stream<? extends TagKey<?>> getTags() {
        return Stream.empty();
    }

    @Override
    public void serialize(RegistryFriendlyByteBuf buf) {
    }

    @Override
    public @NotNull ManaStackKey deserialize(RegistryFriendlyByteBuf buf) {
        return this;
    }

    @Override
    public @NotNull CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return new CompoundTag();
    }

    @Override
    public @NotNull ManaStackKey deserializeNBT(CompoundTag nbt, HolderLookup.Provider provider) {
        return this;
    }

    @Override
    public @NotNull IStackRender getRender() {
        return com.solr98.beyondintegration.client.render.ManaStackKeyRender.INSTANCE;
    }
}
