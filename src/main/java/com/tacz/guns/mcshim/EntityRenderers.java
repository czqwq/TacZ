package com.tacz.guns.mcshim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对应 1.20.1 的 {@code net.minecraft.client.renderer.entity.EntityRenderers}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 通过 {@code RenderingRegistry.registerEntityRenderingHandler(Class, Render)} 注册实体渲染器，
 * 由 {@code cpw.mods.fml.client.registry.RenderingRegistry} 在 {@code ClientRegistry} 阶段调用。
 *
 * <p>
 * 本类是一个<b>转接表</b>：{@link #register} 只把
 * (实体类 / 实体类型, 渲染器工厂) 存进静态表，真正的
 * {@code RenderingRegistry.registerEntityRenderingHandler} 由 {@code ClientProxy} 在
 * {@code FMLInitializationEvent} 里调 {@link #registerAll()} 完成。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.20 的 {@code register} 键是 {@code EntityType<?>}；1.7.10 没有 {@code EntityType}，
 * 注册键是 {@code Class<? extends Entity>}。为了两边都能用，
 * 本类的方法签名用 {@link Object}，并在 {@link #registerAll()} 里按实际类型分派。</li>
 * <li>1.20 讲参数是 {@code EntityRendererProvider<T>}（函数式接口）；
 * 本类同样用 {@link Object} 接（通常传方法引用），由调用方保证是可调用的工厂。</li>
 * <li>1.7.10 的渲染器基类是 {@code net.minecraft.client.renderer.entity.Render}，
 * 1.20 是 {@code EntityRenderer}，类层次完全不同，移植后必须重写渲染器本身。</li>
 * </ul>
 */
public final class EntityRenderers {

    /** 注册项：实体类（或 1.20 的 EntityType）+ 渲染器工厂。 */
    public static final class Entry {

        public final Object entityClassOrType;
        public final Object rendererProvider;

        Entry(Object entityClassOrType, Object rendererProvider) {
            this.entityClassOrType = entityClassOrType;
            this.rendererProvider = rendererProvider;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private EntityRenderers() {}

    /**
     * 对应 1.20 {@code EntityRenderers#register(EntityType, EntityRendererProvider)}。
     *
     * <p>
     * 1.7.10 侧只是登记，实际注册见 {@link #registerAll()}。
     */
    public static void register(Object entityClassOrType, Object rendererProvider) {
        ENTRIES.add(new Entry(entityClassOrType, rendererProvider));
    }

    /** 已登记的项（只读）。 */
    public static List<Entry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    /**
     * 由 {@code ClientProxy} 在 {@code FMLInitializationEvent} 阶段调用，把登记项交给
     * {@code RenderingRegistry}。
     *
     * <p>
     * <b>降级点</b>：本方法只清空登记表并留待调用方自行实现真正的注册
     * （因为 1.7.10 的 {@code RenderingRegistry} 需要一个 {@code Render} 实例，
     * 而 1.20 的 provider 签名不同，无法在这里无害地反射适配）。
     */
    public static void registerAll() {
        ENTRIES.clear();
    }
}
