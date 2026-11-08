package com.solr98.beyondintegration.integration.ae2lt.storage;

import com.moakiee.ae2lt.api.lightning.LightningTier;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.integration.ae2lt.Ae2ltIds;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.LongStackKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * 维度网络中的闪电存储键：高压 / 超高压两种档位共用同一类型 ID。
 */
public final class LightningStackKey extends LongStackKey<LightningType> {

    public static final ResourceLocation ID = ResourceLocation.tryBuild(
            BeyondIntegration.MODID, "stack_type/lightning");
    public static final LightningStackKey HIGH_VOLTAGE = new LightningStackKey(LightningTier.HIGH_VOLTAGE);
    public static final LightningStackKey EXTREME_HIGH_VOLTAGE = new LightningStackKey(LightningTier.EXTREME_HIGH_VOLTAGE);

    private final LightningTier tier;

    private LightningStackKey(LightningTier tier) {
        this.tier = Objects.requireNonNull(tier);
        this.stack = new LightningType(tier, 0);
    }

    public static LightningStackKey of(LightningTier tier) {
        return tier == LightningTier.EXTREME_HIGH_VOLTAGE ? EXTREME_HIGH_VOLTAGE : HIGH_VOLTAGE;
    }

    public LightningTier tier() {
        return tier;
    }

    @Override
    public ResourceLocation getTypeID() {
        return ID;
    }

    @Override
    public @Nullable KeyAmount fromStackObject(Object stack) {
        if (stack instanceof LightningKey lightningKey) {
            return new KeyAmount(of(lightningKey.apiTier()), 1);
        }
        return stack instanceof LightningType lightning
                ? new KeyAmount(of(lightning.tier()), lightning.getStackCount())
                : null;
    }

    @Override
    public @Nullable LightningStackKey fromSourceObject(Object key, CompoundTag ignored) {
        if (key instanceof LightningKey lightningKey) return of(lightningKey.apiTier());
        if (key instanceof LightningType lightning) return of(lightning.tier());
        if (key instanceof LightningTier lightningTier) return of(lightningTier);
        return null;
    }

    /**
     * 供 BD 的 JEI 幽灵拖拽匹配（{@code NetInterfaceGhostHandler}）识别 AE2LT 的闪电素材：
     * 返回 {@link LightningKey} 的类，使标记槽能接收从 JEI 拖来的闪电键。仅用于拖拽匹配，
     * BD 核心不以此类实例化堆叠。
     */
    @Override
    @SuppressWarnings("unchecked")
    public Class<LightningType> getStackClass() {
        return (Class<LightningType>) (Class<?>) LightningKey.class;
    }

    /**
     * 供 BD 的 EMI 拖拽匹配（{@code SlotDragHandler}）识别 AE2LT 的闪电素材。
     */
    @Override
    public Class<?> getSourceClass() {
        return LightningKey.class;
    }

    @Override
    public String getModId() {
        return Ae2ltIds.MODID;
    }

    @Override
    public LightningStackKey getEmpty() {
        return HIGH_VOLTAGE;
    }

    @Override
    public @NotNull LightningType getSource() {
        return stack;
    }

    @Override
    public LightningType getEmptyStack() {
        return new LightningType(tier, 0);
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
    public boolean isSame(IStackKey<?> other) {
        return other instanceof LightningStackKey lightning && lightning.tier == tier;
    }

    @Override
    public boolean isSameTypeSameComponents(IStackKey<?> other) {
        return isSame(other);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LightningStackKey lightning && lightning.tier == tier;
    }

    @Override
    public int hashCode() {
        return 31 * ID.hashCode() + tier.hashCode();
    }

    @Override
    public void serialize(FriendlyByteBuf buf) {
        tier.toNetwork(buf);
    }

    @Override
    public @NotNull LightningStackKey deserialize(FriendlyByteBuf buf) {
        return of(LightningTier.fromNetwork(buf));
    }

    @Override
    public @NotNull CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("tier", tier.getSerializedName());
        return tag;
    }

    @Override
    public @NotNull LightningStackKey deserializeNBT(CompoundTag nbt) {
        return of(LightningTier.fromSerializedName(nbt.getString("tier")));
    }

    @Override
    public @NotNull IStackRender getRender() {
        return LightningStackKeyRender.INSTANCE;
    }
}
