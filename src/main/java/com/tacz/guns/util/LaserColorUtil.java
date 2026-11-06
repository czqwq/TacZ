package com.tacz.guns.util;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;

/**
 * 激光（镭射）颜色解析：优先物品 NBT 里的自定义颜色，否则用客户端索引里的默认颜色。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>上游两个重载的「默认色」来源都在 <b>client 包</b>：
 * {@code client.resource.index.ClientAttachmentIndex}、{@code client.resource.GunDisplayInstance}、
 * {@code client.resource.pojo.display.LaserConfig}。本包（util）按 L1 编译边界
 * <b>不允许</b> import {@code com.tacz.guns.client.*}，因此：
 * <ul>
 * <li>{@link #getLaserColor(ItemStack)}：自定义颜色分支<b>完整保留</b>（只依赖 api 的
 * {@link IAttachment}/{@link IGun}）；默认色分支改为「静态 hook」——
 * {@link #setClientDefaultColorResolver(Function)}，由 client 层在初始化时注入
 * 「按 stack 查客户端索引」的实现，未注入时回退 {@link #DEFAULT_LASER_COLOR}（{@code 0xFF0000}，与上游一致）。</li>
 * <li>{@link #getLaserColor(ItemStack, Object)}：上游第二个形参是 {@code LaserConfig}。
 * 为了既不引入 client 类型又<b>保持调用点 {@code getLaserColor(stack, laserConfig)} 可编译</b>，
 * 这里把形参放宽为 {@link Object}，并在运行时<b>反射</b>调用它的 {@code getDefaultColor()}；
 * 反射失败或对象为 {@code null} 时回退 {@link #DEFAULT_LASER_COLOR}。
 * 这是本文件唯一的语义降级（上游是静态类型调用，这里是一次反射调用）。</li>
 * </ul>
 * </li>
 * <li>{@code net.minecraft.world.item.ItemStack} → {@link ItemStack}；1.7.10 里空栈是
 * {@code null}，因此判空写成 {@code stack == null || stack.getItem() == null}（上游只判 {@code null}）。</li>
 * <li>{@code instanceof IAttachment iAttachment} 的模式匹配改成显式 {@code instanceof} + 强转，
 * 降低 jvmDowngrader 的负担。</li>
 * <li>{@code @NotNull} 保留（{@code org.jetbrains.annotations} 是编译期依赖）。</li>
 * </ul>
 */
public class LaserColorUtil {

    /** 上游多处写死的兜底颜色 {@code 0xFF0000}。 */
    public static final int DEFAULT_LASER_COLOR = 0xFF0000;

    /** 等价上游「查客户端索引拿默认色」的那一步，由 client 层注入。 */
    private static volatile Function<ItemStack, Integer> clientDefaultColorResolver = stack -> DEFAULT_LASER_COLOR;

    /** 反射缓存的 {@code getDefaultColor()} 方法（按类缓存，避免每次都查）。 */
    private static final Map<Class<?>, Method> DEFAULT_COLOR_METHODS = new ConcurrentHashMap<>();

    /**
     * 由 client 层注入「按物品查客户端索引得默认激光色」的实现。
     *
     * @param resolver 为 {@code null} 时恢复为恒返回 {@link #DEFAULT_LASER_COLOR}
     */
    public static void setClientDefaultColorResolver(Function<ItemStack, Integer> resolver) {
        clientDefaultColorResolver = resolver == null ? stack -> DEFAULT_LASER_COLOR : resolver;
    }

    public static int getLaserColor(ItemStack stack, @NotNull Object defaultConfig) {
        if (stack == null || stack.getItem() == null) {
            return defaultColorOf(defaultConfig);
        }

        if (stack.getItem() instanceof IAttachment) {
            IAttachment iAttachment = (IAttachment) stack.getItem();
            if (iAttachment.hasCustomLaserColor(stack)) {
                return iAttachment.getLaserColor(stack);
            } else {
                return defaultColorOf(defaultConfig);
            }
        }

        if (stack.getItem() instanceof IGun) {
            IGun gun = (IGun) stack.getItem();
            if (gun.hasCustomLaserColor(stack)) {
                return gun.getLaserColor(stack);
            } else {
                return defaultColorOf(defaultConfig);
            }
        }

        return defaultColorOf(defaultConfig);
    }

    public static int getLaserColor(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return DEFAULT_LASER_COLOR;
        }

        if (stack.getItem() instanceof IAttachment) {
            IAttachment iAttachment = (IAttachment) stack.getItem();
            if (iAttachment.hasCustomLaserColor(stack)) {
                return iAttachment.getLaserColor(stack);
            } else {
                // 上游：TimelessAPI.getClientAttachmentIndex(...).map(ClientAttachmentIndex::getLaserConfig)
                // .map(LaserConfig::getDefaultColor).orElse(0xFF0000)
                // 1.7.10：client 类型不可引用，改为 hook。
                return resolveClientDefaultColor(stack);
            }
        }

        if (stack.getItem() instanceof IGun) {
            IGun gun = (IGun) stack.getItem();
            if (gun.hasCustomLaserColor(stack)) {
                return gun.getLaserColor(stack);
            } else {
                // 上游：TimelessAPI.getGunDisplay(stack).map(GunDisplayInstance::getLaserConfig)
                // .map(LaserConfig::getDefaultColor).orElse(0xFF0000)
                return resolveClientDefaultColor(stack);
            }
        }

        return DEFAULT_LASER_COLOR;
    }

    private static int resolveClientDefaultColor(ItemStack stack) {
        try {
            Integer color = clientDefaultColorResolver.apply(stack);
            return color == null ? DEFAULT_LASER_COLOR : color;
        } catch (RuntimeException e) {
            return DEFAULT_LASER_COLOR;
        }
    }

    /**
     * 等价上游 {@code LaserConfig#getDefaultColor()}（1.7.10 侧通过反射，见类注释）。
     */
    private static int defaultColorOf(Object defaultConfig) {
        if (defaultConfig == null) {
            return DEFAULT_LASER_COLOR;
        }
        Method method = DEFAULT_COLOR_METHODS.computeIfAbsent(defaultConfig.getClass(), type -> {
            try {
                Method found = type.getMethod("getDefaultColor");
                found.setAccessible(true);
                return found;
            } catch (NoSuchMethodException e) {
                return MISSING_METHOD;
            }
        });
        if (method == MISSING_METHOD) {
            return DEFAULT_LASER_COLOR;
        }
        try {
            Object value = method.invoke(defaultConfig);
            return value instanceof Integer ? (Integer) value : DEFAULT_LASER_COLOR;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return DEFAULT_LASER_COLOR;
        }
    }

    /** 占位方法对象，表示「该类没有 getDefaultColor()」。 */
    private static final Method MISSING_METHOD;

    static {
        Method placeholder;
        try {
            placeholder = LaserColorUtil.class.getDeclaredMethod("resolveClientDefaultColor", ItemStack.class);
        } catch (NoSuchMethodException e) {
            // 理论上不可能发生；兜底为 null，调用侧会因此走 MISSING_METHOD 判等失败而进 catch。
            placeholder = null;
        }
        MISSING_METHOD = placeholder;
    }
}
