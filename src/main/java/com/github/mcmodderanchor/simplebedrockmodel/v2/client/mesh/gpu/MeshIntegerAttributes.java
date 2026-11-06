// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/gpu/MeshIntegerAttributes.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import java.nio.IntBuffer;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;

import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * 网格后端持有的整数参数表；临时网格读取 light／overlay，世界网格仅读取实例 light。
 *
 * <h2>上游做了什么</h2>
 * <p>
 * 1.20 侧把 256×256 个 (u,v) 对铺成一张 {@code GL_RG_INTEGER} 参数表缓冲，再用
 * {@code glVertexAttribIPointer(lightIndex, 2, GL_INT, STRIDE, lightOffset)} +
 * {@code glVertexAttribDivisor(index, 1)} 把「每实例的 lightmap／overlay」挂到几何 VAO 上。
 * 这需要三样 1.7.10 拿不到的东西：<b>VAO 常量属性源</b>、<b>整数顶点属性</b>
 * （{@code glVertexAttribIPointer}，GL 3.0+ 核心／ARB 扩展）以及<b>实例化除频</b>
 * （{@code glVertexAttribDivisor}，GL 3.3 核心／ARB_instanced_arrays）。
 *
 * <h2>1.7.10 用什么实现</h2>
 * <p>
 * 1.7.10 的渲染是固定管线 + {@code Tessellator}，顶点只有 8 个 int 槽位，属性指针完全由
 * {@code Tessellator} 自己设置，<b>没有可挂载的顶点属性</b>。因此本类整体降级为
 * <b>「永远就绪的空操作」</b>：
 * <ul>
 * <li>{@link #supports} 恒返回 {@code true}——不是因为能力具备，而是因为「普通（非实例化）顶点路径」
 * 在 1.7.10 上总是可用；上游那套能力探测在这里没有意义；</li>
 * <li>{@link #ensureReady()} 恒 {@code true}（没有参数表需要分配）；</li>
 * <li>{@link #beginItem()}/{@link #endItem(int)}/{@link #setupLight}/{@link #setupPart}/{@link #divisor}
 * 全部 no-op；</li>
 * <li>{@link #parameterOffset(int)} 与 {@link #fillParameters(IntBuffer)} 是纯函数，
 * 与 GL 无关，<b>原样保留</b>（调用点仍需要一致的光照偏移语义）；</li>
 * <li>{@link #selectBackend} 也是纯函数，原样保留。</li>
 * </ul>
 *
 * <h2>功能降级</h2>
 * <ul>
 * <li><b>实例光照／overlay 不生效</b>：几何按捕获时烘焙的 lightmap 绘制，同一份共享网格被多个实例
 * 复用时它们的光照相同（1.20 里是每实例独立的）。</li>
 * <li><b>没有实例化绘制</b>：每个实例都是一次完整的 {@code Tessellator} 重放。</li>
 * </ul>
 */
public class MeshIntegerAttributes {

    static final int COORDINATES = 256;
    static final int STRIDE = 2 * Integer.BYTES;
    static final int TABLE_BYTES = COORDINATES * COORDINATES * STRIDE;

    private boolean failed;

    public enum Backend {
        UNSUPPORTED,
        CORE,
        ARB
    }

    /**
     * 1.7.10 的「GL 上下文身份」。
     *
     * <p>
     * 上游用 LWJGL3 的 {@code GL.getCapabilities()}（{@code GLCapabilities} 实例，随上下文切换而更换）。
     * 1.7.10 是 LWJGL2，等价物是 {@link GLContext#getCapabilities()}（{@link ContextCapabilities}）。
     * 没有当前上下文时它抛异常，这里吞掉并返回 {@code null}，让调用方走「无上下文」分支。
     *
     * <p>
     * <b>这是本移植新增的辅助方法</b>（上游没有），因为 gl 上下文探测在 1.7.10 侧要额外防异常。
     */
    @Nullable
    public static ContextCapabilities currentContext() {
        try {
            return GLContext.getCapabilities();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 世界网格只替换 UV2；UV1 继续读取几何，状态随句柄和 VAO 的生命周期保存。 */
    public static class LightState {

        private VertexFormat format;
        private int geometryBuffer = -1;
        private int lightIndex = -1;

        public void invalidate() {
            // 1.7.10 无顶点属性可挂载，无需失效。
        }

        /**
         * 上游按「实际上传格式」算 UV2 的属性索引。
         *
         * <p>
         * TODO(L2): 上游 v2/client/mesh/gpu/MeshIntegerAttributes.java:62 用 {@code getElements().indexOf(
         * DefaultVertexFormat.ELEMENT_UV2)} 并校验 {@code maxAttributes}；1.7.10 没有顶点属性数组，
         * 这里只记录状态并恒返回 {@code true}（几何总能画，只是不带每实例光照）。
         */
        public boolean prepareLayout(VertexBuffer geometry, int maxAttributes) {
            VertexFormat uploaded = geometry.getFormat();
            if (uploaded == null || geometry.isInvalid()) return false;
            if (format != uploaded || geometryBuffer != geometry.vertexBufferId) {
                format = uploaded;
                geometryBuffer = geometry.vertexBufferId;
                lightIndex = uploaded.getElements()
                    .indexOf(VertexFormat.UV2_ELEMENT);
            }
            return true;
        }
    }

    public static Backend selectBackend(boolean gl30, long integerPointer, boolean gl33, long corePointer,
        boolean arbInstancing, long arbPointer) {
        if (!gl30 || integerPointer == 0) return Backend.UNSUPPORTED;
        if (gl33 && corePointer != 0) return Backend.CORE;
        return arbInstancing && arbPointer != 0 ? Backend.ARB : Backend.UNSUPPORTED;
    }

    /** 状态由各 Part 持有，不放入全局光照／偏移缓存；新 Part 的属性来源初始为未挂载。 */
    public static class PartState {

        private VertexFormat format;
        private int geometryBuffer = -1;
        private int overlayIndex = -1;
        private int lightIndex = -1;

        public void invalidate() {
            // 1.7.10 无顶点属性可挂载，无需失效。
        }

        /**
         * TODO(L2): 上游 v2/client/mesh/gpu/MeshIntegerAttributes.java:104 会读出 UV1/UV2 的属性索引、
         * 步长、GL 类型与字节偏移（{@code VertexFormatElement#getType()/getCount()/getByteSize()}、
         * {@code VertexFormat#getOffset(int)}）。mcshim 的 {@code VertexFormat}/{@code Element}
         * 是「描述性清单」，没有这些方法；1.7.10 也没有属性指针，故只记录索引并恒返回 {@code true}。
         */
        public boolean prepareLayout(VertexBuffer geometry, int maxAttributes) {
            VertexFormat uploaded = geometry.getFormat();
            if (uploaded == null || geometry.isInvalid()) return false;
            if (format != uploaded || geometryBuffer != geometry.vertexBufferId) {
                format = uploaded;
                geometryBuffer = geometry.vertexBufferId;
                overlayIndex = uploaded.getElements()
                    .indexOf(VertexFormat.UV1_ELEMENT);
                lightIndex = uploaded.getElements()
                    .indexOf(VertexFormat.UV2_ELEMENT);
            }
            return true;
        }
    }

    /**
     * 对应 1.20 {@code MeshIntegerAttributes#supports(GLCapabilities, int, int)}。
     *
     * <p>
     * <b>签名变化</b>：{@code GLCapabilities}（LWJGL3）→ {@link ContextCapabilities}（LWJGL2）。
     *
     * <p>
     * <b>降级点</b>：恒返回 {@code !failed}。1.7.10 没有整数顶点属性／实例化除频，
     * 这里的语义从「能不能做每实例光照」变成「普通顶点路径是否可用」，后者总是可用。
     */
    public boolean supports(@Nullable ContextCapabilities current, int packedLight, int packedOverlay) {
        // TODO(L2): 上游 v2/client/mesh/gpu/MeshIntegerAttributes.java:129 按上下文探测
        // OpenGL30/glVertexAttribIPointer/OpenGL33/glVertexAttribDivisor/ARB_instanced_arrays；
        // 1.7.10 固定管线没有这些能力，降级为「普通顶点路径恒可用」。
        return !failed;
    }

    /** 检查两个完整的无符号 16 位分量，不截断，也不钳制数值。（纯函数，与 1.20 一致。） */
    public static long parameterOffset(int packed) {
        int u = packed & 0xFFFF;
        int v = packed >>> 16;
        return (u | v) < COORDINATES ? ((long) v * COORDINATES + u) * STRIDE : -1;
    }

    /** 纯函数，与 1.20 一致；1.7.10 侧不再有参数表缓冲，保留给调试统计用。 */
    public static void fillParameters(IntBuffer destination) {
        for (int v = 0; v < COORDINATES; v++) {
            for (int u = 0; u < COORDINATES; u++) destination.put(u)
                .put(v);
        }
    }

    public boolean layoutSupported(PartState state, VertexBuffer geometry) {
        return state.prepareLayout(geometry, 0);
    }

    public boolean layoutSupported(LightState state, VertexBuffer geometry) {
        return state.prepareLayout(geometry, 0);
    }

    /** 对应 1.20 {@code ensureReady()}：1.7.10 没有参数表缓冲要分配，恒就绪。 */
    public boolean ensureReady() {
        RenderSystem.assertOnRenderThread();
        // TODO(L2): 上游在此处分配 256×256×2 int 的参数表并 glBufferData；1.7.10 无 VAO/属性指针，
        // 参数表没有收件人，降级为 no-op。
        return !failed;
    }

    /** 对应 1.20 {@code beginItem()}：no-op，返回 -1 表示「没有需要恢复的绑定」。 */
    public int beginItem() {
        // TODO(L2): 上游绑定参数表缓冲并返回旧的 GL_ARRAY_BUFFER_BINDING；1.7.10 无此绑定。
        return -1;
    }

    /** 对应 1.20 {@code endItem(int)}：no-op。 */
    public void endItem(int previous) {
        // 无 GL 绑定需要恢复。
    }

    /** 在已绑定的世界网格 VAO 上设置实例 UV2：1.7.10 无 VAO，no-op。 */
    public void setupLight(LightState state, long lightOffset) {
        // TODO(L2): 上游 v2/client/mesh/gpu/MeshIntegerAttributes.java:228 挂实例 UV2 并设 divisor=1；
        // 1.7.10 无整数顶点属性，降级为 no-op（世界网格按捕获时烘焙的光照绘制）。
    }

    /** 临时网格的每实例 light／overlay：1.7.10 无 VAO，no-op。 */
    public void setupPart(PartState state, boolean instanceLight, long lightOffset, long overlayOffset) {
        // TODO(L2): 上游 v2/client/mesh/gpu/MeshIntegerAttributes.java:247 挂 overlay/light 属性；
        // 1.7.10 顶点格式没有这两个槽位，降级为 no-op。
    }

    /** 实例化除频：1.7.10 无实例化绘制，no-op。 */
    public void divisor(int index, int value) {
        // TODO(L2): 上游按 core/ARB 调 GL33.glVertexAttribDivisor / ARBInstancedArrays...；
        // 1.7.10 固定管线不支持实例化，降级为 no-op。
    }

    /** 对应 1.20 {@code close()}：没有 GL 缓冲可删，只清失败标记。 */
    public void close() {
        failed = false;
    }
}
