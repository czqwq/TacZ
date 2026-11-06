package com.tacz.guns.api.modifier;

import java.util.List;

import com.tacz.guns.resource.pojo.data.attachment.Modifier;

/**
 * 工具类
 * <p>
 * 1.7.10 移植说明：上游实现 {@code it.unimi.dsi.fastutil.Pair}；该类型只经 GTNHLib 传递依赖出现在编译类路径，
 * 本工程未显式声明也未被 shade 进 jar，为避免把打包范围外的第三方类型暴露到公开 API，
 * 现按 PORT_PLAN §3.3 改为自持两个 {@link ParameterizedCache} 字段，
 * 对外保留上游的 {@link #left()} / {@link #right()} 方法名与静态工厂。
 */
public class ParameterizedCachePair<L, R> {

    private final ParameterizedCache<L> left;
    private final ParameterizedCache<R> right;

    private ParameterizedCachePair(ParameterizedCache<L> left, ParameterizedCache<R> right) {
        this.left = left;
        this.right = right;
    }

    public ParameterizedCache<L> left() {
        return left;
    }

    public ParameterizedCache<R> right() {
        return right;
    }

    public static <L, R> ParameterizedCachePair<L, R> of(L defaultLeft, R defaultRight) {
        return new ParameterizedCachePair<>(ParameterizedCache.of(defaultLeft), ParameterizedCache.of(defaultRight));
    }

    public static <L, R> ParameterizedCachePair<L, R> of(List<Modifier> left, List<Modifier> right, L defaultLeft,
        R defaultRight) {
        return new ParameterizedCachePair<>(
            new ParameterizedCache<>(left, defaultLeft),
            new ParameterizedCache<>(right, defaultRight));
    }
}
