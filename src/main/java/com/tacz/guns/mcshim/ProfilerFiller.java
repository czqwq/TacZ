package com.tacz.guns.mcshim;

import java.util.function.Supplier;

import net.minecraft.profiler.Profiler;

/**
 * 对应 1.20.1 的 {@code net.minecraft.util.profiling.ProfilerFiller}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 有 {@link Profiler}（{@code Minecraft#mcProfiler} / {@code Profiler} 实例），
 * 方法名是 {@code startSection/endSection/endStartSection}。1.20 的 {@code ProfilerFiller} 方法名是
 * {@code push/pop/popPush/inlined}。本类是一个薄封装，把两套名字都提供。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>{@link #inlined(Runnable)}：1.20 用它做「不进入 profiler 的轻量区段」，1.7.10 直接执行。</li>
 * <li>{@code getZone()} / {@code getEntry(String)} 等 1.20 的诊断 API 未移植。</li>
 * <li>{@code Profiler} 的 section 名长度上限是 30 字符（1.7.10 会截断），1.20 无此限制。</li>
 * </ul>
 */
public class ProfilerFiller {

    private final Profiler profiler;

    public ProfilerFiller(Profiler profiler) {
        this.profiler = profiler;
    }

    /** 包一个 1.7.10 {@link Profiler}。 */
    public static ProfilerFiller wrap(Profiler profiler) {
        return new ProfilerFiller(profiler);
    }

    /** 底层 {@link Profiler}（可能为 {@code null}）。 */
    public Profiler unwrap() {
        return profiler;
    }

    /** 对应 1.20 {@code ProfilerFiller#push(String)} → {@code Profiler#startSection}。 */
    public void push(String name) {
        if (profiler != null) {
            profiler.startSection(name);
        }
    }

    /** 对应 1.20 的 {@code Supplier<String>} 重载（1.7.10 直接求值）。 */
    public void push(Supplier<String> nameSupplier) {
        push(nameSupplier.get());
    }

    /** 对应 1.20 {@code ProfilerFiller#pop()} → {@code Profiler#endSection}。 */
    public void pop() {
        if (profiler != null) {
            profiler.endSection();
        }
    }

    /** 对应 1.20 {@code ProfilerFiller#popPush(String)} → {@code Profiler#endStartSection}。 */
    public void popPush(String name) {
        if (profiler != null) {
            profiler.endStartSection(name);
        }
    }

    /** 对应 1.20 {@code ProfilerFiller#popPush(Supplier)}。 */
    public void popPush(Supplier<String> nameSupplier) {
        popPush(nameSupplier.get());
    }

    /** 对应 1.20 {@code ProfilerFiller#inlined(Runnable)}：1.7.10 直接执行，不进入 profiler。 */
    public void inlined(Runnable runnable) {
        runnable.run();
    }

    /** 1.7.10 原名：{@code startSection}。 */
    public void startSection(String name) {
        push(name);
    }

    /** 1.7.10 原名：{@code endSection}。 */
    public void endSection() {
        pop();
    }

    /** 1.7.10 原名：{@code endStartSection}。 */
    public void endStartSection(String name) {
        popPush(name);
    }
}
