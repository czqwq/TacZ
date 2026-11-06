// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/config/WorldMeshConfig.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.config;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.StrategyOverride;

/**
 * 世界网格渲染策略的客户端配置。
 *
 * <h2>1.7.10 移植说明</h2>
 * <p>
 * 上游是一个 {@code ForgeConfigSpec}（{@code builder.push(...).comment(...).defineEnum(...)}），
 * 由 {@code ModLoadingContext.registerConfig(ModConfig.Type.CLIENT, SPEC)} 在模组构造期注册，
 * 配置值通过 {@code SPEC.isLoaded()} / {@code STRATEGY.get()} / {@code STRATEGY.set(...)} /
 * {@code STRATEGY.save()} 访问。
 *
 * <p>
 * <b>1.7.10 没有 {@code ForgeConfigSpec} / {@code ModConfig}</b>（那是 1.13+ 的配置系统）。
 * 本类改为「纯内存静态字段 + 两个极小的持有者」，仍然提供调用点用到的<b>同样方法形状</b>
 * （{@code SPEC.isLoaded()}、{@code STRATEGY.get()/set()/save()}），因此
 * {@code WorldMeshRenderer} / {@code WorldMeshGroup} 的调用点无需改动。
 *
 * <h2>降级点</h2>
 * <ul>
 * <li>配置<b>不落盘</b>：{@code save()} 是 no-op，重启游戏后回到默认 {@link StrategyOverride#AUTO}。
 * 上游会把值写进 {@code config/simplebedrockmodel-client.toml}。若将来需要持久化，
 * 应接入 TACZ 自身的 {@code com.tacz.guns.config}。</li>
 * <li>{@link #SPEC} 的静态类型从 {@code ForgeConfigSpec} 变成 {@link Spec}，
 * {@link #STRATEGY} 从 {@code ForgeConfigSpec.EnumValue<StrategyOverride>} 变成
 * {@link EnumValue}。<b>这是本移植唯一改动公开签名的地方</b>（类型缺失所致，1.7.10 无法提供原类型）。</li>
 * </ul>
 */
public final class WorldMeshConfig {

    /**
     * {@code ForgeConfigSpec} 的 1.7.10 替代物。
     *
     * <p>
     * 只承担调用点用到的 {@link #isLoaded()}（上游用它判断「配置是否已经加载完成」）。
     * 1.7.10 的配置是静态字段，永远可用，故恒返回 {@code true}。
     */
    public static final class Spec {

        private Spec() {}

        /** 对应 1.20 {@code ForgeConfigSpec#isLoaded()}：静态字段恒可用。 */
        public boolean isLoaded() {
            return true;
        }
    }

    /** {@code ForgeConfigSpec.EnumValue<T>} 的 1.7.10 替代物：一个带 {@code save()} 的枚举字段。 */
    public static final class EnumValue<T extends Enum<T>> {

        private final String comment;
        private T value;

        EnumValue(String comment, T defaultValue) {
            this.comment = comment;
            this.value = defaultValue;
        }

        /** 对应 1.20 {@code ConfigValue#get()}。 */
        public T get() {
            return value;
        }

        /** 对应 1.20 {@code ConfigValue#set(Object)}。 */
        public void set(T value) {
            this.value = value;
        }

        /** 对应 1.20 {@code ConfigValue#save()}：1.7.10 无配置文件，降级为 no-op。 */
        public void save() {
            // TODO(L2): 上游 v2/client/config/WorldMeshConfig.java:24 的 ForgeConfigSpec 会把值写进
            // config/simplebedrockmodel-client.toml；1.7.10 无 ForgeConfigSpec/ModConfig，
            // 此处降级为纯内存字段，save() 不落盘。
        }

        /** 配置项注释（1.20 里由 {@code builder.comment(...)} 保存，这里只留着备查）。 */
        public String comment() {
            return comment;
        }
    }

    /** 配置 spec 句柄（上游类型 {@code ForgeConfigSpec}）。 */
    public static final Spec SPEC = new Spec();

    /** 世界网格渲染策略（上游类型 {@code ForgeConfigSpec.EnumValue<StrategyOverride>}）。 */
    public static final EnumValue<StrategyOverride> STRATEGY = new EnumValue<>(
        "Strategy for supported world mesh groups: AUTO, INSTANCE, SECTION. Commands update this setting.",
        StrategyOverride.AUTO);

    private WorldMeshConfig() {}
}
