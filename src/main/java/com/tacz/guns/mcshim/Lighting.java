package com.tacz.guns.mcshim;

import net.minecraft.client.renderer.RenderHelper;

/**
 * 对应 1.20.1 的 {@code net.minecraft.client.renderer.Lighting}（GUI/物品展示的固定光照设置）。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 有 {@link RenderHelper}：
 * <ul>
 * <li>{@code enableStandardItemLighting()} —— 3D 物品/实体的标准光照（两个方向光 + 环境光）</li>
 * <li>{@code enableGUIStandardItemLighting()} —— GUI 里的斜向单光源</li>
 * <li>{@code disableStandardItemLighting()} —— 关光照</li>
 * </ul>
 * 1.20 把它们改名为 {@code Lighting.setupFor3DItems()} / {@code setupForFlatItems()} /
 * {@code setupForEntityInInventory()}，本类就是这两个名字的直通。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>{@code setupForFlatItems()} 在 1.20 也会调整 {@code GL_NORMALIZE} 与
 * {@code GL_RESCALE_NORMAL}；1.7.10 侧由 {@link RenderHelper} 内部处理，本类不额外补充。</li>
 * <li>{@code setupForEntityInInventory()} 在 1.20 是独立实现，1.7.10 侧退化为
 * {@code enableStandardItemLighting()}（视觉差异：光照角度略有不同）。</li>
 * </ul>
 */
public final class Lighting {

    private Lighting() {}

    /** 对应 1.20 {@code Lighting#setupForFlatItems()} → {@link RenderHelper#enableGUIStandardItemLighting()}。 */
    public static void setupForFlatItems() {
        RenderHelper.enableGUIStandardItemLighting();
    }

    /** 对应 1.20 {@code Lighting#setupFor3DItems()} → {@link RenderHelper#enableStandardItemLighting()}。 */
    public static void setupFor3DItems() {
        RenderHelper.enableStandardItemLighting();
    }

    /** 对应 1.20 {@code Lighting#setupForEntityInInventory()}（1.7.10 退化）。 */
    public static void setupForEntityInInventory() {
        RenderHelper.enableStandardItemLighting();
    }

    /** 关闭标准光照（1.20 里没有独立方法，对应 {@link RenderHelper#disableStandardItemLighting()}）。 */
    public static void disableStandardItemLighting() {
        RenderHelper.disableStandardItemLighting();
    }
}
