package com.tacz.guns.mcshim;

import net.minecraft.client.renderer.Tessellator;

/**
 * 对应 1.20.1 的 {@code com.mojang.blaze3d.vertex.Tesselator}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 的同名类 {@link Tessellator} 是<b>单例 + 立即绘制</b>模型
 * （{@code startDrawing}/{@code addVertex}/{@code draw}），而 1.20 的 {@code Tesselator}
 * 是<b>实例 + BufferBuilder 复用</b>模型（{@code Tesselator.getInstance().getBuilder()}）。
 *
 * <p>
 * 本类是 1.20 {@code Tesselator} 形状的薄封装：内部持有一个 1.7.10 {@link Tessellator}
 * 与一个可复用的 {@link BufferBuilder}，{@link #getBuilder()} 返回那个
 * {@link BufferBuilder}，{@link #clear()} 把攒的顶点丢掉。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.20 的 {@code Tesselator} 有 8 个 {@code BufferBuilder} 轮转（避免重入），
 * 本类只有 1 个，{@link #getBuilder()} 在未 {@link #clear()} 的情况下重复取用会拿到同一个实例。</li>
 * <li>1.20 的 {@code Tesselator} 内部 {@code BufferBuilder} 直接写 native 内存；
 * 本类走 {@link BufferBuilder} 的 Java 数组（见其注释）。</li>
 * <li>{@code Tesselator.getInstance()} 在 1.20 是「拿实例」，本类返回一个单例包装。</li>
 * </ul>
 */
public final class Tesselator {

    private static final Tesselator INSTANCE = new Tesselator();

    private final Tessellator tessellator = Tessellator.instance;
    private final BufferBuilder builder = new BufferBuilder(4096);

    private Tesselator() {}

    /** 对应 1.20 {@code Tesselator#getInstance()}。 */
    public static Tesselator getInstance() {
        return INSTANCE;
    }

    /** 对应 1.20 {@code Tesselator#getBuilder()}。 */
    public BufferBuilder getBuilder() {
        return builder;
    }

    /** 对应 1.20 {@code Tesselator#clear()}：丢弃当前攒的顶点。 */
    public void clear() {
        builder.discard();
    }

    /** 底层 1.7.10 {@link Tessellator}（需要 {@code setTranslation} 等操作时用）。 */
    public Tessellator getTessellator() {
        return tessellator;
    }
}
