package com.solr98.beyondintegration.feature.soul;

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
 * 网络"灵魂能量"存储键（1.21.1）。
 * <p>类型 ID：{@code beyond_integration:stack_type/soul_energy}；RI 键
 * {@code beyonddimensions:stack_type/goety_soul} 作为可选/兜底。
 */
public class SoulEnergyStackKey extends LongStackKey<SoulEnergyType> {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(BeyondIntegration.MODID, "stack_type/soul_energy");

    public static final ResourceLocation RI_FALLBACK_ID =
            ResourceLocation.parse("beyonddimensions:stack_type/goety_soul");

    public static final SoulEnergyStackKey INSTANCE = new SoulEnergyStackKey(ID);
    public static final SoulEnergyStackKey RI_FALLBACK = new SoulEnergyStackKey(RI_FALLBACK_ID);

    public static final MapCodec<SoulEnergyStackKey> TYPE_CODEC = new MapCodec<>() {
        @Override
        public <T> DataResult<SoulEnergyStackKey> decode(DynamicOps<T> ops, MapLike<T> input) {
            return DataResult.success(SoulEnergyStackKey.INSTANCE);
        }

        @Override
        public <T> RecordBuilder<T> encode(SoulEnergyStackKey value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return prefix;
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }
    };

    private final ResourceLocation id;

    private SoulEnergyStackKey(ResourceLocation id) {
        this.id = id;
        this.stack = new SoulEnergyType(0);
    }

    @Override
    public MapCodec<SoulEnergyStackKey> codec() {
        return TYPE_CODEC;
    }

    @Override
    public @Nullable KeyAmount fromStackObject(Object stack) {
        if (stack instanceof SoulEnergyType type) {
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
    public @Nullable SoulEnergyStackKey fromSourceObject(Object key, DataComponentPatch ignored) {
        if (key instanceof SoulEnergyType || key instanceof Number) {
            return this;
        }
        return null;
    }

    @Override
    public @NotNull SoulEnergyType getSource() {
        return this.stack;
    }

    @Override
    public SoulEnergyStackKey getEmpty() {
        return this;
    }

    @Override
    public SoulEnergyType getEmptyStack() {
        return new SoulEnergyType(0);
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
    public @NotNull SoulEnergyStackKey deserialize(RegistryFriendlyByteBuf buf) {
        return this;
    }

    @Override
    public @NotNull CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return new CompoundTag();
    }

    @Override
    public @NotNull SoulEnergyStackKey deserializeNBT(CompoundTag nbt, HolderLookup.Provider provider) {
        return this;
    }

    @Override
    public @NotNull IStackRender getRender() {
        return com.solr98.beyondintegration.client.render.SoulEnergyStackKeyRender.INSTANCE;
    }
}
