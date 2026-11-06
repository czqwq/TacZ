package com.tacz.guns.mcshim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.opengl.GL11;

/**
 * 对应 1.20.1 的 {@code com.mojang.blaze3d.vertex.VertexFormat}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 <b>没有</b> {@code VertexFormat}：{@code Tessellator} 的顶点布局是硬编码的
 * （8 个 int 槽位：x/y/z + u/v + color + normal + lightmap，见 {@code Tessellator#addVertex}），
 * 元素是否参与绘制靠 {@code hasTexture/hasColor/hasNormals/hasBrightness} 标志位。
 *
 * <p>
 * 本类是「元素清单 + 绘制模式」的<b>描述性</b>对象，只用于让上游的形状能编译、让
 * {@link BufferBuilder} 知道自己该收哪些属性；它<b>不</b>改变 1.7.10 的顶点内存布局。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>{@link #getVertexSize()} 返回的是 1.20 的字节大小语义（按元素个数 × 建议字节数估算），
 * <b>不要</b>用它算 1.7.10 的顶点步长（那是固定的 32 字节）。</li>
 * <li>{@link #getElements()} 直接返回常量清单，仅供调试/日志。</li>
 * <li>{@link Mode} 的 {@link Mode#indexCount(int)} 与 1.20 一致。</li>
 * </ul>
 */
public final class VertexFormat {

    /** 元素类型，对应 1.20 {@code VertexFormatElement.Type}。 */
    public static final class Element {

        /** 元素名（仅调试用）。 */
        public final String name;
        /** 该元素占用几个 float/int（1=标量，3=三维向量，4=RGBA）。 */
        public final int count;

        public Element(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** 位置元素。 */
    public static final Element POSITION_ELEMENT = new Element("Position", 3);
    /** 颜色元素。 */
    public static final Element COLOR_ELEMENT = new Element("Color", 4);
    /** 纹理坐标元素。 */
    public static final Element UV0_ELEMENT = new Element("UV0", 2);
    /** 法线元素。 */
    public static final Element NORMAL_ELEMENT = new Element("Normal", 3);
    /** overlay 元素（1.7.10 无对应槽位）。 */
    public static final Element UV1_ELEMENT = new Element("UV1", 2);
    /** 光照贴图元素。 */
    public static final Element UV2_ELEMENT = new Element("UV2", 2);

    /** 对应 1.20 {@code DefaultVertexFormat.POSITION_COLOR}（INVERT 扇面等纯位置+颜色几何用）。 */
    public static final VertexFormat POSITION_COLOR = new VertexFormat(
        "position_color",
        POSITION_ELEMENT,
        COLOR_ELEMENT);

    /**
     * 对应 1.20 {@code VertexFormat.Mode}。
     *
     * <p>
     * {@link #glMode} 是可直接传给 1.7.10 {@code Tessellator#startDrawing(int)} 的 GL 枚举值。
     */
    public enum Mode {

        /** 点。 */
        POINTS(GL11.GL_POINTS, 1),
        /** 线段。 */
        LINES(GL11.GL_LINES, 2),
        /** 连续线段。 */
        LINE_STRIP(GL11.GL_LINE_STRIP, 1),
        /** 调试线段（1.7.10 只有 {@code GL_LINES}，降级为 LINES）。 */
        DEBUG_LINES(GL11.GL_LINES, 2),
        /** 调试连续线段（降级为 LINE_STRIP）。 */
        DEBUG_LINE_STRIP(GL11.GL_LINE_STRIP, 1),
        /** 三角形。 */
        TRIANGLES(GL11.GL_TRIANGLES, 3),
        /** 三角带。 */
        TRIANGLE_STRIP(GL11.GL_TRIANGLE_STRIP, 1),
        /** 三角扇。 */
        TRIANGLE_FAN(GL11.GL_TRIANGLE_FAN, 1),
        /** 四边形。 */
        QUADS(GL11.GL_QUADS, 4);

        private final int glMode;
        private final int primitiveLength;

        Mode(int glMode, int primitiveLength) {
            this.glMode = glMode;
            this.primitiveLength = primitiveLength;
        }

        /** 对应的 GL 绘制模式常量。 */
        public int glMode() {
            return glMode;
        }

        /** 一个「图元」由几个顶点组成；{@code LINE_STRIP/TRIANGLE_STRIP/TRIANGLE_FAN} 返回 1（每个新顶点出一个图元）。 */
        public int primitiveLength() {
            return primitiveLength;
        }

        /** 对应 1.20 {@code Mode#indexCount(int vertexCount)}：{@code vertexCount} 个顶点能组成几个图元。 */
        public int indexCount(int vertexCount) {
            switch (this) {
                case LINE_STRIP:
                case TRIANGLE_FAN:
                    return vertexCount;
                case TRIANGLE_STRIP:
                    return vertexCount - 2;
                default:
                    return vertexCount / primitiveLength;
            }
        }
    }

    private final String name;
    private final List<Element> elements;
    private final int vertexSize;

    public VertexFormat(String name, Element... elements) {
        this.name = name;
        List<Element> list = new ArrayList<>();
        int size = 0;
        for (Element element : elements) {
            list.add(element);
            size += element.count * Float.BYTES;
        }
        this.elements = Collections.unmodifiableList(list);
        this.vertexSize = size;
    }

    /** 名称（调试用）。 */
    public String getName() {
        return name;
    }

    /** 元素清单。 */
    public List<Element> getElements() {
        return elements;
    }

    /** 单个顶点的字节大小（<b>1.20 语义</b>，1.7.10 实际固定 32 字节）。 */
    public int getVertexSize() {
        return vertexSize;
    }

    /** 是否包含指定元素。 */
    public boolean contains(Element element) {
        return elements.contains(element);
    }

    /** 是否带颜色元素。 */
    public boolean hasColor() {
        return contains(COLOR_ELEMENT);
    }

    /** 是否带纹理坐标元素。 */
    public boolean hasUv() {
        return contains(UV0_ELEMENT);
    }

    /** 是否带法线元素。 */
    public boolean hasNormal() {
        return contains(NORMAL_ELEMENT);
    }

    /** 是否带光照贴图元素。 */
    public boolean hasLightmap() {
        return contains(UV2_ELEMENT);
    }

    @Override
    public String toString() {
        return "VertexFormat[" + name + "]";
    }
}
