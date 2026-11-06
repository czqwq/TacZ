package com.tacz.guns.mcshim;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/**
 * {@link VertexConsumer} 在 1.7.10 的直写实现：每个 {@link #endVertex()} 立刻把顶点写进
 * {@link Tessellator} 的顶点缓冲。
 *
 * <h2>对应关系</h2>
 * 1.20 里 {@code VertexConsumer} 的实现是一堆 {@code VertexBuffer}/{@code BufferBuilder}；
 * 1.7.10 唯一可用的顶点缓冲就是 {@link Tessellator}。本类把链式调用翻译成
 * {@code setTextureUV / setColorRGBA_F / setBrightness / setNormal / addVertex}。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li><b>overlay（受伤红闪）被丢弃</b>：1.7.10 的 {@link Tessellator} 只有 pos/uv/color/normal/lightmap
 * 五个顶点属性，没有 overlay 通道。{@link #overlayCoords(int, int)} 是 no-op。
 * 需要红闪效果请在绘制后用 {@code GL11.GL_BLEND} + {@code glColor4f} 叠一层。</li>
 * <li><b>没有 drawMode 之外的状态</b>：混合/裁剪/深度都由调用方通过 {@link RenderType#setup()} 控制，
 * 本类不碰 GL 状态（除了 {@link #finish()} 里的 {@code Tessellator#draw()}）。</li>
 * <li><b>顶点在 {@link #endVertex()} 才落地</b>：1.7.10 {@code Tessellator#addVertex} 会在调用瞬间把
 * 「当前 uv/color/normal/brightness」快照写进定长 8-int 槽位
 * （{@code build/rfg/minecraft-src/java/net/minecraft/client/renderer/Tessellator.java:407-433}），
 * 而 1.20 的 {@code VertexConsumer} 允许属性写在 {@code vertex(...)} <b>之后</b>、{@code endVertex()} 之前
 * （上游与本工程的调用点全是 {@code vertex(...).color(...).uv(...).lightmap(...).normal(...).endVertex()}）。
 * 因此本类把真正的 {@code addVertex} 延后到 {@link #endVertex()}；若在 {@code vertex(...)} 里就 addVertex，
 * 属性会整体错位一个顶点。</li>
 * <li><b>属性是「当前值」并跨顶点保留</b>：1.20 的 {@code BufferBuilder} 同样保留最后一次设置的值直到被覆盖，
 * 因此本类<b>不</b>在每个顶点后清空 uv/color/light/normal。</li>
 * <li><b>整缓冲属性集一致</b>：{@code Tessellator} 的 {@code hasTexture/hasColor/hasBrightness/hasNormals}
 * 是<b>整缓冲</b>级别标志（{@code draw()} 用它算顶点 stride），中途变化会让已写入顶点错位。
 * 本类在第一个顶点落地时锁定「本缓冲写哪几个属性」，之后每个顶点用同一属性集，
 * 缺省值取 1.20 语义（uv=(0,0)、白、light=FULL_BRIGHT）。</li>
 * </ul>
 *
 * <p>
 * 典型用法：
 * 
 * <pre>
 * {@code
 * TessellatorVertexConsumer consumer = new TessellatorVertexConsumer(
 *         net.minecraft.client.renderer.Tessellator.instance, GL11.GL_QUADS);
 * ... consumer.vertex(x, y, z).color(255, 255, 255, 255).uv(u, v).endVertex(); ...
 * consumer.finish();   // 内部 Tessellator#draw()
 * }
 * </pre>
 */
public class TessellatorVertexConsumer implements VertexConsumer {

    private final Tessellator tessellator;
    private final int drawMode;

    private boolean started;
    private boolean finished;

    private double u;
    private double v;
    private boolean hasUv;

    private boolean hasColor;
    private int red = 255;
    private int green = 255;
    private int blue = 255;
    private int alpha = 255;

    private boolean hasLight;
    private int packedLight = LightTexture.FULL_BRIGHT;

    private boolean hasNormal;
    private float normalX;
    private float normalY = 1.0F;
    private float normalZ;

    private boolean hasDefaultColor = true;
    private int defaultRed = 255;
    private int defaultGreen = 255;
    private int defaultBlue = 255;
    private int defaultAlpha = 255;

    private boolean hasDefaultNormal;
    private float defaultNormalX;
    private float defaultNormalY = 1.0F;
    private float defaultNormalZ;

    /** 当前待提交顶点的位置（{@link #vertex} 只记录，真正 addVertex 在 {@link #endVertex()}）。 */
    private boolean hasVertex;
    private double vertexX;
    private double vertexY;
    private double vertexZ;

    /**
     * 整缓冲属性集是否已锁定。{@code Tessellator} 的 {@code hasTexture/hasColor/hasBrightness/hasNormals}
     * 是整缓冲级别标志，一旦变化会让已写入的顶点错位，所以用第一个顶点决定本缓冲的属性集。
     */
    private boolean attributeMaskLocked;
    private boolean maskUv;
    private boolean maskLight;
    private boolean maskNormal;

    /** 用默认的 {@code GL11.GL_QUADS} 绘制模式创建 consumer。 */
    public TessellatorVertexConsumer(Tessellator tessellator) {
        this(tessellator, GL11.GL_QUADS);
    }

    /**
     * @param tessellator 要写入的 1.7.10 Tessellator
     * @param drawMode    GL 绘制模式（{@code GL11.GL_QUADS / GL_TRIANGLES / GL_TRIANGLE_FAN / GL_LINES / GL_LINE_STRIP}）
     */
    public TessellatorVertexConsumer(Tessellator tessellator, int drawMode) {
        this.tessellator = tessellator;
        this.drawMode = drawMode;
    }

    /**
     * 对应 1.20 里「翻缓冲」这一步：调用 {@link Tessellator#draw()}。
     * 重复调用是安全的（第二次起直接返回）。
     */
    public void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (started) {
            tessellator.draw();
            started = false;
        }
    }

    /** 是否已经调用过 {@link #finish()}。 */
    public boolean isFinished() {
        return finished;
    }

    /** 暴露底层 Tessellator，供需要 {@code setTranslation} 等高级操作的调用方使用。 */
    public Tessellator getTessellator() {
        return tessellator;
    }

    /** 当前 GL 绘制模式。 */
    public int getDrawMode() {
        return drawMode;
    }

    private void ensureStarted() {
        if (!started) {
            tessellator.startDrawing(drawMode);
            started = true;
        }
    }

    // ---------------------------------------------------------------- VertexConsumer

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        ensureStarted();
        // 1.20 的 vertex(...) 只写位置，颜色/uv/法线/light 在之后、endVertex() 之前补；
        // 因此这里不能立刻 addVertex（否则属性会错位到下一个顶点）。
        this.vertexX = x;
        this.vertexY = y;
        this.vertexZ = z;
        this.hasVertex = true;
        return this;
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        return color((int) (red * 255.0F), (int) (green * 255.0F), (int) (blue * 255.0F), (int) (alpha * 255.0F));
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        this.red = clampByte(red);
        this.green = clampByte(green);
        this.blue = clampByte(blue);
        this.alpha = clampByte(alpha);
        this.hasColor = true;
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        this.u = u;
        this.v = v;
        this.hasUv = true;
        return this;
    }

    /**
     * <b>降级点</b>：1.7.10 的 {@link Tessellator} 没有 overlay 顶点属性，此调用被丢弃。
     */
    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        // TODO(port): 1.20 VertexConsumer#overlayCoords —— 1.7.10 Tessellator 无 overlay 通道，丢弃
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        this.packedLight = (u & 0xFFFF) | ((v & 0xFFFF) << 16);
        this.hasLight = true;
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        this.normalX = x;
        this.normalY = y;
        this.normalZ = z;
        this.hasNormal = true;
        return this;
    }

    @Override
    public void endVertex() {
        if (!hasVertex) {
            return;
        }
        hasVertex = false;
        if (!attributeMaskLocked) {
            attributeMaskLocked = true;
            maskUv = hasUv;
            maskLight = hasLight;
            maskNormal = hasNormal || hasDefaultNormal;
        }
        if (maskUv) {
            tessellator.setTextureUV(hasUv ? u : 0.0D, hasUv ? v : 0.0D);
        }
        if (hasColor) {
            tessellator.setColorRGBA(red, green, blue, alpha);
        } else if (hasDefaultColor) {
            tessellator.setColorRGBA(defaultRed, defaultGreen, defaultBlue, defaultAlpha);
        } else {
            tessellator.setColorRGBA(255, 255, 255, 255);
        }
        if (maskLight) {
            tessellator.setBrightness(hasLight ? packedLight : LightTexture.FULL_BRIGHT);
        }
        if (maskNormal) {
            if (hasNormal) {
                tessellator.setNormal(normalX, normalY, normalZ);
            } else if (hasDefaultNormal) {
                tessellator.setNormal(defaultNormalX, defaultNormalY, defaultNormalZ);
            }
        }
        tessellator.addVertex(vertexX, vertexY, vertexZ);
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        this.defaultRed = clampByte(red);
        this.defaultGreen = clampByte(green);
        this.defaultBlue = clampByte(blue);
        this.defaultAlpha = clampByte(alpha);
        this.hasDefaultColor = true;
    }

    @Override
    public void defaultNormal(float x, float y, float z) {
        this.defaultNormalX = x;
        this.defaultNormalY = y;
        this.defaultNormalZ = z;
        this.hasDefaultNormal = true;
    }

    @Override
    public void unsetDefaultColor() {
        this.hasDefaultColor = false;
    }

    private static int clampByte(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 255);
    }
}
