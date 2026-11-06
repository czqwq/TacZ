// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/event/MolangExpressionEvent.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;

/**
 * Molang 表达式事件节点。触发时执行 Molang 表达式。
 *
 * @param expression         原始表达式字符串（调试用）
 * @param compiledExpression 预编译的表达式
 */
public record MolangExpressionEvent(String expression, MolangExpression compiledExpression) implements IEventNode {

    public static MolangExpressionEvent of(String expression, ParticleMolangEnvironment molang) {
        if (expression == null || expression.isEmpty()) {
            return new MolangExpressionEvent("", MolangExpression.constant(0));
        }
        return new MolangExpressionEvent(expression, molang.compile(expression));
    }
}
