package com.tacz.guns.compat.oculus;

import com.tacz.guns.mcshim.MultiBufferSource;

/**
 * 上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/oculus/IUnflushableWrapper.java}
 * 的同名接口，由 {@code mixin/client/oculus/UnflushableWrapperAccessor} 注入到 Iris 的
 * {@code FullyBufferedMultiBufferSource$UnflushableWrapper} 上，用于取出其内部真正的 buffer source
 * 以执行有效的 flush。
 *
 * <p>
 * <b>1.7.10 形态</b>：Iris/Oculus 不支持 1.7.10，因此注入目标类不存在，那个 mixin 也不会被加载；
 * 本接口保留是为了让 {@code OculusCompat#endBatch} 的「先解包再 flush」流程与上游一致，
 * 且类名 / 方法名（{@code tacz$getWrapped}）不漂移。
 *
 * <p>
 * 返回值类型由 1.20 的 {@code net.minecraft.client.renderer.MultiBufferSource} 换成 1.7.10 的
 * {@link MultiBufferSource}（本工程 {@code mcshim} 的等价物）。
 */
public interface IUnflushableWrapper {

    MultiBufferSource tacz$getWrapped();
}
