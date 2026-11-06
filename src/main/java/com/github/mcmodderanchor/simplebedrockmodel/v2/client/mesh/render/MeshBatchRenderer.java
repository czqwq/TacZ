// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/render/MeshBatchRenderer.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render;

import java.nio.FloatBuffer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.VertexBuffer;
import com.tacz.guns.mcshim.RenderType;

/**
 * 管理每组材质、着色器及 VAO 的绘制状态；实例属性仍由对应几何的所有者管理。
 *
 * <h2>上游做什么</h2>
 * <p>
 * 1.20 侧每次换材质：{@code RenderType.setupRenderState()} → {@code RenderSystem.setShader(...)}
 * → 把 {@code ModelViewMat} / {@code ProjMat} / 光照方向等 uniform 灌进当前的
 * {@code ShaderInstance} → {@code VertexBuffer.bind()} → {@code VertexBuffer.draw()}。
 * 「实例矩阵」完全由 shader 用 {@code ModelViewMat × ModelViewMat} 完成。
 *
 * <h2>1.7.10 用什么实现</h2>
 * <p>
 * 1.7.10 <b>没有</b> {@code ShaderInstance} / {@code Uniform} / {@code Uniform.MODEL_VIEW_MATRIX}
 * 这一整套（{@code RenderSystem} 本身就是 1.17 才引入的），也没有 VAO。本类因此退化为：
 * <ul>
 * <li><b>材质切换</b>：仍然调 {@link RenderType#setupRenderState()} /
 * {@link RenderType#clearRenderState()}（mcshim 把它们翻译成固定管线的 GL 状态调用）；</li>
 * <li><b>几何绘制</b>：{@link #bind(VertexBuffer)} 记录当前几何，
 * {@link #drawItem}/{@link #drawWorld} 先 {@code glPushMatrix → glMultMatrix(delta) → draw()}，
 * 再 {@code glPopMatrix}。也就是说上游的 {@code ModelViewMat} 乘法在这里变成
 * <b>固定管线的 GL_MODELVIEW 栈操作</b>；</li>
 * <li><b>uniform 上传全部降级为 no-op</b>（{@link #uploadModelView}、
 * {@link #uploadSharedUniforms}、{@link #uploadInstanceLighting}、{@link #uploadItemLighting}、
 * {@link #uploadLocalLight}）——没有 shader 就没有 uniform；</li>
 * <li><b>光照方向</b>：上游把 {@code LIGHT0_DIRECTION}/{@code LIGHT1_DIRECTION} 灌进 shader，
 * 1.7.10 的对应物是固定管线的 {@code GL_LIGHT0/GL_LIGHT1}（本类不接管；
 * {@code RenderHelper.enableStandardItemLighting()} 已由调用方负责）。</li>
 * </ul>
 *
 * <h2>公开签名变化（1.7.10 平台缺口所致）</h2>
 * <ul>
 * <li>{@code uploadInstanceLighting(ShaderInstance, Matrix4f)} →
 * {@code uploadInstanceLighting(Object, Matrix4f)}；</li>
 * <li>{@code uploadItemLighting(ShaderInstance, Matrix3f)} → {@code uploadItemLighting(Object, Matrix3f)}；</li>
 * <li>{@code uploadLocalLight(Uniform, Matrix3f)} → {@code uploadLocalLight(Object, Matrix3f)}；</li>
 * <li>{@code uploadSharedUniforms(ShaderInstance, Matrix4f, Matrix4f)} →
 * {@code uploadSharedUniforms(Object, Matrix4f, Matrix4f)}。</li>
 * </ul>
 * 换成 {@code Object} 是因为 1.7.10 既没有 {@code ShaderInstance} 也没有 {@code Uniform}，
 * 无法提供原类型；方法体全部是 no-op，调用方传 {@code null} 也是合法用法。
 */
public class MeshBatchRenderer implements AutoCloseable {

    /** 复用的矩阵转置缓冲（GL 的 {@code glMultMatrix} 只吃 FloatBuffer）。 */
    private static final FloatBuffer MATRIX_SCRATCH = BufferUtils.createFloatBuffer(16);
    private final Matrix4f projection;
    private final Matrix4f baseView = new Matrix4f();
    private final Matrix4f delta = new Matrix4f();
    private RenderType material;
    private VertexBuffer bound;
    private int draws, setups, binds;

    public MeshBatchRenderer(Matrix4f projection) {
        this.projection = projection;
    }

    public boolean beginMaterial(RenderType next, Matrix4f modelView) {
        if (material == next) return true;
        finishMaterial();
        material = next; // 状态设置本身失败时也必须清理。
        setups++;
        next.setupRenderState();
        // 1.7.10 没有 shader，固定管线总是「可用」；上游在这里 shader == null 时返回 false。
        baseView.set(modelView);
        uploadSharedUniforms(null, modelView, projection);
        return true;
    }

    public void bind(VertexBuffer buffer) {
        if (bound == buffer) return;
        buffer.bind();
        bound = buffer;
        binds++;
    }

    public void drawItem(MeshDrawTransform transform) {
        uploadModelView(transform.modelView);
        uploadItemLighting(null, transform.inverseLinear);
        drawWith(transform.pose);
        draws++;
    }

    public void drawWorld(Matrix4f modelView, Matrix4f localTransform) {
        uploadModelView(modelView);
        uploadInstanceLighting(null, localTransform);
        // 上游的「实例矩阵」是 modelView × localTransform；而 1.7.10 的当前 GL_MODELVIEW
        // 已经等于 beginMaterial 收到的 baseView，所以这里只补上 delta = baseView⁻¹ × modelView。
        delta.set(baseView)
            .invert()
            .mul(modelView);
        drawWith(delta);
        draws++;
    }

    /** 把 {@code delta} 压进 GL_MODELVIEW，绘制当前几何，再复原。 */
    private void drawWith(Matrix4f delta) {
        if (bound == null) return;
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            MATRIX_SCRATCH.clear();
            delta.get(MATRIX_SCRATCH);
            MATRIX_SCRATCH.flip();
            GL11.glMultMatrix(MATRIX_SCRATCH);
            bound.draw();
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * 对应 1.20 {@code uploadModelView(Matrix4f)}：把 {@code ModelViewMat} uniform 传给 shader。
     *
     * <p>
     * TODO(L2): 上游 v2/client/mesh/render/MeshBatchRenderer.java:85 调
     * {@code shader.MODEL_VIEW_MATRIX.set(...).upload()}；1.7.10 无 ShaderInstance，
     * 本方法降级为 no-op（真正的变换由 {@link #drawItem}/{@link #drawWorld} 用
     * {@code glMultMatrix} 施加到 GL_MODELVIEW 上）。
     */
    public void uploadModelView(Matrix4f matrix) {
        // 无 shader，无 uniform。
    }

    public int draws() {
        return draws;
    }

    public int setups() {
        return setups;
    }

    public int binds() {
        return binds;
    }

    public void finishMaterial() {
        RenderType previous = material;
        if (previous == null) return;
        VertexBuffer.unbind();
        previous.clearRenderState();
        material = null;
        bound = null;
    }

    /** 仅首次异常收尾；保留清理错误到原异常，交给边界日志和路径禁用处理。 */
    public void abort(RuntimeException failure) {
        RenderType previous = material;
        material = null;
        bound = null;
        try {
            VertexBuffer.unbind();
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
        if (previous != null) {
            try {
                previous.clearRenderState();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
        }
    }

    @Override
    public void close() {
        finishMaterial();
    }

    /**
     * 对应 1.20 {@code uploadInstanceLighting(ShaderInstance, Matrix4f)}。
     *
     * <p>
     * TODO(L2): 上游 v2/client/mesh/render/MeshBatchRenderer.java:149 把世界空间光源方向
     * 乘以 {@code inverse(transform)} 的线性部分后写进 {@code LIGHT0_DIRECTION}/{@code LIGHT1_DIRECTION}
     * uniform（{@code GL_LIGHT0/1} 的 shader 版）。1.7.10 无 shader uniform，本方法降级为 no-op。
     *
     * <p>
     * <b>签名变化</b>：{@code ShaderInstance} → {@code Object}（1.7.10 无该类）。
     */
    public void uploadInstanceLighting(Object shader, Matrix4f transform) {
        // 无 shader，无 uniform。
    }

    /**
     * 对应 1.20 {@code uploadItemLighting(ShaderInstance, Matrix3f)}。
     *
     * <p>
     * TODO(L2): 上游 v2/client/mesh/render/MeshBatchRenderer.java:176 用 {@code RenderSystem#setupShaderLights}
     * 把物品坐标系下的两条光照方向写进 shader；1.7.10 由固定管线
     * {@code RenderHelper.enableStandardItemLighting()} / {@code GL_LIGHT0,1} 负责，
     * 本方法降级为 no-op。
     *
     * <p>
     * <b>签名变化</b>：{@code ShaderInstance} → {@code Object}（1.7.10 无该类）。
     */
    public static void uploadItemLighting(Object shader, Matrix3f inverseLinear) {
        // 无 shader，无 uniform。
    }

    /**
     * 对应 1.20 {@code uploadLocalLight(Uniform, Matrix3f)}。
     *
     * <p>
     * TODO(L2): 上游 v2/client/mesh/render/MeshBatchRenderer.java:183 读 uniform 的 float buffer、
     * 乘 {@code inverseLinear} 再 {@code upload()}；1.7.10 无 {@code Uniform}，降级为 no-op。
     *
     * <p>
     * <b>签名变化</b>：{@code Uniform} → {@code Object}（1.7.10 无该类）。
     */
    public static void uploadLocalLight(Object uniform, Matrix3f inverseLinear) {
        // 无 shader，无 uniform。
    }

    /**
     * 对应 1.20 {@code uploadSharedUniforms(ShaderInstance, Matrix4f, Matrix4f)}。
     *
     * <p>
     * TODO(L2): 上游 v2/client/mesh/render/MeshBatchRenderer.java:192 逐个设置
     * {@code Sampler0..11}/{@code ModelViewMat}/{@code ProjMat}/{@code InverseViewRotationMatrix}/
     * {@code ColorModulator}/{@code GlintAlpha}/{@code FogStart/FogEnd/FogColor/FogShape}/
     * {@code TextureMat}/{@code GameTime}/{@code Light0/1Direction} 然后 {@code shader.apply()}。
     * 1.7.10 这些全部由固定管线全局状态承担（纹理绑定、GL 矩阵栈、{@code glColor4f}、雾参数），
     * 没有 uniform 可写；本方法降级为 no-op。
     *
     * <p>
     * <b>签名变化</b>：{@code ShaderInstance} → {@code Object}（1.7.10 无该类）。
     */
    public static void uploadSharedUniforms(Object shader, Matrix4f baseView, Matrix4f projection) {
        // 无 shader，无 uniform。上游 MAX_SAMPLERS = 12 的采样器循环在这里没有对应物。
        if (baseView == null || projection == null) {
            throw new IllegalArgumentException("Shared uniform matrices are required");
        }
    }

}
