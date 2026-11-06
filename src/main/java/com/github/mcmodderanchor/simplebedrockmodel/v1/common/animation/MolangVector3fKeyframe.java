// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/animation/MolangVector3fKeyframe.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.maydaymemory.mae.basic.BaseKeyframe;
import com.maydaymemory.mae.basic.IEvaluationContext;
import com.maydaymemory.mae.basic.InterpolatableKeyframe;
import com.maydaymemory.mae.basic.Interpolator;

/**
 * 支持 Molang 表达式的 Vector3f 关键帧。
 * 每次获取 pre/post 值时，会实时求值 Molang 表达式。
 * <p>
 * 必须通过 {@link IEvaluationContext} 参数传入上下文（{@link #getPre(IEvaluationContext)} /
 * {@link #getPost(IEvaluationContext)}）。无参版本返回零向量。
 */
public class MolangVector3fKeyframe extends BaseKeyframe<Vector3fc> implements InterpolatableKeyframe<Vector3fc> {

    private static final Vector3fc ZERO = new Vector3f();

    private final MolangExpression[] preFunctions;
    private final MolangExpression[] postFunctions;
    private final Interpolator<Vector3fc> interpolator;

    /**
     * @param timeS         关键帧时间（秒）
     * @param preFunctions  pre 值的 3 个 Molang 表达式 (x, y, z)
     * @param postFunctions post 值的 3 个 Molang 表达式 (x, y, z)
     * @param interpolator  插值器
     */
    public MolangVector3fKeyframe(float timeS, MolangExpression[] preFunctions, MolangExpression[] postFunctions,
        Interpolator<Vector3fc> interpolator) {
        super(timeS);
        this.preFunctions = preFunctions;
        this.postFunctions = postFunctions;
        this.interpolator = interpolator;
    }

    /**
     * @deprecated 无 Molang 上下文时无法求值，返回零向量。请使用 {@link #getPre(IEvaluationContext)}。
     */
    @Override
    @Deprecated
    public Vector3fc getPre() {
        return ZERO;
    }

    /**
     * @deprecated 无 Molang 上下文时无法求值，返回零向量。请使用 {@link #getPost(IEvaluationContext)}。
     */
    @Override
    @Deprecated
    public Vector3fc getPost() {
        return ZERO;
    }

    @Override
    public Vector3fc getPre(IEvaluationContext ctx) {
        if (ctx instanceof MolangContext<?>mc) {
            return evaluate(preFunctions, mc);
        }
        return ZERO;
    }

    @Override
    public Vector3fc getPost(IEvaluationContext ctx) {
        if (ctx instanceof MolangContext<?>mc) {
            return evaluate(postFunctions, mc);
        }
        return ZERO;
    }

    @Override
    public Interpolator<Vector3fc> getInterpolator() {
        return interpolator;
    }

    @Override
    public Vector3fc getValue() {
        return getPre();
    }

    private static Vector3fc evaluate(MolangExpression[] functions, MolangContext<?> ctx) {
        return new Vector3f(
            (float) functions[0].evaluate(ctx),
            (float) functions[1].evaluate(ctx),
            (float) functions[2].evaluate(ctx));
    }
}
