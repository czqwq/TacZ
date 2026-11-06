package com.tacz.guns.mcshim;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 对应 1.20.1 的 {@code net.minecraft.server.packs.resources.PreparableReloadListener}。
 *
 * <h2>1.20 的形态</h2>
 * 
 * <pre>
 * 
 * CompletableFuture&lt;Void&gt; reload(PreparableReloadListener.PreparationBarrier barrier, ResourceManager resourceManager,
 *     ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler, Executor backgroundExecutor,
 *     Executor gameExecutor);
 * </pre>
 * 
 * 由 {@code SimpleReloadInstance} 统一调度：prepare 阶段在后台线程池跑、apply 阶段回主线程。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 有 {@code net.minecraft.client.resources.IResourceManagerReloadListener}
 * （{@code onResourceManagerReload(IResourceManager)}），但它
 * <b>①</b> 只能看到 {@code assets/**}、<b>②</b> 是客户端专用、<b>③</b> 没有 prepare/apply 两阶段。
 * 因此本类是独立接口，由本工程的「重载调度器」（{@code resource/**} 里的
 * {@code CommonDataManager} / {@code ClientDataManager} 那层）<b>直接驱动</b>：
 * 
 * <pre>
 * for (PreparableReloadListener l : listeners) {
 *     l.reload(barrier, resourceManager, profiler, profiler, executor, executor);
 * }
 * </pre>
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.7.10 侧<b>不保证</b>后台线程执行 prepare；调度器可以选择在调用线程上同步执行
 * （默认实现见 {@link SimplePreparableReloadListener#reload}）。</li>
 * <li>{@link PreparationBarrier} 的两个泛型阶段可以退化成 {@code Void}（1.7.10 侧没有
 * {@code Stage} 枚举）。</li>
 * <li>没有 {@code getDependencies()}（1.20 的 listener 依赖排序未移植）。</li>
 * </ul>
 */
public interface PreparableReloadListener {

    /**
     * 对应 1.20 {@code PreparableReloadListener#reload(...)}。
     *
     * @param barrier            阶段屏障（1.7.10 侧同步实现里直接 {@code wait(Unit.INSTANCE)}）
     * @param resourceManager    本工程的资源管理器（替代 1.20 的 {@code ResourceManager}）
     * @param prepareProfiler    prepare 阶段 profiler
     * @param applyProfiler      apply 阶段 profiler
     * @param backgroundExecutor 后台线程池（1.7.10 侧可能是 {@code Runnable::run}）
     * @param gameExecutor       主线程池（同上）
     */
    CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
        ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler, Executor backgroundExecutor,
        Executor gameExecutor);

    /**
     * 对应 1.20 {@code PreparableReloadListener.PreparationBarrier}：把「prepare 结果」交给 apply 阶段。
     *
     * <p>
     * 1.7.10 的同步调度里 prepare 与 apply 在同一个调用栈上，因此本接口的
     * {@link #wait(Object)} 直接返回同一个对象。
     */
    interface PreparationBarrier {

        /** 对应 1.20 {@code PreparationBarrier#wait(T preparedValue)}。 */
        <T> CompletableFuture<T> wait(T preparedValue);

        /** 1.7.10 侧「什么都不做」的屏障：直接返回入参。 */
        PreparationBarrier DIRECT = new PreparationBarrier() {

            @Override
            public <T> CompletableFuture<T> wait(T preparedValue) {
                return CompletableFuture.completedFuture(preparedValue);
            }
        };
    }
}
