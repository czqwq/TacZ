package com.tacz.guns.compat.ar;

/**
 * 上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/ar/BedrockPartLightingMode.java} 原样保留。
 *
 * <p>
 * 它本身**不依赖** Accelerated Rendering 的任何类型（只是两个光照模式的枚举），因此 1.7.10 可以直接沿用；
 * 上游由 {@code mixin/client/ar/BedrockPartMixin}（{@code IAcceleratedRenderer<BedrockPartLightingMode>}）
 * 用它来选择「着色 / 无着色」两套网格缓存。
 *
 * <p>
 * 1.7.10 没有 AR，也没有那套网格缓存，因此该枚举当前没有使用方；保留是为了让
 * {@code BedrockPartLightingMode} 这个名字不漂移，日后需要区分着色/无着色顶点时可以直接用。
 */
public enum BedrockPartLightingMode {
    SHADED,
    UNSHADED
}
