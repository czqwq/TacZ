package com.tacz.guns.mcshim;

/**
 * 对应 1.20.1 的 {@code net.minecraft.client.renderer.LightTexture}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 没有 {@code LightTexture} 类（光照贴图由 {@code TextureUtil}/{@code TextureManager} 内部的
 * {@code lightmap} 纹理承担），但「打包亮度值」这个概念是存在的：
 * <ul>
 * <li>{@code net.minecraft.world.World#getLightBrightnessForSkyBlocks(x, y, z, min)} 返回
 * {@code sky << 20 | block << 4}；</li>
 * <li>{@code Entity#getBrightnessForRender(float)} 返回同样布局；</li>
 * <li>{@code net.minecraft.client.renderer.Tessellator#setBrightness(int)} 直接把这个 int
 * 写进顶点缓冲的 lightmap 槽位（低 16 位 → U，高 16 位 → V）；</li>
 * <li>{@code OpenGlHelper.setLightmapTextureCoords(lightmapTexUnit, u, v)} 用
 * {@code u = packed & 0xFFFF}、{@code v = packed >> 16}。</li>
 * </ul>
 *
 * <h2>打包约定（<b>与 1.20 不同，务必注意</b>）</h2>
 * 
 * <pre>
 *   本 shim：pack(block, sky) = (sky &amp; 0xF) &lt;&lt; 20 | (block &amp; 0xF) &lt;&lt; 4
 *             block(packed)  = (packed &amp; 0xFFFF) &gt;&gt;&gt; 4         // 0..15
 *             sky(packed)    = (packed &gt;&gt;&gt; 16) &gt;&gt;&gt; 4           // 0..15
 *
 *   1.20  ：pack(block, sky) = (block &amp; 0xFFFF) | ((sky &amp; 0xFFFF) &lt;&lt; 16)
 *             block(packed)  = packed &amp; 0xFFFF                  // 0..15
 *             sky(packed)    = packed &gt;&gt; 16 &amp; 0xFFFF            // 0..15
 * </pre>
 * 
 * 之所以采用 1.7.10 布局，是因为这样 {@code pack(...)} 的返回值可以<b>直接</b>喂给
 * {@link net.minecraft.client.renderer.Tessellator#setBrightness(int)} 与
 * {@code OpenGlHelper.setLightmapTextureCoords}，不需要在渲染代码里做二次换算。
 * 从 1.20 搬过来的调用点只需把「构造亮度值」的地方换成本类的 {@link #pack(int, int)}，
 * 读回的地方换成 {@link #block(int)} / {@link #sky(int)}，公式本身不用改。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.20 的 {@code LightTexture} 是<b>实例</b>类（持有 lightmap 纹理、flicker 状态、gamma 表、
 * {@code updateLightTexture(float)}）。1.7.10 的 lightmap 由引擎自己更新，
 * 本类只有<b>静态</b>打包/拆包与常量，没有实例方法。</li>
 * <li>{@link #pack(int, int)} 会把入参 clamp 到 0..15（1.20 不 clamp，直接位运算）。
 * 上游若传入超过 15 的值（例如通过配置项提高亮度上限），会被截断到 15。</li>
 * </ul>
 */
public final class LightTexture {

    /** 方块光照最大值（1.7.10 与 1.20 都是 4 bit）。 */
    public static final int MAX_LIGHT_COORDS = 15;
    /** 天空光照最大值。 */
    public static final int MAX_SKY_LIGHT_COORDS = 15;
    /** 对应 1.7.10 惯用的「全亮」亮度值 {@code 0xF000F0}（block=15, sky=15）。 */
    public static final int FULL_BRIGHT = 0xF000F0;
    /** 对应 1.7.10 惯用的「全黑」亮度值（block=0, sky=0）。 */
    public static final int FULL_DARK = 0;

    private LightTexture() {}

    /**
     * 打包方块光与天空光，对应 1.20 {@code LightTexture#pack(int, int)}。
     *
     * <p>
     * 本 shim 的布局为 {@code sky << 20 | block << 4}（1.7.10 原生布局），
     * 可直接传给 {@code Tessellator#setBrightness(int)}。
     */
    public static int pack(int blockLight, int skyLight) {
        int block = clamp4(blockLight);
        int sky = clamp4(skyLight);
        return (sky << 20) | (block << 4);
    }

    /** 从打包值取出方块光照（0..15），对应 1.20 {@code LightTexture#block(int)}。 */
    public static int block(int packedLight) {
        return (packedLight & 0xFFFF) >>> 4;
    }

    /** 从打包值取出天空光照（0..15），对应 1.20 {@code LightTexture#sky(int)}。 */
    public static int sky(int packedLight) {
        return (packedLight >>> 16) >>> 4;
    }

    /** 把打包亮度换算成 lightmap 纹理的 U 坐标（0..240），等价 1.7.10 里的 {@code packed & 65535}。 */
    public static float u(int packedLight) {
        return (float) (packedLight & 0xFFFF);
    }

    /** 把打包亮度换算成 lightmap 纹理的 V 坐标（0..240），等价 1.7.10 里的 {@code packed >> 16}。 */
    public static float v(int packedLight) {
        return (float) (packedLight >> 16);
    }

    private static int clamp4(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 15);
    }
}
