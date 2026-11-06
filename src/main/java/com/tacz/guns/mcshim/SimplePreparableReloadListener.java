package com.tacz.guns.mcshim;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * 对应 1.20.1 的 {@code net.minecraft.server.packs.resources.SimplePreparableReloadListener}。
 *
 * <h2>1.20 的形态</h2>
 * 抽象类，固定两个钩子：
 * 
 * <pre>
 * 
 * protected abstract void prepare(ResourceManager resourceManager, ProfilerFiller profiler);
 * 
 * protected abstract void apply(T object, ResourceManager resourceManager, ProfilerFiller profiler);
 * 
 * protected abstract T prepare(ResourceManager resourceManager, ProfilerFiller profiler); // 返回 prepare 结果
 * </pre>
 * 
 * {@code reload} 模板方法负责：后台跑 prepare → 过 barrier → 主线程跑 apply。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 保持同样的模板方法形态，但<b>默认同步执行</b>（1.7.10 的资源加载本来就发生在
 * 客户端/服务端线程上，且 {@code ResourceManager} 是本地文件 IO，异步收益很小）。
 * 需要异步的实现可以 override {@link #reload} 或给调度器传真实线程池。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>prepare / apply <b>不保证</b>在不同线程上（见上）。</li>
 * <li>不处理 {@code Stage} / {@code ReloadableResourceManager} 的生命周期与异常聚合，
 * 异常直接以 {@link CompletionException} 抛出。</li>
 * </ul>
 */
public abstract class SimplePreparableReloadListener<T> implements PreparableReloadListener {

    /**
     * prepare 阶段：从 {@code resourceManager} 读出数据，返回值交给 {@link #apply}。
     */
    protected abstract T prepare(ResourceManager resourceManager, ProfilerFiller profiler);

    /**
     * apply 阶段：把 prepare 的结果写进内存状态。
     */
    protected abstract void apply(T object, ResourceManager resourceManager, ProfilerFiller profiler);

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
        ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler, Executor backgroundExecutor,
        Executor gameExecutor) {
        T prepared;
        prepareProfiler.push("prepare");
        try {
            prepared = prepare(resourceManager, prepareProfiler);
        } finally {
            prepareProfiler.pop();
        }
        return barrier.wait(prepared)
            .thenAcceptAsync(value -> {
                applyProfiler.push("apply");
                try {
                    apply(value, resourceManager, applyProfiler);
                } finally {
                    applyProfiler.pop();
                }
            }, gameExecutor == null ? Runnable::run : gameExecutor)
            .exceptionally(throwable -> { throw new CompletionException(throwable); });
    }
}
