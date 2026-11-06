// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/molang/runtime/binding/QueryBinding.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.binding;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将 MolangContext 子类上的无参方法映射为 Molang 命名空间下的属性。
 * <p>
 * 用法：在 MolangContext 的子类中，对返回 {@code double} 的无参方法标注此注解。
 * 
 * <pre>
 * public class MyContext extends MolangContext&lt;MyEntity&gt; {
 *     &#64;QueryBinding("is_on_ground")
 *     public double isOnGround() { return getEntity().isOnGround() ? 1.0 : 0.0; }
 *
 *     // 映射到自定义命名空间
 *     &#64;QueryBinding(value = "is_jumping", namespace = "input")
 *     public double isJumping() { return ...; }
 * }
 * </pre>
 * 
 * 编译后 {@code query.is_on_ground} 和 {@code input.is_jumping}
 * 分别生成直接 INVOKEVIRTUAL 调用。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryBinding {

    /** 属性名 */
    String value();

    /** 命名空间，默认 {@code "query"} */
    String namespace() default "query";
}
