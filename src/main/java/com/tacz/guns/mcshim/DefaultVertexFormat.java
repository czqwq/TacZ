package com.tacz.guns.mcshim;

/**
 * 对应 1.20.1 的 {@code com.mojang.blaze3d.vertex.DefaultVertexFormat}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 没有这个概念，顶点布局由 {@code Tessellator} 内部固定（见 {@link VertexFormat} 的说明）。
 * 本类只提供与 1.20 同名的常量，供 {@link BufferBuilder#begin(VertexFormat.Mode, VertexFormat)}
 * 与 {@code RenderType} 传参。
 *
 * <h2>行为差异 / 降级点</h2>
 * 这些常量<b>只描述</b>上游期望收哪些顶点属性；{@link BufferBuilder} 在 1.7.10 上仍然把
 * 所有属性塞进 {@code Tessellator} 的固定 8-int 槽位，缺的属性用默认值（白色、uv=(0,0)、
 * 法线 (0,1,0)、全亮）。因此「用 POSITION 格式画」在 1.20 里是「不带颜色/uv」，
 * 在 1.7.10 里是「带默认颜色的固定槽位」，视觉上等价但顶点内存更大。
 */
public final class DefaultVertexFormat {

    /** 对应 1.20 {@code DefaultVertexFormat.BLIT_SCREEN}。 */
    public static final VertexFormat BLIT_SCREEN = new VertexFormat(
        "blit_screen",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.UV0_ELEMENT,
        VertexFormat.COLOR_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.EMPTY}。 */
    public static final VertexFormat EMPTY = new VertexFormat("empty");

    /** 对应 1.20 {@code DefaultVertexFormat.BLOCK}（方块模型：位置 + 颜色 + uv + 光照 + 法线）。 */
    public static final VertexFormat BLOCK = new VertexFormat(
        "block",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.COLOR_ELEMENT,
        VertexFormat.UV0_ELEMENT,
        VertexFormat.UV2_ELEMENT,
        VertexFormat.NORMAL_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.NEW_ENTITY}（1.20 的实体格式）。 */
    public static final VertexFormat NEW_ENTITY = new VertexFormat(
        "new_entity",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.COLOR_ELEMENT,
        VertexFormat.UV0_ELEMENT,
        VertexFormat.UV2_ELEMENT,
        VertexFormat.UV1_ELEMENT,
        VertexFormat.NORMAL_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.POSITION}。 */
    public static final VertexFormat POSITION = new VertexFormat("position", VertexFormat.POSITION_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.POSITION_COLOR}。 */
    public static final VertexFormat POSITION_COLOR = new VertexFormat(
        "position_color",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.COLOR_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.POSITION_COLOR_NORMAL}。 */
    public static final VertexFormat POSITION_COLOR_NORMAL = new VertexFormat(
        "position_color_normal",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.COLOR_ELEMENT,
        VertexFormat.NORMAL_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.POSITION_COLOR_LIGHTMAP}。 */
    public static final VertexFormat POSITION_COLOR_LIGHTMAP = new VertexFormat(
        "position_color_lightmap",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.COLOR_ELEMENT,
        VertexFormat.UV2_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.POSITION_TEX}。 */
    public static final VertexFormat POSITION_TEX = new VertexFormat(
        "position_tex",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.UV0_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.POSITION_TEX_COLOR}。 */
    public static final VertexFormat POSITION_TEX_COLOR = new VertexFormat(
        "position_tex_color",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.UV0_ELEMENT,
        VertexFormat.COLOR_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.POSITION_TEX_NORMAL}。 */
    public static final VertexFormat POSITION_TEX_NORMAL = new VertexFormat(
        "position_tex_normal",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.UV0_ELEMENT,
        VertexFormat.NORMAL_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR}。 */
    public static final VertexFormat POSITION_TEX_LIGHTMAP_COLOR = new VertexFormat(
        "position_tex_lightmap_color",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.UV0_ELEMENT,
        VertexFormat.UV2_ELEMENT,
        VertexFormat.COLOR_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL}。 */
    public static final VertexFormat POSITION_TEX_COLOR_NORMAL = new VertexFormat(
        "position_tex_color_normal",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.UV0_ELEMENT,
        VertexFormat.COLOR_ELEMENT,
        VertexFormat.NORMAL_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.POSITION_COLOR_TEX}（粒子常用）。 */
    public static final VertexFormat POSITION_COLOR_TEX = new VertexFormat(
        "position_color_tex",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.COLOR_ELEMENT,
        VertexFormat.UV0_ELEMENT);

    /** 对应 1.20 {@code DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP}。 */
    public static final VertexFormat POSITION_COLOR_TEX_LIGHTMAP = new VertexFormat(
        "position_color_tex_lightmap",
        VertexFormat.POSITION_ELEMENT,
        VertexFormat.COLOR_ELEMENT,
        VertexFormat.UV0_ELEMENT,
        VertexFormat.UV2_ELEMENT);

    private DefaultVertexFormat() {}
}
