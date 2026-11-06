package com.tacz.guns.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.common.config.Configuration;

/**
 * 1.7.10 版 {@code net.minecraftforge.common.ForgeConfigSpec} 的等价物。
 *
 * <p>
 * 上游（1.20.1）用 {@code ForgeConfigSpec} 描述配置，由 Forge 的 {@code ModConfig} 负责加载/保存/同步；
 * 1.7.10 只有 {@link Configuration}（一个文件 = 一份配置，category 分组）。
 * 本类把两者粘在一起：
 * <ul>
 * <li>持有底层 {@link Configuration}</li>
 * <li>持有本 spec 登记的全部 {@link ConfigValue}，{@link #load()} 时逐个 {@link ConfigValue#reload()}</li>
 * <li>{@link #save()} = {@link Configuration#save()}，供 {@code CommonConfig.COMMON_CONFIG_SPEC.save()} 这类
 * 上游调用点使用</li>
 * </ul>
 *
 * <p>
 * 与上游的差异（记录而非隐藏）：
 * <ul>
 * <li>上游 {@code ModConfig.Type.COMMON / SERVER / CLIENT} 的文件名与目录由 Forge 决定，SERVER 配置会首先生成在
 * {@code serverconfig/} 并在客户端登录时自动同步；1.7.10 全部落在 {@code config/} 下，SERVER 配置的客户端同步
 * 由 {@code network/message/ServerMessageSyncConfig} 手动完成。</li>
 * <li>上游配置热重载（{@code ModConfigEvent.Reloading}）由 Forge 触发；1.7.10 需手动
 * {@code load()}（例如 reload 命令），见 {@link ConfigManager#reloadAll()}。</li>
 * </ul>
 */
public class TaczConfigSpec {

    private final Configuration configuration;
    private final List<ConfigValue<?>> values = new ArrayList<>();
    private boolean loaded = false;

    public TaczConfigSpec(File file) {
        this.configuration = new Configuration(file);
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public File getFile() {
        return configuration.getConfigFile();
    }

    /**
     * 由 {@link ConfigBuilder} 登记配置项时调用。
     */
    void addValue(ConfigValue<?> value) {
        values.add(value);
    }

    public List<ConfigValue<?>> getValues() {
        return Collections.unmodifiableList(values);
    }

    /**
     * 读取配置文件并刷新全部配置项缓存值。重复调用即为「热重载」。
     */
    public TaczConfigSpec load() {
        configuration.load();
        for (ConfigValue<?> value : values) {
            value.reload();
        }
        loaded = true;
        return this;
    }

    /**
     * 落盘。对应上游的 {@code ForgeConfigSpec#save()} 调用点。
     */
    public void save() {
        configuration.save();
    }

    public boolean isLoaded() {
        return loaded;
    }
}
