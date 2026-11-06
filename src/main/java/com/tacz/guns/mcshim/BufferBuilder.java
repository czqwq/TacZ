package com.tacz.guns.mcshim;

import java.util.Arrays;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/**
 * 对应 1.20.1 的 {@code com.mojang.blaze3d.vertex.BufferBuilder}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 没有可复用的顶点构建器，唯一的顶点 API 是 {@link Tessellator}：
 * 它内部有一块 {@code int[] rawBuffer}（每顶点 8 个 int），但<b>同一时刻只能有一个 draw call</b>
 * （{@code startDrawing} 与 {@code draw} 必须成对，且不能嵌套）。
 *
 * <p>
 * 因为 {@link MultiBufferSource.BufferSource} 需要按 {@code RenderType} 分别攒顶点、
 * 最后再统一 {@code endBatch()}，本类改为<b>自己存顶点</b>（一组 float 数组），
 * 等到 {@link #end()} 时把快照交给 {@link RenderedBuffer}，
 * 由 {@link BufferUploader#draw(RenderedBuffer)} 再灌进 {@code Tessellator} 绘制。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li><b>没有 VBO / 显存常驻</b>：1.20 的 {@code BufferBuilder} 直接写 native 内存并上传成 VBO；
 * 本类用 Java 堆上的 float 数组，绘制时逐顶点喂 {@code Tessellator}。<b>性能显著低于 1.20</b>，
 * 顶点量大的模型（枪械网格）会有明显开销。</li>
 * <li><b>不支持 {@code begin} 后继续 {@code end} 复用</b>：1.20 的 RenderedBuffer 可以延后提交，
 * 本类的 {@code end()} 只是「封口」，真正的 GL 绘制发生在 {@link BufferUploader} 或
 * {@link MultiBufferSource.BufferSource#endBatch()} 里。</li>
 * <li><b>overlay 属性被丢弃</b>（1.7.10 顶点格式无该槽位）。</li>
 * <li><b>index 缓冲 / 顶点排序</b>：1.7.10 的 {@code Tessellator#draw()} 用
 * {@code glDrawArrays}，本类同样只做顺序绘制，不做面排序。</li>
 * <li>{@code initialCapacity} 只用于预分配 Java 数组，与 GL 缓冲无关。</li>
 * </ul>
 */
public class BufferBuilder implements VertexConsumer {

    /**
     * 对应 1.20 {@code BufferBuilder.RenderedBuffer}：一次 {@link BufferBuilder#end()} 之后的
     * 顶点快照。
     *
     * <p>
     * <b>降级点</b>：1.20 里它持有 native {@code ByteBuffer} 与 {@code DrawState}，
     * {@code release()} 释放 native 内存；本类只是 Java 数组的持有者，{@code release()} 是 no-op
     * （仅为让上游 {@code builder.end().release()} 的写法能编译）。
     */
    public static final class RenderedBuffer {

        private final float[] positions;
        private final float[] colors;
        private final float[] uvs;
        private final float[] lights;
        private final float[] normals;
        private final int vertexCount;
        private final VertexFormat.Mode mode;
        private final VertexFormat format;

        RenderedBuffer(float[] positions, float[] colors, float[] uvs, float[] lights, float[] normals, int vertexCount,
            VertexFormat.Mode mode, VertexFormat format) {
            this.positions = positions;
            this.colors = colors;
            this.uvs = uvs;
            this.lights = lights;
            this.normals = normals;
            this.vertexCount = vertexCount;
            this.mode = mode;
            this.format = format;
        }

        /** 顶点数。 */
        public int vertexCount() {
            return vertexCount;
        }

        /** 绘制模式。 */
        public VertexFormat.Mode mode() {
            return mode;
        }

        /** 顶点格式。 */
        public VertexFormat format() {
            return format;
        }

        /** 是否没有任何顶点。 */
        public boolean isEmpty() {
            return vertexCount == 0;
        }

        /** 对应 1.20 {@code RenderedBuffer#release()}：本实现无 native 内存，no-op。 */
        public void release() {
            // 1.7.10 版无 native 内存需要释放
        }

        // 供 BufferUploader（同包）读取顶点数据
        float[] rawPositions() {
            return positions;
        }

        float[] rawColors() {
            return colors;
        }

        float[] rawUvs() {
            return uvs;
        }

        float[] rawLights() {
            return lights;
        }

        float[] rawNormals() {
            return normals;
        }
    }

    private final int initialCapacity;

    private float[] positions;
    private float[] colors;
    private float[] uvs;
    private float[] lights;
    private float[] normals;
    private int vertexCount;

    private VertexFormat.Mode mode = VertexFormat.Mode.QUADS;
    private VertexFormat format = DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP;
    private boolean building;

    // 当前顶点的待写属性
    private boolean hasColor;
    private float red = 1.0F;
    private float green = 1.0F;
    private float blue = 1.0F;
    private float alpha = 1.0F;

    private boolean hasUv;
    private float u;
    private float v;

    private boolean hasLight;
    private int packedLight = LightTexture.FULL_BRIGHT;

    private boolean hasNormal;
    private float normalX;
    private float normalY = 1.0F;
    private float normalZ;

    /** 待提交顶点（{@link #vertex} 记录，{@link #endVertex()} 落地）。 */
    private boolean hasPendingVertex;
    private float pendingX;
    private float pendingY;
    private float pendingZ;

    /** 对应 1.20 {@code new BufferBuilder(int initialCapacity)}（单位：顶点数）。 */
    public BufferBuilder(int initialCapacity) {
        this.initialCapacity = Math.max(4, initialCapacity);
        allocate(this.initialCapacity);
    }

    private void allocate(int capacity) {
        positions = new float[capacity * 3];
        colors = new float[capacity * 4];
        uvs = new float[capacity * 2];
        lights = new float[capacity];
        normals = new float[capacity * 3];
    }

    /** 对应 1.20 {@code BufferBuilder#begin(VertexFormat.Mode, VertexFormat)}。 */
    public void begin(VertexFormat.Mode mode, VertexFormat format) {
        if (building) {
            throw new IllegalStateException("BufferBuilder is already building");
        }
        this.mode = mode;
        this.format = format;
        this.vertexCount = 0;
        this.hasPendingVertex = false;
        this.building = true;
    }

    /** 对应 1.20 {@code BufferBuilder#building()}。 */
    public boolean building() {
        return building;
    }

    /** 对应 1.20 {@code BufferBuilder#discard()}：丢弃已攒的顶点，不产生 RenderedBuffer。 */
    public void discard() {
        this.vertexCount = 0;
        this.hasPendingVertex = false;
        this.building = false;
    }

    /** 当前绘制模式（1.20 里通过 {@code RenderType#mode()} 拿到的就是它）。 */
    public VertexFormat.Mode mode() {
        return mode;
    }

    /** 当前顶点格式。 */
    public VertexFormat format() {
        return format;
    }

    /** 已攒的顶点数。 */
    public int vertexCount() {
        return vertexCount;
    }

    /**
     * 对应 1.20 {@code BufferBuilder#end()}（返回 {@code RenderedBuffer} 或 {@code null}）。
     *
     * <p>
     * 顶点数为 0 时返回 {@code null}（与 1.20 一致）。
     */
    public RenderedBuffer end() {
        if (!building) {
            return null;
        }
        building = false;
        if (vertexCount == 0) {
            return null;
        }
        int posLen = vertexCount * 3;
        int colLen = vertexCount * 4;
        int uvLen = vertexCount * 2;
        int norLen = vertexCount * 3;
        return new RenderedBuffer(
            Arrays.copyOf(positions, posLen),
            Arrays.copyOf(colors, colLen),
            Arrays.copyOf(uvs, uvLen),
            Arrays.copyOf(lights, vertexCount),
            Arrays.copyOf(normals, norLen),
            vertexCount,
            mode,
            format);
    }

    // ---------------------------------------------------------------- VertexConsumer

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        if (!building) {
            throw new IllegalStateException("BufferBuilder not building; call begin(mode, format) first");
        }
        // 与 1.20 一致：vertex(...) 只记录位置，颜色/uv/light/法线在之后、endVertex() 之前补，
        // 顶点在 endVertex() 时才真正落进数组（上游调用点均为
        // vertex(...).color(...).uv(...).lightmap(...).normal(...).endVertex()）。
        this.pendingX = (float) x;
        this.pendingY = (float) y;
        this.pendingZ = (float) z;
        this.hasPendingVertex = true;
        return this;
    }

    @Override
    public BufferBuilder color(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        this.hasColor = true;
        return this;
    }

    @Override
    public BufferBuilder color(int red, int green, int blue, int alpha) {
        return color(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
    }

    @Override
    public BufferBuilder uv(float u, float v) {
        this.u = u;
        this.v = v;
        this.hasUv = true;
        return this;
    }

    /** <b>降级点</b>：1.7.10 顶点格式无 overlay 槽位，此处丢弃。 */
    @Override
    public BufferBuilder overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public BufferBuilder uv2(int u, int v) {
        this.packedLight = (u & 0xFFFF) | ((v & 0xFFFF) << 16);
        this.hasLight = true;
        return this;
    }

    @Override
    public BufferBuilder normal(float x, float y, float z) {
        this.normalX = x;
        this.normalY = y;
        this.normalZ = z;
        this.hasNormal = true;
        return this;
    }

    @Override
    public void endVertex() {
        if (!hasPendingVertex) {
            return;
        }
        hasPendingVertex = false;
        ensureCapacity(vertexCount + 1);
        int i = vertexCount;
        positions[i * 3] = pendingX;
        positions[i * 3 + 1] = pendingY;
        positions[i * 3 + 2] = pendingZ;
        // 属性沿用「最后一次设置的值」（与 1.20 BufferBuilder 相同），未被设置过的取 1.20 缺省值。
        colors[i * 4] = hasColor ? red : 1.0F;
        colors[i * 4 + 1] = hasColor ? green : 1.0F;
        colors[i * 4 + 2] = hasColor ? blue : 1.0F;
        colors[i * 4 + 3] = hasColor ? alpha : 1.0F;
        uvs[i * 2] = hasUv ? u : 0.0F;
        uvs[i * 2 + 1] = hasUv ? v : 0.0F;
        lights[i] = hasLight ? packedLight : LightTexture.FULL_BRIGHT;
        normals[i * 3] = hasNormal ? normalX : 0.0F;
        normals[i * 3 + 1] = hasNormal ? normalY : 1.0F;
        normals[i * 3 + 2] = hasNormal ? normalZ : 0.0F;
        vertexCount++;
    }

    private void ensureCapacity(int required) {
        int capacity = positions.length / 3;
        if (required <= capacity) {
            return;
        }
        int newCapacity = Math.max(required, capacity * 2);
        positions = Arrays.copyOf(positions, newCapacity * 3);
        colors = Arrays.copyOf(colors, newCapacity * 4);
        uvs = Arrays.copyOf(uvs, newCapacity * 2);
        lights = Arrays.copyOf(lights, newCapacity);
        normals = Arrays.copyOf(normals, newCapacity * 3);
    }

    // ---------------------------------------------------------------- 内部给 BufferUploader 用

    /** 缺省绘制模式常量，方便调用方传 {@code GL11} 值。 */
    public static int defaultDrawMode() {
        return GL11.GL_QUADS;
    }

    /** 底部 Tessellator 单例（1.7.10 唯一可用的顶点提交入口）。 */
    public static Tessellator tessellator() {
        return Tessellator.instance;
    }
}
