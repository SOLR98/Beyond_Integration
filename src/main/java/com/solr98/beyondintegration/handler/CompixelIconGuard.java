package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.core.log.BiLog;
import com.mojang.logging.LogUtils;
import dev.compixel.forge.item.ItemIcon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CompixelUI 图标渲染异常的报告器：记录「哪个资源键（物品）在渲染时炸了」，并配合
 * {@code CompixelIconGuardMixin} 做安全回退。
 * <p>
 * 已知触发场景：CompixelUI 0.1.15 的 {@code NativeIconAnimation.resolve} 把
 * {@code BakedModel.getParticleIcon()} 当非空（Forge 允许返回 null），
 * 打开 BD 存储界面 / 退出存档仍在渲染时抛
 * {@code NullPointerException: getParticleIcon(...) must not be null}。
 * 回退只发生在图标刷新策略的探测阶段，图标本身仍会画出，只是不再跟着动画重绘。
 * <p>
 * 上报节流：同一个资源键只打一次完整堆栈，之后按累计次数定期汇总，避免每帧刷屏。
 * 本类自身绝不允许再抛异常（否则兜底就变成了新的崩溃源）。
 */
public final class CompixelIconGuard {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 已经完整报告过的资源键。 */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    /** 累计异常次数。 */
    private static final AtomicLong TOTAL = new AtomicLong();
    /** 每累计这么多次输出一条汇总。 */
    private static final long SUMMARY_EVERY = 200L;

    private CompixelIconGuard() {}

    /**
     * 报告一次图标渲染异常。
     *
     * @param icon  出错时正在渲染的 Compixel 图标（通常是 {@link ItemIcon}）
     * @param error 捕获到的异常
     */
    public static void report(Object icon, Throwable error) {
        try {
            String key = describe(icon);
            long total = TOTAL.incrementAndGet();
            if (REPORTED.add(key)) {
                LOGGER.error(BiLog.t("[BI] 物品图标渲染异常，已回退为静态图标（累计第 {} 次）：资源键 = {}；原因 = {}", "[BI] item icon render error; fell back to the static icon (occurrence {}): key = {}; cause = {}"),
                        total, key, error == null ? "?" : error.toString(), error);
            } else if (total % SUMMARY_EVERY == 1L) {
                LOGGER.warn(BiLog.t("[BI] 物品图标渲染异常累计 {} 次，涉及 {} 种资源键（最近一次：{}）", "[BI] item icon render errors: {} total across {} key(s) (latest: {})"),
                        total, REPORTED.size(), key);
            }
        } catch (Throwable ignored) {
            // 调试兜底本身不得再抛
        }
    }

    /** 把图标描述成「物品 ID + 数量 + NBT」，NBT 为空时省略。 */
    private static String describe(Object icon) {
        if (!(icon instanceof ItemIcon itemIcon)) return String.valueOf(icon);
        ItemStack stack = stackOf(itemIcon);
        if (stack != null && !stack.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            CompoundTag tag = stack.getTag();
            String base = id + " x" + stack.getCount();
            return tag == null || tag.isEmpty() ? base : base + " " + tag;
        }
        // ItemIcon 的 stack 是 Kotlin internal（外部模块只能走混淆过的 getter），反射失败时退回显示名
        return "\"" + itemIcon.getDescription() + "\"（无法读取物品键）";
    }

    /**
     * 反射读 {@link ItemIcon} 的原始物品。该字段是 Kotlin {@code internal}，
     * 编译期不可直接访问，读取失败不影响兜底（只降级日志精度）。
     */
    private static ItemStack stackOf(ItemIcon icon) {
        try {
            Field field = STACK_FIELD;
            if (field == null) {
                field = ItemIcon.class.getDeclaredField("stack");
                field.setAccessible(true);
                STACK_FIELD = field;
            }
            return (ItemStack) field.get(icon);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static volatile Field STACK_FIELD;
}
