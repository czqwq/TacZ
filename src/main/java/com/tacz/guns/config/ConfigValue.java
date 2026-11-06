package com.tacz.guns.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 1.7.10 版 {@code net.minecraftforge.common.ForgeConfigSpec.ConfigValue} 的等价物。
 *
 * <p>
 * 上游（1.20.1）的配置项是 {@code ForgeConfigSpec} 定义的 {@code ConfigValue} 对象，调用方统一用
 * {@code XxxConfig.FOO.get()} 读值、{@code .set(v)} 写值，配置系统自己管理文件加载/保存。
 * 1.7.10 只有 {@link Configuration}（{@code ForgeConfigSpec} 之前的旧 API），它没有「配置项对象」这一层，
 * 只有 {@code getInt/getFloat/getBoolean/get(key,...)} 这类一次性读取 + {@link Property}。
 *
 * <p>
 * 为了让上游调用点（item/entity/client 等包里的 {@code GunConfig.XXX.get()}）<b>一行都不用改</b>，
 * 这里用 {@code Configuration} + {@link Property} 重新造了「配置项对象」：
 * <ul>
 * <li>{@link #get()}：返回缓存值，与上游语义一致（不每次读文件）</li>
 * <li>{@link #set(Object)}：写回内存 + 写回 {@link Property}，由 {@link TaczConfigSpec#save()} 落盘</li>
 * <li>{@link #reload()}：重新向 {@link Configuration} 登记（自动带注释/范围）并刷新缓存值，
 * {@link TaczConfigSpec#load()} 会遍历调用</li>
 * </ul>
 *
 * <p>
 * 与上游的差异：
 * <ul>
 * <li>上游有 {@code ModConfig.Type.SERVER} 的自动客户端同步；1.7.10 没有。本工程 {@code config/sync/SyncConfig}
 * 的同步改由 {@code com.tacz.guns.network.message.ServerMessageSyncConfig} 手动下发，见该类注释。</li>
 * <li>上游支持 {@code spec.isLoaded()} 判断；这里用 {@link TaczConfigSpec#isLoaded()}</li>
 * </ul>
 *
 * @param <T> 值的 Java 类型（Integer / Boolean / Double / String / Enum / List&lt;String&gt;）
 */
public abstract class ConfigValue<T> {

    protected final TaczConfigSpec spec;
    /**
     * 1.7.10 {@link Configuration} 的 category（上游 push() 出来的路径用 '.' 连接）
     */
    protected final String category;
    protected final String key;

    private T value;
    private Property property;

    protected ConfigValue(TaczConfigSpec spec, String category, String key) {
        this.spec = spec;
        this.category = category;
        this.key = key;
    }

    public String getCategory() {
        return category;
    }

    public String getKey() {
        return key;
    }

    /**
     * 形如 {@code gun.DefaultGunFireSoundDistance} 的完整路径，用于日志与调试命令。
     */
    public String getPath() {
        return category.isEmpty() ? key : category + "." + key;
    }

    /**
     * 读取当前值。语义与上游 {@code ForgeConfigSpec.ConfigValue#get()} 一致：返回缓存值。
     */
    public final T get() {
        return value;
    }

    /**
     * 修改当前值。语义与上游一致：只改内存 + 标记配置文件已变更，落盘由
     * {@link TaczConfigSpec#save()} 负责。
     */
    public void set(T newValue) {
        this.value = newValue;
        if (this.property != null) {
            write(this.property, newValue);
        }
    }

    /**
     * 向 {@link Configuration} 重新登记本项并刷新缓存值。
     * <p>
     * 1.7.10 的 {@link Configuration#load()} 会重建内部的 category/property 映射，之前的 {@link Property}
     * 引用会失效，因此每次 load 之后必须调用本方法（{@link TaczConfigSpec#load()} 已负责遍历）。
     */
    public final void reload() {
        this.property = define(spec.getConfiguration());
        this.value = this.property == null ? null : read(this.property);
    }

    @Override
    public String toString() {
        return getPath() + "=" + value;
    }

    /**
     * 把值写入 {@link Property}（{@link #set(Object)} 调用）。落盘由 {@link Configuration#save()} 完成。
     */
    protected abstract void write(Property property, T value);

    /**
     * 从 {@link Property} 解析出值。解析失败应返回默认值而不是抛异常（与 {@code ForgeConfigSpec} 的宽松语义一致）。
     */
    protected abstract T read(Property property);

    /**
     * 向 {@link Configuration} 登记本项（带注释、取值范围、默认值），返回对应的 {@link Property}。
     */
    protected abstract Property define(Configuration config);
}
