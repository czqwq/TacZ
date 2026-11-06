package com.tacz.guns.mcshim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对应 1.20.1 的 {@code net.minecraft.client.renderer.blockentity.BlockEntityRenderers}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 <b>没有</b> {@code BlockEntityRenderers}：方块实体渲染器（1.7.10 叫
 * {@code TileEntitySpecialRenderer}）通过
 * {@code ClientRegistry.registerTileEntity(Class<? extends TileEntity>, String, TileEntitySpecialRenderer)}
 * 直接注册，或由 {@code TileEntityRendererDispatcher.mapSpecialRenderers} 反射登记。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>与 {@link EntityRenderers} 相同：本类只是登记表，{@link Object} 类型参数，
 * 真正的注册由 {@code ClientProxy} 在初始化阶段完成。</li>
 * <li>1.7.10 的 {@code TileEntitySpecialRenderer} 是泛型类且<b>每实例绑定一个绑定点</b>，
 * 与 1.20 的 {@code BlockEntityRenderer} 完全不同，移植后必须重写渲染器本身。</li>
 * </ul>
 */
public final class BlockEntityRenderers {

    /** 注册项：方块实体类（或 1.20 的 BlockEntityType）+ 渲染器工厂。 */
    public static final class Entry {

        public final Object blockEntityClassOrType;
        public final Object rendererProvider;

        Entry(Object blockEntityClassOrType, Object rendererProvider) {
            this.blockEntityClassOrType = blockEntityClassOrType;
            this.rendererProvider = rendererProvider;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private BlockEntityRenderers() {}

    /** 对应 1.20 {@code BlockEntityRenderers#register(BlockEntityType, BlockEntityRendererProvider)}。 */
    public static void register(Object blockEntityClassOrType, Object rendererProvider) {
        ENTRIES.add(new Entry(blockEntityClassOrType, rendererProvider));
    }

    /** 已登记的项（只读）。 */
    public static List<Entry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    /** 由 {@code ClientProxy} 在初始化阶段消费并清空登记表。 */
    public static void registerAll() {
        ENTRIES.clear();
    }
}
