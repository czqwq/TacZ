package com.tacz.guns.mcshim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对应 1.20.1 的 {@code net.minecraft.client.renderer.MultiBufferSource}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.20 的 {@code MultiBufferSource} 是「按 {@code RenderType} 把顶点分流到不同
 * {@code BufferBuilder}」的门面，最后由 {@code BufferSource#endBatch()} 统一
 * 「设 GL 状态 → 上传 VBO → 绘制」。1.7.10 只能逐批次 {@code Tessellator} 绘制，
 * 本 shim 的 {@link BufferSource} 照样按 {@code RenderType} 攒顶点（用
 * {@link BufferBuilder} 的 Java 数组），在 {@code endBatch()} 时对每个
 * {@code RenderType} 依次 {@link RenderType#setupRenderState()} →
 * {@link BufferUploader#draw(BufferBuilder.RenderedBuffer)} →
 * {@link RenderType#clearRenderState()}。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li><b>顺序</b>：本类按 {@link RenderType} 实例（身份）各攒一个 builder，{@code endBatch()} 时按
 * 「该类型第一次被 {@code getBuffer} 的顺序」依次绘制。1.20 的 {@code BufferSource} 只有一个共享
 * builder，一旦换成别的 {@code RenderType} 就先把上一个批次 flush 掉，因此 1.20 的绘制顺序是
 * 「类型切换顺序」、本类是「类型首次出现顺序」。对「按类型整段绘制」的调用点两者一致；
 * 唯一可见差异是 A→B→A 这种交错调用（透明面排序可能与上游不同）。</li>
 * <li><b>不排序</b>：1.20 对 {@code sortOnUpload} 的类型在 endBatch 时做面排序，1.7.10 未实现
 * （{@code Tessellator#getVertexState} 能做，但需要调用方显式触发）。</li>
 * <li><b>immediate() 返回同一个实例</b>：1.20 的 {@code immediate()} 返回的是一个「只缓冲一类」的
 * 特殊 source；本类返回 {@code this}（因为 {@link RenderType} 已经决定了 GL 状态）。</li>
 * <li><b>endLastBatch()</b>：1.20 语义是「只 flush 最后一个 RenderType」；本类按
 * {@code LinkedHashMap} 的最后一个 key 实现，等价。</li>
 * <li>{@link #getBuffer(RenderType)} 返回的 {@link VertexConsumer} 在 {@code endBatch()} 之后<b>不可</b>继续使用。</li>
 * </ul>
 *
 * <p>
 * 典型用法与 1.20 完全一致：
 * 
 * <pre>
 * {@code
 * MultiBufferSource.BufferSource source = MultiBufferSource.immediate();
 * VertexConsumer consumer = source.getBuffer(RenderType.entityCutoutNoCull(texture));
 * ... consumer.vertex(...).color(...).uv(...).endVertex(); ...
 * source.endBatch();
 * }
 * </pre>
 */
public interface MultiBufferSource {

    /** 对应 1.20 {@code MultiBufferSource#getBuffer(RenderType)}。 */
    VertexConsumer getBuffer(RenderType renderType);

    /** 对应 1.20 {@code MultiBufferSource#immediate()}（返回 BufferSource）。 */
    static BufferSource immediate() {
        return new BufferSource();
    }

    /** 对应 1.20 {@code MultiBufferSource#immediateWithBuffers(Map, boolean)}：忽略预置 buffer，返回空 source。 */
    static BufferSource immediateWithBuffers(Map<RenderType, BufferBuilder> buffers, boolean sortOnUpload) {
        return new BufferSource();
    }

    /**
     * 对应 1.20.1 {@code MultiBufferSource.BufferSource}：按 {@link RenderType} 分流并批量提交。
     */
    class BufferSource implements MultiBufferSource {

        private final Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        private final List<RenderType> drawOrder = new ArrayList<>();

        public BufferSource() {}

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            BufferBuilder builder = builders.get(renderType);
            if (builder == null) {
                builder = new BufferBuilder(4096);
                builder.begin(renderType.mode(), renderType.format());
                builders.put(renderType, builder);
                drawOrder.add(renderType);
            }
            return builder;
        }

        /** 对应 1.20 {@code BufferSource#immediate()}：返回自身。 */
        public BufferSource immediate() {
            return this;
        }

        /**
         * 对应 1.20 {@code BufferSource#endBatch()}：把所有攒下的 {@link RenderType} 依次绘制并清空。
         */
        public void endBatch() {
            for (RenderType renderType : drawOrder) {
                drawBatch(renderType);
            }
            builders.clear();
            drawOrder.clear();
        }

        /** 对应 1.20 {@code BufferSource#endBatch(RenderType)}：只绘制指定类型。 */
        public void endBatch(RenderType renderType) {
            if (drawBatch(renderType)) {
                builders.remove(renderType);
                drawOrder.remove(renderType);
            }
        }

        /**
         * 对应 1.20 {@code BufferSource#endLastBatch()}：只绘制最后一次 {@code getBuffer} 的类型。
         */
        public void endLastBatch() {
            if (drawOrder.isEmpty()) {
                return;
            }
            RenderType renderType = drawOrder.get(drawOrder.size() - 1);
            endBatch(renderType);
        }

        private boolean drawBatch(RenderType renderType) {
            BufferBuilder builder = builders.get(renderType);
            if (builder == null) {
                return false;
            }
            BufferBuilder.RenderedBuffer buffer = builder.end();
            if (buffer == null || buffer.isEmpty()) {
                return true;
            }
            renderType.setupRenderState();
            try {
                BufferUploader.draw(buffer);
            } finally {
                renderType.clearRenderState();
                buffer.release();
            }
            return true;
        }

        /** 是否有待绘制的批次。 */
        public boolean hasPendingBatches() {
            return !drawOrder.isEmpty();
        }
    }
}
